package dev.androidagent.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * What the agent is allowed to launch, and what it has to ask about first.
 *
 * Intent text is model-supplied and frequently derived from screen content,
 * which the harness already treats as untrusted — a phone number read off a
 * page is not a phone number the user asked to dial. So every intent this
 * project launches is checked here first.
 *
 * Pure JVM on purpose: this is the security boundary for the intent layer,
 * and it is worth more as something covered by fast unit tests than as
 * something wired into `android.net.Uri`.
 */
object IntentPolicy {

    /**
     * Schemes that are refused outright.
     *
     * A positive allowlist was the first design and it does not survive
     * contact with the feature: app deep links use private schemes — `waze:`,
     * `spotify:`, `tg:` — and enumerating them would block exactly the case
     * this layer exists to serve. So the rule is structural instead: anything
     * that reads local data, injects a component, or executes is refused, and
     * ordinary opaque app schemes are allowed.
     *
     * - `file`, `content`, `android_resource`: read the device's own storage
     *   and providers. A model-supplied one is a data-exfiltration primitive.
     * - `intent`: Android's own serialisation format. It can name an arbitrary
     *   component, action, category and extras inside a single string, which
     *   would route straight around every check below.
     * - `javascript`, `data`: execute in whatever browser resolves them.
     * - `jar`: fetches and loads code.
     */
    val blockedSchemes: Set<String> = setOf(
        "file",
        "content",
        "android_resource",
        "intent",
        "android-app",
        "javascript",
        "data",
        "jar",
    )

    /**
     * Actions the agent may name.
     *
     * `ACTION_CALL` is deliberately absent: it places a call with no dialer
     * confirmation. `ACTION_DIAL` reaches the same screen and leaves the last
     * press to the user, so the capability is kept and the irreversible half
     * is not.
     */
    val allowedActions: Set<String> = setOf(
        "android.intent.action.VIEW",
        "android.intent.action.DIAL",
        "android.intent.action.SENDTO",
        "android.intent.action.SEARCH",
        "android.intent.action.WEB_SEARCH",
        "android.intent.action.MAIN",
    )

    /** Schemes whose whole purpose is to send something to another person. */
    private val messagingSchemes: Set<String> = setOf("sms", "smsto", "mms", "mmsto", "mailto")

    /**
     * Query keys that carry a payload rather than a destination.
     *
     * A `tel:` or a map link names a place to go. A `?text=` or `?body=` names
     * something to send, and that is the line between navigating and acting.
     */
    private val payloadKeys: Set<String> = setOf(
        "body", "text", "subject", "message", "amount", "cc", "bcc",
    )

    /** Hosts that carry a prefilled outbound message in an ordinary https URL. */
    private val messagingHosts: Set<String> = setOf(
        "wa.me", "api.whatsapp.com", "t.me", "telegram.me", "m.me",
    )

    private const val MAX_URI_CHARS = 2_000

    sealed interface Decision {
        /** Safe to launch without asking. */
        data class Allow(val uri: String?, val action: String) : Decision

        /**
         * Launchable, but it would send, pay, or otherwise act on someone
         * else's behalf. [what] is the sentence to put to the user.
         */
        data class NeedsConfirmation(val uri: String?, val action: String, val what: String) : Decision

        /** Refused. [reason] is a typed error code, [message] is for the model. */
        data class Deny(val reason: String, val message: String) : Decision
    }

