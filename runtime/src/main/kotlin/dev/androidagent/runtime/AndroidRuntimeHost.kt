package dev.androidagent.runtime

import android.content.Context
import dev.androidagent.core.RuntimeHost
import dev.androidagent.core.RuntimePhase
import dev.androidagent.core.RuntimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-phone Codex app-server host.
 *
 * Env/launch contract (mirrors the verified phone-shell probe):
 * - HOME=<files>/runtime/home
 * - CODEX_HOME=<files>/runtime/home/.codex (app-private; never leaves the app)
 * - TMPDIR=<files>/runtime/tmp (sibling of home so CODEX_HOME is not under the
 *   system temp dir, which would break Codex arg0 alias setup in release builds)
 * - PATH=<nativeLibraryDir>:<files>/runtime/package/codex-path:/system/bin
 * - Launch: <nativeLibraryDir>/libcodex_app_server.so --listen stdio://
 *   (no `app-server` subcommand; the staged binary already is the app-server).
 *
 * targetSdk 35 cannot execute app-writable files, so every native ELF is
 * packaged as lib*.so in the APK (see tools/prepare_runtime.py) and executed
 * from applicationInfo.nativeLibraryDir. Canonical package-layout symlinks are
 * recreated under <files>/runtime/package for diagnostics only.
 *
 * Known upstream blocker (rust-v0.153.4, install-context/src/lib.rs):
 * CodexPackageLayout::from_exe only recognises an executable inside a `bin/`
 * or `codex-resources/` directory next to codex-package.json. A renamed
 * lib*.so under nativeLibraryDir yields package_layout=None, so upstream falls
 * back to bare "rg" (PATH), a sibling "codex-code-mode-host" (PATH), and no
 * bundled zsh/bwrap. There is no upstream env/config override for the package
 * layout in this version, so helper resolution stays degraded until upstream
 * adds one or root proves app-UID exec behaviour on device. This host sets the
 * best-effort PATH and reports the exact failure; it never fakes discovery.
 *
 * Credentials stay app-private: this host only ensures CODEX_HOME exists and
 * writes a comment-only config.toml when none exists. It never reads
 * auth.json or any credential file, and it supervises only its own process.
 */
class AndroidRuntimeHost(private val appContext: Context) : RuntimeHost {

    private val _status = MutableStateFlow(RuntimeStatus())
    override val status: StateFlow<RuntimeStatus> = _status

    /** <files>/runtime : root of all app-private runtime state. */
    val runtimeRoot: File get() = File(appContext.filesDir, "runtime")

    /** App-private HOME. */
    override val homeDirectory: File get() = File(runtimeRoot, "home")

    /** App-private CODEX_HOME. */
    val codexHomeDirectory: File get() = File(homeDirectory, ".codex")

    /** App-private TMPDIR (sibling of home, never its parent). */
    val tmpDirectory: File get() = File(runtimeRoot, "tmp")

    /** Diagnostic package-layout links (never executed through). */
    val packageLinkDirectory: File get() = File(runtimeRoot, "package")

    /** APK native library dir holding the staged lib*.so executables. */
    val nativeLibraryDirectory: File
        get() = File(appContext.applicationInfo.nativeLibraryDir)

    private val lock = Mutex()
    private var process: Process? = null
    private var prepared = false

    override suspend fun prepare() {
        lock.withLock { prepareLocked() }
    }

