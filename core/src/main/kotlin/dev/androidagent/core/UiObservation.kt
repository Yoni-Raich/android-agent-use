package dev.androidagent.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * One semantic UI observation model shared by every device backend.
 *
 * The uiautomator XML dump and the accessibility node tree both reduce to
 * [UiNode], and both render through [UiObservationSerializer], so the model
 * sees one schema and one failure taxonomy no matter which backend answered.
 * The node *lists* still differ between backends — different traversal roots
 * and different inclusion rules — which is why the envelope carries `source`.
 */
data class UiNode(
    val nodeId: String,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: List<Int>?,
    val enabled: Boolean,
    val clickable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean,
    val packageName: String?,
    /** True for password fields. Their text is never emitted, whatever the backend reported. */
    val password: Boolean = false,
    val clickableAncestor: UiNode? = null,
) {
    fun isMeaningful(): Boolean =
        text != null || contentDescription != null || resourceId != null ||
            clickable || scrollable || focused || !enabled

    fun toJson(): JsonObject = buildJsonObject {
        put("nodeId", nodeId)
        if (password) {
            // Never emit the contents of a password field, even when the
            // platform handed us the characters rather than a mask.
            put("password", true)
        } else {
            text?.let { put("text", UiObservationSerializer.safeField(it)) }
        }
        contentDescription?.let { put("contentDescription", UiObservationSerializer.safeField(it)) }
        resourceId?.let { put("resourceId", it) }
        className?.let { put("class", it) }
        bounds?.let { values ->
            put("bounds", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        }
        put("enabled", enabled)
        put("clickable", clickable)
        put("scrollable", scrollable)
        put("focused", focused)
        clickableAncestor?.let { ancestor ->
            put("clickableAncestor", buildJsonObject {
                put("nodeId", ancestor.nodeId)
                ancestor.bounds?.let { values ->
                    put("bounds", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                }
                ancestor.className?.let { put("class", it) }
            })
        }
    }

    /** A parent reference carries position only; its own labels belong to the child. */
    fun asClickTarget(): UiNode = copy(
        text = null,
        contentDescription = null,
        resourceId = null,
        packageName = null,
        password = false,
        clickableAncestor = null,
    )
}

/** A parsed screen, before it is rendered for the model. */
data class UiObservation(val activePackage: String?, val nodes: List<UiNode>)

/**
 * Identity of the last observation handed to the model. [backend] scopes
 * unchanged-suppression: a fall-through from one gateway to another produces a
 * different node set, so an unchanged reply across backends would be a lie.
 */
data class ObservationFingerprint(val digest: String, val revision: Long, val backend: String)

/** The outcome of rendering one observation for the model. */
data class RenderedObservation(
    val text: String,
    val fingerprint: ObservationFingerprint?,
    val unchanged: Boolean,
)

/**
 * Shared revision counter and last-observation memory.
 *
 * One instance per process, handed to every gateway. Per-gateway counters
 * would make `revision` jump backwards when a call falls through to another
 * backend, and `unchangedSinceRevision` could then name a revision produced by
 * a different backend with a different node set.
 */
class ObservationState {
    private val revision = AtomicLong(0L)

    @Volatile
    private var last: ObservationFingerprint? = null

    fun nextRevision(): Long = revision.incrementAndGet()

    fun last(): ObservationFingerprint? = last

    fun record(fingerprint: ObservationFingerprint) {
        last = fingerprint
    }

    /**
     * Forget the last observation, so the next successful one carries a full
     * payload. Called when a run begins and after any failed observation: the
     * screen is then unknown, and a diff across a gap in knowledge is wrong.
     */
    fun reset() {
        last = null
    }
}

object UiObservationSerializer {
    const val MAX_OUTPUT_CHARS = 20_000
    const val MAX_UI_FIELD_CHARS = 256
    const val MAX_UI_NODES = 5_000

    /** Trim, cap and drop an empty attribute the way every backend must. */
    fun compactField(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_UI_FIELD_CHARS)

    /**
     * On-screen text reaches the model, so anything that looks like a token
     * leaves the device redacted. Applied at emit time so both backends and
     * every future one inherit it.
     */
    fun safeField(value: String): String =
        SecretRedactor.redactUiText(value).take(MAX_UI_FIELD_CHARS)

    fun semanticJson(
        observation: UiObservation,
        source: String,
        observationId: String,
        revision: Long,
        elapsedMs: Long,
        truncated: Boolean,
        stable: Boolean,
    ): String = buildJsonObject {
        put("ok", true)
        put("observationId", observationId)
        put("revision", revision)
        put("elapsedMs", elapsedMs)
        put("source", source)
        put("stable", stable)
        observation.activePackage?.let { put("activePackage", it) }
        put("truncated", truncated)
        put("nodes", buildJsonArray { observation.nodes.forEach { add(it.toJson()) } })
    }.toString()

    fun unchangedJson(
        activePackage: String?,
        nodeCount: Int,
        source: String,
        observationId: String,
        revision: Long,
        elapsedMs: Long,
        unchangedSinceRevision: Long,
    ): String = buildJsonObject {
        put("ok", true)
        put("observationId", observationId)
        put("revision", revision)
        put("elapsedMs", elapsedMs)
        put("source", source)
        put("stable", true)
        activePackage?.let { put("activePackage", it) }
        put("unchanged", true)
        put("unchangedSinceRevision", unchangedSinceRevision)
        put("nodeCount", nodeCount)
        put(
            "hint",
            "Screen is identical to revision $unchangedSinceRevision. Reuse those nodes; " +
                "if the previous action was meant to change the screen it did not take effect. " +
                "Call read_ui with force=true to resend the full node list.",
        )
    }.toString()

    /**
     * The one failure envelope. [remedy] and [alternatives] tell the model what
     * to do instead, so an unavailable backend is actionable rather than a
     * dead end.
     */
    fun failureJson(
        observationId: String,
        revision: Long,
        elapsedMs: Long,
        errorType: String,
        message: String,
        remedy: String? = null,
        alternatives: List<String> = emptyList(),
        reasons: List<String> = emptyList(),
    ): String = buildJsonObject {
        put("ok", false)
        put("observationId", observationId)
        put("revision", revision)
        put("elapsedMs", elapsedMs)
        put("source", "none")
        put("stable", false)
        put("errorType", errorType)
        put("message", message.take(MAX_UI_FIELD_CHARS))
        remedy?.let { put("remedy", it.take(MAX_UI_FIELD_CHARS)) }
        if (alternatives.isNotEmpty()) {
            put("alternatives", buildJsonArray { alternatives.forEach { add(JsonPrimitive(it)) } })
        }
        if (reasons.isNotEmpty()) {
            put("reasons", buildJsonArray { reasons.forEach { add(JsonPrimitive(it.take(MAX_UI_FIELD_CHARS))) } })
        }
    }.toString()

    /**
     * Digest of exactly what the model would receive, so a screen that merely
     * re-renders identically is recognised. Bounds are part of the node JSON,
     * so any real movement changes the digest.
     */
    fun digest(activePackage: String?, nodes: List<UiNode>): String {
        val payload = buildJsonObject {
            activePackage?.let { put("activePackage", it) }
            put("nodes", buildJsonArray { nodes.forEach { add(it.toJson()) } })
        }.toString()
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * Unchanged-suppression plus the truncation loop, once, for every backend.
     * The caller records [RenderedObservation.fingerprint] only on success.
     */
    fun render(
        observation: UiObservation,
        source: String,
        backend: String,
        observationId: String,
        revision: Long,
        elapsedMs: Long,
        previous: ObservationFingerprint?,
        force: Boolean,
        stable: Boolean,
    ): RenderedObservation {
        val fingerprint = ObservationFingerprint(
            digest = digest(observation.activePackage, observation.nodes),
            revision = revision,
            backend = backend,
        )
        if (!force && previous != null && previous.backend == backend && previous.digest == fingerprint.digest) {
            // The screen is byte-identical to what the model already holds.
            // Acknowledge it instead of resending the whole node list.
            return RenderedObservation(
                text = unchangedJson(
                    observation.activePackage, observation.nodes.size, source,
                    observationId, revision, elapsedMs, previous.revision,
                ),
                fingerprint = null,
                unchanged = true,
            )
        }
        var nodes = observation.nodes
        var truncated = false
        var text = semanticJson(
            observation, source, observationId, revision, elapsedMs, truncated, stable,
        )
        while (text.length > MAX_OUTPUT_CHARS && nodes.size > 1) {
            nodes = nodes.dropLast((nodes.size / 8).coerceAtLeast(1))
            truncated = true
            text = semanticJson(
                observation.copy(nodes = nodes), source, observationId, revision,
                elapsedMs, truncated, stable,
            )
        }
        return RenderedObservation(text = text, fingerprint = fingerprint, unchanged = false)
    }
}
