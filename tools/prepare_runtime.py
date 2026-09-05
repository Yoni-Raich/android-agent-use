#!/usr/bin/env python3
"""Stage the official Codex app-server package for the Android APK.

Pipeline (stdlib only, reproducible):
  1. Verify SHA-256 of codex-package.tar.gz (official rust-v0.153.4 ARM64 musl).
  2. Safely extract it to .codex-work/runtime/package (no absolute paths,
     no "..", no symlinks/hardlinks, no devices).
  3. Copy native ELFs to app/build/generated/runtime/jniLibs/arm64-v8a/ as
     lib*.so (targetSdk 35 can only execute APK nativeLibraryDir files).
  4. Verify each staged .so is ELF64-LE AArch64 (e_machine == 183).
  5. Write app/build/generated/runtime/assets/runtime/ metadata
     (codex-package.json copy + runtime-manifest.json).

Root wires the Gradle side (jniLibs/assets srcDirs) and runs device tests.
This script never touches credentials and never claims device success.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import struct
import sys
import tarfile
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

EXPECTED_SHA256 = "5673c5a8935ff2f85ca67b489e560fdd5e08fb0f0e2f7426f048ec7449aa4fdc"
PACKAGE_VERSION = "0.153.4"
PACKAGE_TARGET = "aarch64-unknown-linux-musl"

DEFAULT_PACKAGE = os.path.join(".codex-work", "runtime", "codex-package.tar.gz")
DEFAULT_PACKAGE_DIR = os.path.join(".codex-work", "runtime", "package")
DEFAULT_JNILIBS = os.path.join(
    "app", "build", "generated", "runtime", "jniLibs", "arm64-v8a"
)
DEFAULT_ASSETS = os.path.join(
    "app", "build", "generated", "runtime", "assets", "runtime"
)

# Canonical package path -> staged lib name. Mirror in
# runtime/.../AndroidRuntimeHost.kt PACKAGE_LINKS; keep both in sync.
LIB_MAPPING = {
    "bin/codex-app-server": "libcodex_app_server.so",
    "bin/codex-code-mode-host": "libcodex_code_mode_host.so",
    "codex-path/rg": "libcodex_rg.so",
    "codex-resources/bwrap": "libcodex_bwrap.so",
    "codex-resources/zsh/bin/zsh": "libcodex_zsh.so",
}

EM_AARCH64 = 183
ELF_MAGIC = b"\x7fELF"


def fail(message: str) -> "NoReturn":  # type: ignore[name-defined]
    print("prepare_runtime: ERROR: " + message, file=sys.stderr)
    raise SystemExit(1)


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_package(url: str, dest: str) -> None:
    print("prepare_runtime: downloading " + url)
    os.makedirs(os.path.dirname(os.path.abspath(dest)), exist_ok=True)
    tmp = dest + ".part"
    try:
        with urllib.request.urlopen(url) as response, open(tmp, "wb") as handle:
            shutil.copyfileobj(response, handle, 1024 * 1024)
    except Exception as exc:
        if os.path.exists(tmp):
            os.remove(tmp)
        fail("download failed: %s" % exc)
    os.replace(tmp, dest)


def verify_hash(path: str, expected: str) -> None:
    actual = sha256_file(path)
    if actual.lower() != expected.lower():
        fail(
            "SHA-256 mismatch for %s\n  expected %s\n  actual   %s"
            % (path, expected, actual)
        )
    print("prepare_runtime: sha256 ok " + actual)


def reset_output(path: str) -> None:
    resolved = os.path.realpath(path)
    allowed = [os.path.realpath(os.path.join(REPO_ROOT, '.codex-work', 'runtime')),
               os.path.realpath(os.path.join(REPO_ROOT, 'app', 'build', 'generated', 'runtime'))]
    if not any(resolved != base and os.path.commonpath([resolved, base]) == base for base in allowed):
        fail('output must be a child of a dedicated runtime build directory: ' + resolved)
    if os.path.isdir(resolved):
        shutil.rmtree(resolved)
    os.makedirs(resolved, exist_ok=True)


def safe_extract(archive: str, dest: str) -> list[str]:
    """Extract with strict guards. Returns sorted member names."""
    reset_output(dest)
    dest_real = os.path.realpath(dest)
    names: list[str] = []
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            name = member.name
            if not name or name.startswith("/") or name.startswith("\\"):
                fail("refusing absolute member: %r" % name)
            parts = name.replace("\\", "/").split("/")
            if "" in parts or "." in parts or ".." in parts:
                fail("refusing unsafe member: %r" % name)
            if member.issym() or member.islnk():
                fail("refusing link member: %r" % name)
            if member.isdev():
                fail("refusing device member: %r" % name)
            target_real = os.path.realpath(os.path.join(dest, *parts))
            if target_real != dest_real and not target_real.startswith(
                dest_real + os.sep
            ):
                fail("refusing escaping member: %r" % name)
            names.append(name)
        # Python >=3.12 data filter as a second guard; strict checks above apply
        # on every interpreter.
        try:
            tar.extractall(dest, filter="data")  # type: ignore[call-arg]
        except TypeError:
            tar.extractall(dest)
    return sorted(names)


def check_required_layout(names: list[str]) -> None:
    present = set(names)
    missing = [p for p in list(LIB_MAPPING) + ["codex-package.json"] if p not in present]
    if missing:
        fail("package missing required entries: " + ", ".join(missing))


def check_elf_aarch64(path: str) -> int:
    with open(path, "rb") as handle:
        header = handle.read(64)
    if len(header) < 20 or header[0:4] != ELF_MAGIC:
        fail("not an ELF file: " + path)
    if header[4] != 2 or header[5] != 1:
        fail(
            "not ELF64 little-endian: %s (class=%d data=%d)"
            % (path, header[4], header[5])
        )
    _e_type, e_machine = struct.unpack_from("<HH", header, 16)
    if e_machine != EM_AARCH64:
        fail("not AArch64 (e_machine=%d): %s" % (e_machine, path))
    return e_machine


def stage_libraries(package_dir: str, jnilibs: str) -> list[dict]:
    reset_output(jnilibs)
    staged: list[dict] = []
    for package_path in sorted(LIB_MAPPING):
        lib_name = LIB_MAPPING[package_path]
        src = os.path.join(package_dir, *package_path.split("/"))
        if not os.path.isfile(src):
            fail("missing extracted file: " + src)
        if os.path.islink(src):
            fail("unexpected symlink in package: " + src)
        dest = os.path.join(jnilibs, lib_name)
        shutil.copyfile(src, dest)
        os.chmod(dest, 0o755)
        check_elf_aarch64(dest)
        staged.append(
            {
                "package_path": package_path,
                "lib_name": lib_name,
                "size": os.path.getsize(dest),
                "sha256": sha256_file(dest),
                "elf_machine": EM_AARCH64,
            }
        )
        print("prepare_runtime: staged %s -> %s" % (package_path, lib_name))
    return staged


def stage_assets(package_dir: str, assets: str, staged: list[dict]) -> None:
    reset_output(assets)
    manifest_src = os.path.join(package_dir, "codex-package.json")
    with open(manifest_src, "r", encoding="utf-8") as handle:
        package_manifest = json.load(handle)
    shutil.copyfile(manifest_src, os.path.join(assets, "codex-package.json"))
    manifest = {
        "version": package_manifest.get("version", PACKAGE_VERSION),
        "variant": package_manifest.get("variant", "codex-app-server"),
        "target": package_manifest.get("target", PACKAGE_TARGET),
        "package_sha256": EXPECTED_SHA256,
        "files": staged,
        "env_contract": {
            "HOME": "<files>/runtime/home",
            "CODEX_HOME": "<files>/runtime/home/.codex",
            "TMPDIR": "<files>/runtime/tmp",
            "PATH": "<nativeLibraryDir>:<files>/runtime/package/codex-path:/system/bin",
            "launch": "<nativeLibraryDir>/libcodex_app_server.so --listen stdio://",
        },
        "notes": (
            "Upstream rust-v0.153.4 discovers resources only from the exe path "
            "(bin/ or codex-resources/ beside codex-package.json); renamed "
            "lib*.so files lose automatic rg/zsh/bwrap/host discovery. No "
            "device success is claimed by this script."
        ),
    }
    # Sort keys for reproducibility.
    with open(os.path.join(assets, "runtime-manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("prepare_runtime: wrote runtime-manifest.json")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Stage Codex runtime for the APK.")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--package-dir", default=DEFAULT_PACKAGE_DIR)
    parser.add_argument("--expected-sha256", default=EXPECTED_SHA256)
    parser.add_argument("--out-jnilibs", default=DEFAULT_JNILIBS)
    parser.add_argument("--out-assets", default=DEFAULT_ASSETS)
    parser.add_argument(
        "--url",
        default="https://github.com/openai/codex/releases/download/rust-v0.153.4/codex-app-server-package-aarch64-unknown-linux-musl.tar.gz",
        help="Download the package from this URL when --package is missing.",
    )
    return parser.parse_args(argv)


def resolve(repo_path: str) -> str:
    return repo_path if os.path.isabs(repo_path) else os.path.join(REPO_ROOT, repo_path)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    package = resolve(args.package)
    package_dir = resolve(args.package_dir)
    jnilibs = resolve(args.out_jnilibs)
    assets = resolve(args.out_assets)

    if not os.path.isfile(package):
        if args.url:
            download_package(args.url, package)
        else:
            fail("package not found: %s (pass --url to download)" % package)

    verify_hash(package, args.expected_sha256)
    names = safe_extract(package, package_dir)
    print("prepare_runtime: extracted %d entries" % len(names))
    check_required_layout(names)
    staged = stage_libraries(package_dir, jnilibs)
    stage_assets(package_dir, assets, staged)
    print("prepare_runtime: OK version=%s target=%s" % (PACKAGE_VERSION, PACKAGE_TARGET))
    print("  jnilibs: " + os.path.relpath(jnilibs, REPO_ROOT))
    print("  assets:  " + os.path.relpath(assets, REPO_ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