    override suspend fun startAppServer(): Process {
        lock.withLock {
            if (!prepared) prepareLocked()
            val binary = withContext(Dispatchers.IO) { resolveServerBinary() }
                ?: error(
                    "Codex runtime missing in ${nativeLibraryDirectory.absolutePath}. " +
                        "Run tools/prepare_runtime.py and rebuild the APK."
                )
            val executable = withContext(Dispatchers.IO) { binary.canExecute() }
            if (!executable) {
                setStatus(RuntimePhase.ERROR, "Codex binary is not executable: ${binary.absolutePath}")
                error("Codex binary is not executable: ${binary.absolutePath}")
            }
            stopLocked()
            setStatus(RuntimePhase.PREPARING, "Starting Codex app-server")
            val started = withContext(Dispatchers.IO) {
                ProcessBuilder(binary.absolutePath, "--listen", "stdio://")
                    .directory(homeDirectory)
                    .apply {
                        environment().putAll(serverEnvironment(nativeLibraryDirectory))
                        redirectErrorStream(false)
                    }
                    .start()
            }
            process = started
            setStatus(RuntimePhase.RUNNING, "Codex app-server running")
            return started
        }
    }

    override suspend fun stop() {
        lock.withLock { stopLocked() }
    }

    /** Resolved server binary, or null when the APK staging is missing. */
    suspend fun serverBinaryFile(): File? =
        withContext(Dispatchers.IO) { resolveServerBinary() }

    /** Exact environment passed to the app-server process. */
    fun serverEnvironmentForCurrentConfig(): Map<String, String> =
        serverEnvironment(nativeLibraryDirectory)

    /** Supervised process, or null when stopped. Own process only. */
    fun currentProcess(): Process? = process

    // ---- internals (caller holds [lock]) ----

    private suspend fun prepareLocked() {
        setStatus(RuntimePhase.PREPARING, "Preparing Codex runtime")
        val created = withContext(Dispatchers.IO) {
            val dirs = listOf(
                runtimeRoot,
                homeDirectory,
                codexHomeDirectory,
                File(codexHomeDirectory, "tmp"),
                tmpDirectory,
                packageLinkDirectory
            )
            val failed = dirs.firstOrNull { dir -> !dir.isDirectory && !dir.mkdirs() && !dir.isDirectory }
            if (failed != null) return@withContext failed
            writeDefaultConfigIfMissing()
            null
        }
        if (created != null) {
            prepared = false
            setStatus(RuntimePhase.ERROR, "Cannot create ${created.absolutePath}")
            error("Cannot create ${created.absolutePath}")
        }
        val linkWarnings = withContext(Dispatchers.IO) { recreatePackageLinks() }
        val binary = withContext(Dispatchers.IO) { resolveServerBinary() }
        if (binary == null) {
            prepared = false
            setStatus(
                RuntimePhase.MISSING,
                "Staged Codex binary not found in ${nativeLibraryDirectory.absolutePath}. " +
                    "Run tools/prepare_runtime.py and rebuild the APK."
            )
            return
        }
        if (!binary.canExecute()) {
            prepared = false
            setStatus(RuntimePhase.ERROR, "Codex binary is not executable: ${binary.absolutePath}")
            return
        }
        prepared = true
        val detail = if (linkWarnings.isEmpty()) "" else " Link warnings: ${linkWarnings.joinToString("; ")}"
        setStatus(RuntimePhase.READY, "Codex runtime ready (${binary.name}).$detail")
    }

    private suspend fun stopLocked() {
        val current = process
        process = null
        if (current == null) {
            if (prepared) setStatus(RuntimePhase.READY, "Codex runtime ready")
            return
        }
        withContext(Dispatchers.IO) {
            current.destroy()
            if (!waitForExit(current, 2_000_000_000L)) {
                current.destroyForcibly()
                waitForExit(current, 2_000_000_000L)
            }
        }
        if (prepared) setStatus(RuntimePhase.READY, "Codex app-server stopped")
    }

