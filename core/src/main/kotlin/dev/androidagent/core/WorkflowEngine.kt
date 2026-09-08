package dev.androidagent.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
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

/**
 * Runs a known sequence of steps without a model round trip per step.
 *
 * The model pays a full turn for every tap today. `act_and_observe` compresses
 * exactly two calls into one; everything longer — open the app, wait, find a
 * node, tap, wait for the screen to settle, find another, type, send, verify —
 * is still one round trip each. The bundled skills describe those sequences
 * accurately, but they are prompt text and nothing executes them.
 *
 * The safety rule from `act_and_observe` is extended rather than changed:
 * **a step that already committed is never re-run because a later step
 * failed.** The result names the failing step, the completed prefix and the
 * observation at that moment, so the model resumes instead of restarting.
 */
class WorkflowEngine(
    /** Invokes one tool. The composite, so a step reaches whichever backend serves it. */
    private val invokeTool: suspend (String, JsonObject) -> ToolResult,
    private val store: WorkflowStore,
    /** True once the run has been revoked; checked between every step. */
    private val isRevoked: () -> Boolean,
) {

    /**
     * Step kinds, deliberately a closed set.
     *
     * Anything not here is a tool the model should call itself. A workflow that
     * could run arbitrary tools would be a second, weaker agent loop with none
     * of the coordinator's guarantees.
     */
    private val allowedTools = setOf(
        "open_app", "open_intent", "resolve_intent",
        "tap", "tap_node", "swipe", "scroll_node",
        "type_text", "set_text", "key",
        "read_ui", "wait_for_change", "screenshot",
    )

    /**
     * Tools that change something. A failure after one of these has run is
     * reported as "already committed", never retried.
     */
    private val committing = setOf(
        "open_app", "open_intent", "tap", "tap_node", "swipe",
        "scroll_node", "type_text", "set_text", "key",
    )

    suspend fun run(arguments: JsonObject): ToolResult {
        val steps = arguments["steps"]?.let { element ->
            runCatching { element.jsonArray }.getOrNull()
        } ?: throw IllegalArgumentException("steps is required and must be an array")
        require(steps.isNotEmpty()) { "steps cannot be empty" }
        require(steps.size <= MAX_STEPS) { "a workflow is limited to $MAX_STEPS steps" }

        val totalBudget = (arguments["totalBudgetMs"]?.jsonPrimitive?.longOrNull ?: DEFAULT_TOTAL_MS)
            .coerceIn(1_000L, MAX_TOTAL_MS)
        val startedAt = System.nanoTime()
        val completed = mutableListOf<JsonObject>()

        for ((index, element) in steps.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (isRevoked()) {
                // Stop must abort mid-workflow exactly as it aborts one tool.
                return outcome(
                    ok = false,
                    completed = completed,
                    failedAt = index,
                    error = "stopped",
                    message = "Run stopped. The steps already completed are listed; nothing after them ran.",
                )
            }
            val step = runCatching { element.jsonObject }.getOrNull()
                ?: return outcome(
                    false, completed, index, "malformed_step",
                    "Step $index is not an object.",
                )
            val tool = step["tool"]?.jsonPrimitive?.contentOrNull
                ?: return outcome(
                    false, completed, index, "malformed_step",
                    "Step $index has no \"tool\".",
                )
            if (tool !in allowedTools) {
                return outcome(
                    false, completed, index, "tool_not_allowed",
                    "\"$tool\" cannot run inside a workflow. Allowed: " +
                        allowedTools.sorted().joinToString(", ") + ".",
                )
            }
            val args = step["arguments"] as? JsonObject ?: buildJsonObject { }
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
            if (elapsed >= totalBudget) {
                return outcome(
                    false, completed, index, "budget_exhausted",
                    "The workflow used its ${totalBudget}ms budget before step $index. " +
                        "Nothing at or after that step ran.",
                )
            }
            val stepBudget = (step["timeoutMs"]?.jsonPrimitive?.longOrNull ?: DEFAULT_STEP_MS)
                .coerceIn(500L, totalBudget - elapsed)

            val result = try {
                withTimeoutOrNull(stepBudget) { invokeTool(tool, args) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The step threw. If it was a committing tool the device may
                // already have changed, which is exactly what the model needs
                // to know before it decides what to do next.
                return outcome(
                    false, completed, index, "step_failed",
                    "Step $index (\"$tool\") failed: ${failure.message}",
                    committed = tool in committing,
                )
            } ?: return outcome(
                false, completed, index, "step_timeout",
                "Step $index (\"$tool\") did not finish within ${stepBudget}ms.",
                committed = tool in committing,
            )

            if (!result.success) {
                return outcome(
                    false, completed, index, "step_failed",
                    "Step $index (\"$tool\") reported failure: ${result.text.take(MAX_STEP_TEXT)}",
                    committed = tool in committing,
                )
            }
            completed += buildJsonObject {
                put("step", index)
                put("tool", tool)
                put("result", result.text.take(MAX_STEP_TEXT))
            }
        }
        return outcome(true, completed, failedAt = null, error = null, message = null)
    }

    /** Persist a sequence that worked, keyed by package, beside the knowledge store. */
    fun save(arguments: JsonObject): ToolResult {
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("name is required")
        val pkg = arguments["package"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("package is required")
        val steps = arguments["steps"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: throw IllegalArgumentException("steps is required and must be an array")
        val saved = store.save(
            WorkflowStore.Workflow(
                name = name,
                packageName = pkg,
                description = arguments["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                steps = steps,
            ),
        )
        return ToolResult(
            buildJsonObject {
                put("ok", true)
                put("name", saved.name)
                put("package", saved.packageName)
                put("steps", saved.steps.size)
                put(
                    "note",
                    "Saved. A later chat can list it with list_workflows and run it with " +
                        "run_workflow. Verify it still works before trusting it: app updates " +
                        "move selectors.",
                )
            }.toString(),
        )
    }

    fun list(arguments: JsonObject): ToolResult {
        val pkg = arguments["package"]?.jsonPrimitive?.contentOrNull?.trim()
        val workflows = if (pkg.isNullOrEmpty()) store.all() else store.forPackage(pkg)
        return ToolResult(
            buildJsonObject {
                put("count", workflows.size)
                put(
                    "workflows",
                    JsonArray(
                        workflows.map { workflow ->
                            buildJsonObject {
                                put("name", workflow.name)
                                put("package", workflow.packageName)
                                put("description", workflow.description)
                                put("steps", workflow.steps)
                            }
                        },
                    ),
                )
                if (workflows.isEmpty()) {
                    put(
                        "hint",
                        "Nothing saved yet. Once you work out a sequence that runs cleanly, " +
                            "store it with save_workflow so the next chat does not rebuild it.",
                    )
                }
            }.toString(),
        )
    }

    private fun outcome(
        ok: Boolean,
        completed: List<JsonObject>,
        failedAt: Int?,
        error: String?,
        message: String?,
        committed: Boolean = false,
    ): ToolResult = ToolResult(
        buildJsonObject {
            put("ok", ok)
            put("completedSteps", JsonArray(completed))
            failedAt?.let { put("failedAtStep", it) }
            error?.let { put("errorType", it) }
            message?.let { put("message", it) }
            if (!ok) {
                put("lastCommittedStepMayHaveRun", committed)
                put(
                    "remedy",
                    if (committed) {
                        "The failing step may already have changed the device. Read the screen " +
                            "before deciding: do not re-run the whole workflow, and do not repeat " +
                            "that step blindly. Continue from where it stopped."
                    } else {
                        "Nothing was committed by the failing step. Continue from it once the " +
                            "cause is addressed; the steps listed above have already run and " +
                            "must not be repeated."
                    },
                )
            }
        }.toString(),
        success = ok,
    )

    companion object {
        const val MAX_STEPS = 24
        const val DEFAULT_STEP_MS = 8_000L
        const val DEFAULT_TOTAL_MS = 45_000L

        /**
         * The coordinator holds a process-wide lock for the whole tool call and
         * budgets two seconds for cancel, so a workflow that ran for minutes
         * would make Stop feel broken. Ninety seconds is already generous.
         */
        const val MAX_TOTAL_MS = 90_000L

        private const val MAX_STEP_TEXT = 1_500
    }
}
