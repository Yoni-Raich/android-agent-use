package dev.androidagent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale

/**
 * What the agent has worked out about an app, kept across chats.
 *
 * Nothing the agent learned used to survive. The session workspace is rewritten
 * by the seeder on every access, the bundled skills are force-replaced on every
 * app start, and both are per-session anyway — so a selector discovered in one
 * chat was invisible to the next and rediscovered from scratch.
 *
 * This lives under `homeDirectory`, which is global across chats and is the one
 * place the seeder does not touch.
 *
 * The stable key is `(package, resourceId | contentDescription)` and never a
 * coordinate. A bounds centre is invalidated by any re-render; a resource id
 * survives one. That distinction is what makes remembering worth doing at all,
 * and it is why this could not have been built before the accessibility tree
 * existed.
 *
 * Pure JVM: no Android types, so the format and the staleness rules are
 * covered by fast unit tests rather than only by running the app.
 */
class KnowledgeStore(
    private val root: File,
    /** Injectable so tests do not depend on the wall clock. */
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * One thing the agent knows how to do in one app.
     *
     * @param selector the durable address — a resource id, or a content
     *   description when the app exposes no id. Never coordinates.
     * @param lastVerified when this was last seen to work. Selectors age out
     *   with app updates, and stale confidence is worse than no confidence, so
     *   this is mandatory rather than optional.
     */
    data class Record(
        val packageName: String,
        val screen: String,
        val selector: String,
        val does: String,
        val intent: String? = null,
        val fallbacks: List<String> = emptyList(),
        val lastVerified: Long = 0L,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("screen", screen)
            put("selector", selector)
            put("does", does)
            intent?.let { put("intent", it) }
            if (fallbacks.isNotEmpty()) {
                put("fallbacks", JsonArray(fallbacks.map { JsonPrimitive(it) }))
            }
            put("lastVerified", lastVerified)
        }
    }

    /** Everything known about one package, newest verification first. */
    fun read(packageName: String): List<Record> {
        val file = fileFor(packageName) ?: return emptyList()
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (text.length > MAX_FILE_CHARS) return emptyList()
        val parsed = runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrNull() ?: return emptyList()
        val records = runCatching { parsed["records"]!!.jsonArray }.getOrNull() ?: return emptyList()
        return records.mapNotNull { element ->
            runCatching { recordFrom(packageName, element.jsonObject) }.getOrNull()
        }.sortedByDescending { it.lastVerified }
    }

    /**
     * Add or replace one record.
     *
     * Replacement is by selector, not by insertion: re-learning the same
     * selector should refresh what we believe about it rather than pile a
     * second, possibly contradictory, entry behind the first.
     *
     * @return the stored record, with `lastVerified` stamped.
     */
    fun upsert(record: Record): Record {
        val file = requireNotNull(fileFor(record.packageName)) {
            "\"${record.packageName}\" is not a valid Android package name"
        }
        validate(record)
        val stamped = record.copy(lastVerified = now())
        val merged = (read(record.packageName).filterNot { it.selector == stamped.selector } + stamped)
            .sortedByDescending { it.lastVerified }
            .take(MAX_RECORDS_PER_PACKAGE)
        file.parentFile?.mkdirs()
        val payload = buildJsonObject {
            put("package", record.packageName)
            put("version", FORMAT_VERSION)
            put("records", JsonArray(merged.map { it.toJson() }))
        }
        // Written whole and replaced, so a crash mid-write cannot leave a
        // half-parsed file that read() would silently discard.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(payload.toString())
        if (!temp.renameTo(file)) {
            file.writeText(payload.toString())
            temp.delete()
        }
        return stamped
    }

    /** Packages this store holds anything for. */
    fun packages(): List<String> =
        root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(SUFFIX) }
            ?.map { it.name.removeSuffix(SUFFIX) }
            ?.sorted()
            .orEmpty()

    /**
     * True when a record is old enough that it should be checked rather than
     * trusted. Not an expiry: a stale selector is still the best guess
     * available, it just is not evidence any more.
     */
    fun isStale(record: Record): Boolean = now() - record.lastVerified > STALE_AFTER_MS

    /**
     * A compact, bounded view for one package.
     *
     * Deliberately per-package and capped rather than a whole-store dump: the
     * store grows without limit and the prompt does not.
     */
    fun summary(packageName: String, limit: Int = DEFAULT_SUMMARY_LIMIT): JsonObject {
        val records = read(packageName).take(limit.coerceIn(1, MAX_RECORDS_PER_PACKAGE))
        return buildJsonObject {
            put("package", packageName)
            put("known", records.size)
            put(
                "records",
                JsonArray(
                    records.map { record ->
                        buildJsonObject {
                            put("screen", record.screen)
                            put("selector", record.selector)
                            put("does", record.does)
                            record.intent?.let { put("intent", it) }
                            if (record.fallbacks.isNotEmpty()) {
                                put("fallbacks", JsonArray(record.fallbacks.map { JsonPrimitive(it) }))
                            }
                            // The model needs to know how much to trust this,
                            // not when it was written.
                            put("stale", isStale(record))
                        }
                    },
                ),
            )
            if (records.isEmpty()) {
                put(
                    "hint",
                    "Nothing is known about $packageName yet. Work it out from read_ui, then " +
                        "record what worked with remember_capability so the next chat does not " +
                        "have to rediscover it.",
                )
            } else {
                put(
                    "hint",
                    "A record marked stale:true is a hint to check, not a fact. Verify it against " +
                        "the current screen before relying on it, and re-record it once confirmed.",
                )
            }
        }
    }

    private fun recordFrom(packageName: String, json: JsonObject): Record = Record(
        packageName = packageName,
        screen = json.text("screen") ?: "",
        selector = json.text("selector") ?: error("record has no selector"),
        does = json.text("does") ?: "",
        intent = json.text("intent"),
        fallbacks = runCatching {
            json["fallbacks"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList()),
        lastVerified = json["lastVerified"]?.jsonPrimitive?.longOrNull ?: 0L,
    )

    private fun validate(record: Record) {
        require(record.selector.isNotBlank()) { "selector is required" }
        require(record.selector.length <= MAX_FIELD_CHARS) { "selector is too long" }
        require(record.screen.length <= MAX_FIELD_CHARS) { "screen is too long" }
        require(record.does.length <= MAX_FIELD_CHARS) { "does is too long" }
        require((record.intent?.length ?: 0) <= MAX_FIELD_CHARS) { "intent is too long" }
        require(record.fallbacks.size <= MAX_FALLBACKS) { "too many fallbacks" }
        // A coordinate is not a durable address, and storing one would quietly
        // undo the reason this store exists.
        require(!COORDINATE_RE.matches(record.selector.trim())) {
            "selector must be a resourceId or contentDescription, never coordinates"
        }
    }

    /**
     * Package names map to filenames directly, so anything that could escape
     * the directory is refused rather than sanitised — a sanitised path is a
     * path someone will later assume is the real package name.
     */
    private fun fileFor(packageName: String): File? {
        val trimmed = packageName.trim()
        if (!PACKAGE_RE.matches(trimmed)) return null
        return File(root, trimmed.lowercase(Locale.ROOT) + SUFFIX)
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val FORMAT_VERSION = 1

        /** Directory name under `homeDirectory`. */
        const val DIRECTORY = "knowledge"

        private const val SUFFIX = ".json"

        /** Two months. Long enough to survive a quiet app, short enough to catch a redesign. */
        const val STALE_AFTER_MS = 60L * 24 * 60 * 60 * 1_000

        const val MAX_RECORDS_PER_PACKAGE = 60
        const val DEFAULT_SUMMARY_LIMIT = 12
        private const val MAX_FIELD_CHARS = 400
        private const val MAX_FALLBACKS = 8
        private const val MAX_FILE_CHARS = 512 * 1024

        private val PACKAGE_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val COORDINATE_RE = Regex("[\\[(]?\\s*\\d+\\s*[,;]\\s*\\d+.*")

        /** Where the store lives for a given runtime home. */
        fun directoryIn(homeDirectory: File): File = File(homeDirectory, DIRECTORY)
    }
}