    private fun resolveServerBinary(): File? {
        val dir = nativeLibraryDirectory
        for (name in SERVER_LIB_CANDIDATES) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
        }
        return null
    }

    private fun serverEnvironment(nativeLibDir: File): Map<String, String> {
        val path = listOf(
            nativeLibDir.absolutePath,
            File(packageLinkDirectory, "codex-path").absolutePath,
            "/system/bin"
        ).joinToString(":")
        return mapOf(
            "HOME" to homeDirectory.absolutePath,
            "CODEX_HOME" to codexHomeDirectory.absolutePath,
            "TMPDIR" to tmpDirectory.absolutePath,
            "PATH" to path
        )
    }

    /**
     * Comment-only default config. Written once, never overwritten, so an
     * existing user config (and any credentials it references) is preserved.
     */
    private fun writeDefaultConfigIfMissing() {
        val config = File(codexHomeDirectory, "config.toml")
        if (config.exists()) return
        runCatching {
            config.writeText(
                "# Managed by Android Agent. Credentials stay in app-private CODEX_HOME.\n" +
                    "# Helper discovery (rg/code-mode-host/zsh) is limited while the\n" +
                    "# upstream package layout cannot be preserved under nativeLibraryDir.\n"
            )
        }
    }

    /**
     * Recreate canonical-name symlinks -> nativeLibraryDir lib*.so files.
     * Diagnostic only: targetSdk 35 cannot execute through app-writable paths,
     * so the server is always launched via its nativeLibraryDir path.
     * Returns human-readable warnings; never throws.
     */
    private fun recreatePackageLinks(): List<String> {
        val warnings = mutableListOf<String>()
        val libDir = nativeLibraryDirectory
        for ((linkPath, libName) in PACKAGE_LINKS) {
            val link = File(packageLinkDirectory, linkPath)
            val target = File(libDir, libName)
            try {
                link.parentFile?.mkdirs()
                if (!target.isFile) {
                    warnings += "$libName missing in nativeLibraryDir"
                    continue
                }
                val linkPathNio = link.toPath()
                if (java.nio.file.Files.isSymbolicLink(linkPathNio)) {
                    val pointsAt = runCatching {
                        java.nio.file.Files.readSymbolicLink(linkPathNio).toString()
                    }.getOrDefault("")
                    if (pointsAt != target.absolutePath && pointsAt != target.toPath().toString()) {
                        java.nio.file.Files.deleteIfExists(linkPathNio)
                    }
                } else if (link.exists()) {
                    link.delete()
                }
                if (!java.nio.file.Files.exists(linkPathNio)) {
                    java.nio.file.Files.createSymbolicLink(linkPathNio, target.toPath())
                }
            } catch (e: Exception) {
                warnings += "link $linkPath: ${e.message}"
            }
        }
        copyAssetIfMissing("runtime/codex-package.json", File(packageLinkDirectory, "codex-package.json"))
        copyAssetIfMissing("runtime/runtime-manifest.json", File(packageLinkDirectory, "runtime-manifest.json"))
        return warnings
    }

    private fun copyAssetIfMissing(assetPath: String, dest: File) {
        if (dest.exists()) return
        runCatching {
            appContext.assets.open(assetPath).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun setStatus(phase: RuntimePhase, message: String) {
        _status.value = RuntimeStatus(phase = phase, message = message)
    }


    private fun waitForExit(p: Process, nanos: Long): Boolean {
        val deadline = System.nanoTime() + nanos
        while (p.isAlive) {
            if (System.nanoTime() >= deadline) return false
            try {
                Thread.sleep(25)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    companion object {
        const val SERVER_LIB_NAME = "libcodex_app_server.so"
        private val SERVER_LIB_CANDIDATES = arrayOf(
            SERVER_LIB_NAME,
            "libcodex-app-server.so"
        )

        /**
         * Canonical package path -> staged lib name. Mirrors
         * tools/prepare_runtime.py LIB_MAPPING (single source of truth for the
         * rename; keep both in sync).
         */
        val PACKAGE_LINKS: List<Pair<String, String>> = listOf(
            "bin/codex-app-server" to "libcodex_app_server.so",
            "bin/codex-code-mode-host" to "libcodex_code_mode_host.so",
            "codex-path/rg" to "libcodex_rg.so",
            "codex-resources/bwrap" to "libcodex_bwrap.so",
            "codex-resources/zsh/bin/zsh" to "libcodex_zsh.so"
        )
    }
}
