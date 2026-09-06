import tempfile
import unittest
from pathlib import Path

from tools.prepare_runtime import (
    CODE_MODE_HOST_ANDROID_NAME,
    CODE_MODE_HOST_NAME,
    patch_code_mode_host_lookup,
)


class PrepareRuntimeTest(unittest.TestCase):
    def test_android_alias_matches_fixed_width_without_nul(self):
        self.assertEqual(len(CODE_MODE_HOST_ANDROID_NAME), len(CODE_MODE_HOST_NAME))
        self.assertNotIn(b"\0", CODE_MODE_HOST_ANDROID_NAME)

    def test_patch_leaves_valid_terminated_path(self):
        original = b"error:" + CODE_MODE_HOST_NAME + b"|" + CODE_MODE_HOST_NAME + b"\0tail"
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "codex-app-server"
            binary.write_bytes(original)
            patch_code_mode_host_lookup(str(binary))
            patched = binary.read_bytes()

        self.assertEqual(
            patched,
            b"error:"
            + CODE_MODE_HOST_NAME
            + b"|"
            + CODE_MODE_HOST_ANDROID_NAME
            + b"\0tail",
        )
        lookup_start = patched.index(CODE_MODE_HOST_ANDROID_NAME)
        lookup_end = lookup_start + len(CODE_MODE_HOST_ANDROID_NAME)
        self.assertNotIn(b"\0", patched[lookup_start:lookup_end])
        self.assertEqual(patched[lookup_end], 0)


if __name__ == "__main__":
    unittest.main()
