package dev.androidagent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The data directory the agent's own skills read and write: `~/memory`.
 *
 * What the agent learns is kept as a skill — a `SKILL.md` under
 * `$HOME/.agents/skills` that the catalog offers back in every later chat. The
 * data those skills work on cannot live beside them: the app replaces every
 * skill it ships, whole, on each start, so a lookup table stored inside one
 * would be deleted by the next app update. It lives here instead, under
 * `homeDirectory`, which nothing reseeds.
 *
 * The agent writes these files itself, through its own shell. There is no tool
 * for it and there does not need to be — the runtime already runs with full
 * access to this directory, and a tool would only be a second, narrower way to
 * do what a heredoc does. What the app owns is the layout, the signpost that
 * explains it, and the one file it ships defaults for.
 *
 * Pure JVM: no Android types, so the merge rules and the migration are covered
 * by fast unit tests rather than only by running the app.
 */
class MemoryStore(private val root: File) {

    /** Create the layout and the human-facing signpost. Safe to call repeatedly. */
    fun ensureLayout() {
        root.mkdirs()
        val readme = File(root, "README.md")
        if (!readme.isFile) writeAtomically(readme, README)
    }

    /** Durable user defaults, shared by every chat. Empty when nothing is set yet. */
    fun preferences(): JsonObject {
        val file = preferencesFile()
        if (!file.isFile) return JsonObject(emptyMap())
        val text = runCatching { file.readText() }.getOrNull() ?: return JsonObject(emptyMap())
        if (text.length > MAX_PREFERENCES_CHARS) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    }

    /**
     * Fill in the app's defaults for keys the document does not have yet.
     *
     * A merge rather than a write-if-absent: this runs on every app start, and
     * a later release that adds a default would otherwise never reach a phone
     * that already has the file. Whatever is already there wins, so a value the
     * user chose is never replaced by the default it overrode.
     */
    fun ensurePreferences(defaults: String) {
        val parsed = requireNotNull(
            runCatching { Json.parseToJsonElement(defaults).jsonObject }.getOrNull(),
        ) { "default preferences are not a JSON object" }
        val existing = preferences()
        val merged = mergeMissing(existing, parsed)
        if (merged == existing && preferencesFile().isFile) return
        val text = merged.toString()
        if (text.length > MAX_PREFERENCES_CHARS) return
        val file = preferencesFile()
        file.parentFile?.mkdirs()
        writeAtomically(file, text)
    }

    /**
     * Pull the per-session `preferences.json` files written before preferences
     * were global into the one that now is, newest file first.
     *
     * Existing values win. A preference already recorded globally was set by a
     * later, deliberate act; a per-session file is a snapshot of one chat, and
     * several of them can disagree. Each file is deleted once its content is
     * safely merged, so afterwards there is exactly one preferences file rather
     * than a global one plus a scatter of stale copies the model might read
     * instead. A file that cannot be read or parsed is left alone rather than
     * deleted on a guess about what was in it.
     */
    fun absorbLegacyPreferences(files: List<File>) {
        val candidates = files.filter { it.isFile && it.length() > 0L }
            .sortedByDescending { it.lastModified() }
        if (candidates.isEmpty()) return
        var merged = preferences()
        var absorbed = false
        for (file in candidates) {
            val parsed = runCatching {
                val text = file.readText()
                require(text.length <= MAX_PREFERENCES_CHARS)
                Json.parseToJsonElement(text).jsonObject
            }.getOrNull() ?: continue
            merged = mergeMissing(merged, parsed)
            absorbed = true
            file.delete()
        }
        if (!absorbed) return
        val text = merged.toString()
        if (text.length > MAX_PREFERENCES_CHARS) return
        val target = preferencesFile()
        target.parentFile?.mkdirs()
        writeAtomically(target, text)
    }

    private fun preferencesFile(): File = File(root, PREFERENCES_FILE)

    private fun mergeMissing(base: JsonObject, incoming: JsonObject): JsonObject {
        val merged = base.toMutableMap()
        for ((key, value) in incoming) {
            val existing = merged[key]
            merged[key] = when {
                existing == null -> value
                existing is JsonObject && value is JsonObject -> mergeMissing(existing, value)
                else -> existing
            }
        }
        return JsonObject(merged)
    }

    private fun writeAtomically(file: File, text: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    companion object {
        /** Directory name under `homeDirectory`. */
        const val DIRECTORY = "memory"

        const val PREFERENCES_FILE = "preferences.json"

        private const val MAX_PREFERENCES_CHARS = 32 * 1024

        /** Where the data lives for a given runtime home. */
        fun directoryIn(homeDirectory: File): File = File(homeDirectory, DIRECTORY)

        private val README = """
            # Shared memory

            This folder is global. Every chat on this phone reads and writes the same
            files, and nothing here is reseeded or overwritten by the app the way a
            session workspace is.

            - `preferences.json` — user defaults: preferred apps, addresses, contacts.
            - `<skill-name>/` — the data one of the agent's skills keeps: a lookup
              table, a list, whatever that skill was built to remember.

            The skills themselves live in `../.agents/skills`. Data is kept here rather
            than beside them because the app replaces the skills it ships on every
            update, and that would take the data with it.

            These are plain files. You can read, edit or delete any of them, and the
            app's Files sheet shows the same folder.
        """.trimIndent() + "\n"
    }
}
