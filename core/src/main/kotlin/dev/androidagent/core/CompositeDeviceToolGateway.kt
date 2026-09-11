package dev.androidagent.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Routes each tool to the first backend that declares it, and falls through to
 * the next when that backend reports the capability is absent.
 *
 * The advertised surface is deliberately **static**. Codex binds the tool list
 * at `thread/start` and never re-sends it on `thread/resume`, so a surface that
 * shrank when the accessibility service was switched off would leave every
 * running thread holding a list that no longer matches reality, with no way to
 * correct it. Availability is therefore reported at invoke time, as a typed
 * failure the model can act on.
 *
 * @param members backends in priority order, most preferred first.
 */
class CompositeDeviceToolGateway(
    private val members: List<DeviceToolGateway>,
) : DeviceToolGateway {

    init {
        require(members.isNotEmpty()) { "A composite gateway needs at least one backend" }
    }

    /** Tool name to its ordered fallback chain. */
    private val routes: Map<String, List<DeviceToolGateway>> =
        members
            .flatMap { member -> member.definitions.map { it.name to member } }
            .groupBy({ it.first }, { it.second })

    /** First declaration of a name wins; a later duplicate only joins its fallback chain. */
    override val definitions: List<ToolDefinition> =
        members.flatMap { it.definitions }.distinctBy { it.name }

    override fun beginRun(runId: String, workspace: File) {
        val failures = mutableListOf<Throwable>()
        for (member in members) {
            runCatching { member.beginRun(runId, workspace) }.onFailure { failures += it }
        }
        // A half-armed composite is the worst state to be in: one backend would
        // accept device calls while another silently refuses them.
        if (failures.isNotEmpty()) {
            revoke()
            throw failures.first()
        }
    }

    override fun revoke() {
        // Every member is revoked even when an earlier one throws. Skipping a
        // revocation would leave a live backend after Stop.
        val failures = mutableListOf<Throwable>()
        for (member in members) {
            runCatching { member.revoke() }.onFailure { failures += it }
        }
        failures.firstOrNull()?.let { throw it }
    }

    override fun needsControl(name: String): Boolean =
        // Unrouted names fail safe as visible control, matching the ADB gateway.
        routes[name]?.firstOrNull()?.needsControl(name) ?: true

    override fun hidesOverlayDuringCapture(name: String): Boolean =
        routes[name]?.firstOrNull()?.hidesOverlayDuringCapture(name) ?: false

    override fun statusLine(): String? =
        members.mapNotNull { it.statusLine() }.joinToString(" | ").takeIf { it.isNotEmpty() }

    /**
     * The union over live members, restricted to what this composite actually
     * advertises.
     *
     * A name served by a dead first choice and a live fallback is ready: that
     * is exactly what the fallback chain is for, and it is why a down ADB
     * transport does not make `read_ui` or `open_intent` unavailable.
     */
    override fun readyTools(): Set<String> {
        val live = members.flatMap { member -> runCatching { member.readyTools() }.getOrDefault(emptySet()) }
        return definitions.map { it.name }.toSet() intersect live.toSet()
    }

    override suspend fun invoke(name: String, arguments: JsonObject): ToolResult {
        if (name == "device_status") {
            // Only the composite knows every backend, so it answers this itself.
            return ToolResult(statusLine() ?: "No device backend is configured.")
        }
        val chain = routes[name] ?: return ToolResult("Unknown tool: $name", success = false)
        val reasons = mutableListOf<String>()
        for (member in chain) {
            try {
                return member.invoke(name, arguments)
            } catch (absent: ToolNotServiceable) {
                // Contract: nothing was dispatched, so another backend may try.
                reasons += "${absent.errorType}: ${absent.message}"
            }
            // Any other exception propagates: the action may already have been
            // committed, and repeating it on another backend could double it.
        }
        return unavailable(name, reasons)
    }

    override suspend fun cancel() {
        // The coordinator budgets one timeout for the whole composite, so the
        // members are cancelled together rather than one after another.
        coroutineScope {
            members.map { member -> async { runCatching { member.cancel() } } }.awaitAll()
        }
    }

    private fun unavailable(name: String, reasons: List<String>): ToolResult = ToolResult(
        UiObservationSerializer.failureJson(
            observationId = "-",
            revision = 0,
            elapsedMs = 0,
            errorType = "backend_unavailable",
            message = "No device backend can currently serve \"$name\".",
            remedy = "Ask the user to enable the Hey Mike accessibility service in " +
                "Settings > Accessibility, or to connect Wireless Debugging.",
            alternatives = definitions.map { it.name }.filter { it != name && it in READ_ONLY_ALTERNATIVES },
            reasons = reasons,
        ),
        success = false,
    )

    private companion object {
        /** Tools worth suggesting when the requested one has no live backend. */
        val READ_ONLY_ALTERNATIVES = setOf("read_ui", "screenshot", "device_status")
    }
}
