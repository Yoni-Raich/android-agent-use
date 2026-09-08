package dev.androidagent.a11y

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import dev.androidagent.core.IntentPolicy
import dev.androidagent.core.ToolNotServiceable
import dev.androidagent.core.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Reaching a destination directly instead of walking there through the UI.
 *
 * Every task used to start at a launcher screen and navigate, even when a
 * single intent would land exactly on the target. That is slower, more
 * fragile, and spends model turns on navigation that carries no decision.
 *
 * Both tools run in-process, so they work with no ADB connection. Neither
 * accepts an explicit component from the model: [IntentPolicy] rejects the
 * `intent:` scheme for the same reason, since a named component would route
 * around every check.
 */
internal class IntentTools(
    private val context: Context,
    /** Max apps reported by a resolve, so a broad action cannot flood the reply. */
    private val maxMatches: Int = 20,
) {

    /**
     * Read-only. Reports which apps would handle an intent, so the model can
     * check before committing to something with a side effect.
     */
    fun resolve(arguments: JsonObject): ToolResult {
        val action = arguments.string("action")
        val uri = arguments.string("uri")
        return when (val decision = IntentPolicy.evaluate(action, uri, userConfirmed = true)) {
            // Confirmation is irrelevant here: resolving launches nothing.
            // A Deny still stands, because it means the intent is malformed
            // or forbidden and reporting its handlers would be misleading.
            is IntentPolicy.Decision.Deny -> denied(decision)
            is IntentPolicy.Decision.NeedsConfirmation -> ToolResult(
                "unreachable: userConfirmed was set",
                success = false,
            )
            is IntentPolicy.Decision.Allow -> {
                val intent = build(decision.action, decision.uri)
                val matches = query(intent)
                ToolResult(
                    buildJsonObject {
                        put("ok", true)
                        put("action", decision.action)
                        decision.uri?.let { put("uri", it) }
                        put("resolves", matches.isNotEmpty())
                        put("handlers", JsonArray(matches.map { it.toJson() }))
                        if (matches.isEmpty()) {
                            put(
                                "hint",
                                "Nothing on this device handles that intent. Either the app is " +
                                    "not installed, or it is not visible to this app. Fall back " +
                                    "to open_app and UI navigation.",
                            )
                        }
                    }.toString(),
                )
            }
        }
    }

    /**
     * Launches. The confirmation gate here is prompt-level, not enforced:
     * `userConfirmed` is set by the model, and the model is instructed to set
     * it only after the user agreed in the conversation. It is a speed bump
     * against a deep link scraped off a page being fired without anyone
     * noticing, not a guarantee — a real guarantee needs an approval round
     * trip through the engine, which this tool has no channel for.
     */
    fun open(arguments: JsonObject): ToolResult {
        val action = arguments.string("action")
        val uri = arguments.string("uri")
        val confirmed = arguments["userConfirmed"]?.jsonPrimitive?.booleanOrNull == true
        val pkg = arguments.string("package")
        return when (val decision = IntentPolicy.evaluate(action, uri, confirmed)) {
            is IntentPolicy.Decision.Deny -> denied(decision)
            is IntentPolicy.Decision.NeedsConfirmation -> ToolResult(
                buildJsonObject {
                    put("ok", false)
                    put("errorType", "confirmation_required")
                    put("message", decision.what)
                    put(
                        "remedy",
                        "Ask the user, in the conversation, whether to do this. If they agree, " +
                            "call open_intent again with userConfirmed=true. Do not set it on " +
                            "your own judgement.",
                    )
                }.toString(),
                success = false,
            )
            is IntentPolicy.Decision.Allow -> launch(decision, pkg)
        }
    }

    private fun launch(decision: IntentPolicy.Decision.Allow, pkg: String?): ToolResult {
        val intent = build(decision.action, decision.uri)
        if (pkg != null) {
            require(PACKAGE_RE.matches(pkg)) { "package is not a valid Android package name" }
            // A package hint narrows an ambiguous link to one app. It is not a
            // component: the app still picks its own entry point.
            intent.setPackage(pkg)
        }
        if (query(intent).isEmpty()) {
            throw ToolNotServiceable(
                "no_handler",
                "Nothing on this device handles ${decision.action}" +
                    (decision.uri?.let { " $it" } ?: "") +
                    (pkg?.let { " in $it" } ?: "") + ".",
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult(
                buildJsonObject {
                    put("ok", true)
                    put("launched", decision.action)
                    decision.uri?.let { put("uri", it) }
                    put(
                        "note",
                        "The intent was dispatched. Confirm with read_ui that the expected " +
                            "screen actually opened before acting on it.",
                    )
                }.toString(),
            )
        } catch (error: SecurityException) {
            ToolResult(
                buildJsonObject {
                    put("ok", false)
                    put("errorType", "launch_denied")
                    put("message", error.message ?: "The system refused the launch.")
                    put(
                        "remedy",
                        "Background activity launches are restricted from Android 10. This app " +
                            "is exempt while it holds Display over other apps (SYSTEM_ALERT_WINDOW) " +
                            "for the floating card. Ask the user to confirm that permission is " +
                            "still granted.",
                    )
                }.toString(),
                success = false,
            )
        }
    }

    /**
     * `FLAG_GRANT_*_URI_PERMISSION` is never set. Handing another app a
     * permission on our data is not something an intent built from model text
     * gets to do.
     */
    private fun build(action: String, uri: String?): Intent =
        if (uri == null) Intent(action) else Intent(action, Uri.parse(uri))

    private fun query(intent: Intent): List<ResolveInfo> =
        runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList()).take(maxMatches)

    private fun ResolveInfo.toJson(): JsonObject = buildJsonObject {
        put("package", activityInfo?.packageName ?: "unknown")
        // The label is what the user sees in a chooser, which is what makes
        // the reply legible when several apps claim the same link.
        runCatching { loadLabel(context.packageManager)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { put("label", it) }
        put("default", isDefault)
    }

    private fun denied(decision: IntentPolicy.Decision.Deny): ToolResult = ToolResult(
        buildJsonObject {
            put("ok", false)
            put("errorType", decision.reason)
            put("message", decision.message)
        }.toString(),
        success = false,
    )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private val PACKAGE_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

        val DEFINITIONS_SPEC: List<IntentToolSpec> = listOf(
            IntentToolSpec(
                name = "resolve_intent",
                description = "Check which installed apps would handle an action and/or uri, without " +
                    "launching anything. Read-only. Use it before open_intent when you are not sure " +
                    "the deep link is supported, instead of launching and hoping.",
                properties = mapOf("action" to "string", "uri" to "string"),
                required = emptyList(),
            ),
            IntentToolSpec(
                name = "open_intent",
                description = "Open a destination directly by intent or deep link instead of navigating " +
                    "there through the UI — a maps route, a specific chat, a settings screen. Prefer this " +
                    "over open_app plus taps when a link reaches the target. If the reply is " +
                    "confirmation_required, ask the user first, then retry with userConfirmed=true. " +
                    "Always verify with read_ui that the expected screen opened.",
                properties = mapOf(
                    "action" to "string",
                    "uri" to "string",
                    "package" to "string",
                    "userConfirmed" to "boolean",
                ),
                required = emptyList(),
            ),
        )
    }
}

/** Schema for one intent tool, rendered by the gateway's own `tool` helper. */
internal data class IntentToolSpec(
    val name: String,
    val description: String,
    val properties: Map<String, String>,
    val required: List<String>,
)