    /**
     * Decide what to do with a proposed intent.
     *
     * @param action fully-qualified action, or null to default to VIEW.
     * @param uri the data URI, or null for an action that needs none.
     */
    fun evaluate(action: String?, uri: String?): Decision {
        val resolvedAction = action?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "android.intent.action.VIEW"
        if (resolvedAction !in allowedActions) {
            return Decision.Deny(
                "action_not_allowed",
                "\"$resolvedAction\" is not an action this agent may send. Allowed: " +
                    allowedActions.sorted().joinToString(", ") + ".",
            )
        }
        if (uri == null || uri.isBlank()) {
            return if (resolvedAction == "android.intent.action.MAIN") {
                Decision.Allow(null, resolvedAction)
            } else {
                Decision.Deny(
                    "uri_required",
                    "\"$resolvedAction\" needs a uri.",
                )
            }
        }
        val trimmed = uri.trim()
        if (trimmed.length > MAX_URI_CHARS) {
            return Decision.Deny(
                "uri_too_long",
                "The uri is ${trimmed.length} characters, above the $MAX_URI_CHARS limit.",
            )
        }
        if (trimmed.any { it == '\n' || it == '\r' || it.code < 0x20 }) {
            return Decision.Deny(
                "uri_malformed",
                "The uri contains control characters.",
            )
        }
        // The scheme is read off the raw text before parsing, because some
        // blocked schemes are not valid URIs at all — `android_resource` has an
        // underscore, which RFC 3986 forbids and java.net.URI rejects. Parsing
        // first would report those as merely malformed, which reads as "try a
        // different spelling" rather than "this is not allowed".
        declaredSchemeOf(trimmed)?.let { declared ->
            if (declared in blockedSchemes) {
                return Decision.Deny(
                    "scheme_blocked",
                    "\"$declared:\" is not a scheme this agent may open. It can read local data or " +
                        "name an arbitrary component, which would bypass the checks on this tool.",
                )
            }
        }
        val parsed = try {
            URI(trimmed)
        } catch (error: URISyntaxException) {
            return Decision.Deny("uri_malformed", "The uri could not be parsed: ${error.reason}")
        }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
            ?: return Decision.Deny(
                "uri_relative",
                "The uri has no scheme. A relative uri cannot be resolved to an app.",
            )
        val sideEffect = describeSideEffect(parsed, scheme, resolvedAction)
        return if (sideEffect == null) {
            Decision.Allow(trimmed, resolvedAction)
        } else {
            Decision.NeedsConfirmation(trimmed, resolvedAction, sideEffect)
        }
    }

    /**
     * The scheme as written, lowercased, or null when the text declares none.
     *
     * Deliberately laxer than RFC 3986 about what characters a scheme may
     * contain: the job here is to recognise a scheme someone is trying to
     * sneak past the list, not to validate it.
     */
    private fun declaredSchemeOf(uri: String): String? {
        val colon = uri.indexOf(':')
        if (colon <= 0) return null
        val candidate = uri.substring(0, colon)
        if (candidate.any { it == '/' || it == '?' || it == '#' }) return null
        return candidate.lowercase(Locale.ROOT)
    }

    /**
     * One sentence naming the side effect, or null when the intent only
     * navigates.
     */
    private fun describeSideEffect(uri: URI, scheme: String, action: String): String? {
        val query = queryOf(uri)
        val payload = if (query.containsKey("amount")) {
            "amount"
        } else {
            payloadKeys.firstOrNull { key -> query.containsKey(key) }
        }
        if (scheme in messagingSchemes) {
            val target = (uri.schemeSpecificPart ?: "").substringBefore('?').ifBlank { "a recipient" }
            return "Open a prefilled $scheme message to $target" +
                (payload?.let { ", carrying a $it" } ?: "") + "."
        }
        if (action == "android.intent.action.SENDTO") {
            return "Send to a recipient through another app."
        }
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host != null && host in messagingHosts && payload != null) {
            return "Open $host with a prefilled message ($payload)."
        }
        if (payload == "amount") {
            return "Start a payment."
        }
        if (payload != null && (scheme == "http" || scheme == "https")) {
            // A search URL also carries ?text=, so this is not on its own a
            // side effect; only the messaging hosts above are treated as one.
            return null
        }
        if (payload != null) {
            // Private app schemes are intentionally supported, but a payload
            // such as whatsapp://send?text=... is still an outbound action.
            // Ask rather than treating every unknown scheme as navigation.
            return "Open $scheme with a prefilled $payload."
        }
        return null
    }

    /** Query keys, lowercased. Values are never inspected or logged. */
    private fun queryOf(uri: URI): Map<String, String> {
        val raw = uri.query ?: uri.schemeSpecificPart?.substringAfter('?', "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore('=').lowercase(Locale.ROOT)
                key to pair.substringAfter('=', "")
            }
    }
}
