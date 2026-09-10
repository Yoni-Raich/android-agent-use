package dev.androidagent.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.androidagent.core.AgentSkill
import dev.androidagent.core.ReasoningEffortOption
import dev.androidagent.core.RunPhase
import dev.androidagent.core.RunState
import dev.androidagent.core.VoicePhase

// The composer: one rounded field and one button that is voice while the
// field is empty, send once there is text, and stop while the agent works.
// Steering a running agent keeps a separate stop beside the send button, so
// Stop is never more than one tap away. Model and reasoning share one chip
// under the field, which opens a sheet.

private val FieldBorder = Color(0xFF383838)
private val FieldBorderFocused = Color(0xFF4A4A4A)
private val ChipBorder = Color(0xFF333333)
private val ChipInk = Color(0xFFCFCFCF)
private val SheetFill = Color(0xFF1B1B1B)
private val OptionFill = Color(0xFF252525)
private val AttachmentFill = Color(0xFF2A2A2A)
private val MutedInk = Color(0xFF8F8F8F)

private enum class ComposerAction { VOICE, SEND, STEER, STOP }

@Composable
internal fun AgentComposer(
    state: AgentUiState,
    actions: AgentUiActions,
    // Where the voice button sits, so voice mode can grow out of it.
    onVoiceButtonPlaced: (Offset) -> Unit = {},
) {
    var draft by rememberSaveable(state.activeSessionId) { mutableStateOf("") }
    var choosingModel by remember { mutableStateOf(false) }
    val voiceActive = state.voiceState.active
    val active = state.runState.active && state.runState.sessionId == state.activeSessionId && !voiceActive
    val stopping = state.runState.phase == RunPhase.STOPPING && !voiceActive
    val voiceStopping = state.voiceState.phase == VoicePhase.STOPPING
    val voiceBusy = state.voiceState.phase in setOf(VoicePhase.STARTING, VoicePhase.STOPPING)
    val hasDraft = draft.isNotBlank()
    val canSend = state.activeSessionId != null && hasDraft && !state.isLoadingMessages && !stopping && !voiceStopping
    val action = when {
        active && hasDraft -> ComposerAction.STEER
        active -> ComposerAction.STOP
        hasDraft -> ComposerAction.SEND
        !state.runState.active || voiceActive -> ComposerAction.VOICE
        // Another chat is running; a message typed here waits for it.
        else -> ComposerAction.SEND
    }
    val submit = {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            if (active) actions.onSteer(text) else actions.onSend(text, state.attachments)
            draft = ""
        }
    }
    val showSkillSuggestions = draft.startsWith("\$") && !draft.contains(" ") && !draft.contains("\n")
    val skillQuery = if (showSkillSuggestions) draft.removePrefix("\$").trim() else ""
    val matchingSkills = remember(showSkillSuggestions, skillQuery, state.availableSkills) {
        when {
            !showSkillSuggestions -> emptyList()
            skillQuery.isEmpty() -> state.availableSkills
            else -> state.availableSkills.filter {
                it.name.contains(skillQuery, ignoreCase = true) || it.description.contains(skillQuery, ignoreCase = true)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.runState.active) {
            RunStatusRow(state.runState, showStop = !active && !voiceActive, onStop = actions.onStop)
        }
        if (state.queuedTurns.isNotEmpty()) QueuedTurns(state, actions)
        if (matchingSkills.isNotEmpty()) SkillSuggestions(matchingSkills) { name -> draft = "\$$name " }

        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val border by animateColorAsState(if (focused) FieldBorderFocused else FieldBorder, tween(200), label = "composer-border")
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, border)) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(
                    onClick = actions.onAttach,
                    enabled = !active && !voiceActive && state.activeSessionId != null,
                ) {
                    Icon(Icons.Outlined.Add, "Attach file")
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.attachments.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.attachments, key = { it.id }) { attachment ->
                                AttachmentChip(attachment.name) { actions.onRemoveAttachment(attachment.id) }
                            }
                        }
                    }
                    MessageField(
                        value = draft,
                        onValueChange = { draft = it },
                        enabled = state.activeSessionId != null,
                        placeholder = if (active) "Add an instruction…" else "Message Android Agent",
                        interaction = interaction,
                    )
                }
                if (action == ComposerAction.STEER) {
                    ComposerButton(ComposerAction.STOP, enabled = !stopping, onClick = actions.onStop, outlined = true)
                }
                ComposerButton(
                    action = action,
                    enabled = when (action) {
                        ComposerAction.VOICE -> state.activeSessionId != null && !state.isLoadingMessages && !voiceStopping
                        ComposerAction.STOP -> !stopping
                        ComposerAction.SEND, ComposerAction.STEER -> canSend
                    },
                    onClick = when (action) {
                        ComposerAction.VOICE -> actions.onVoiceToggle
                        ComposerAction.STOP -> actions.onStop
                        ComposerAction.SEND, ComposerAction.STEER -> submit
                    },
                    busy = action == ComposerAction.VOICE && voiceBusy,
                    voiceActive = voiceActive,
                    modifier = Modifier.onGloballyPositioned { onVoiceButtonPlaced(it.boundsInRoot().center) },
                )
            }
        }
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ModelChip(state, enabled = !active && !voiceActive) {
                if (state.availableModels.isEmpty()) actions.onOpenSettings() else choosingModel = true
            }
        }
    }
    if (choosingModel) ModelSheet(state, actions, onDismiss = { choosingModel = false })
}

@Composable
private fun MessageField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    interaction: MutableInteractionSource,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
        // Each line takes its own direction, as in the chat; RtlLines makes
        // any line with a Hebrew letter read right to left.
        textDirection = TextDirection.Content,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        minLines = 1,
        maxLines = 6,
        interactionSource = interaction,
        visualTransformation = RtlLines,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Message input" },
        decorationBox = { field ->
            // Full width, and handed down to the text itself: a Box otherwise
            // lets the text shrink to its own width, so a right-to-left line
            // would end mid-field instead of at the field's edge.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart, propagateMinConstraints = true) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            }
        },
    )
}

/**
 * Shows a right-to-left mark before each line that holds a Hebrew or Arabic
 * letter, so the line lays out right to left, without changing the draft.
 */
private object RtlLines : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val marks = rtlLineStarts(text.text)
        if (marks.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val shown = buildAnnotatedString {
            var from = 0
            for (at in marks) {
                append(text.subSequence(from, at))
                append(RLM)
                from = at
            }
            append(text.subSequence(from, text.length))
        }
        val mapping = object : OffsetMapping {
            // A caret at the start of a marked line sits after its mark.
            override fun originalToTransformed(offset: Int): Int = offset + marks.count { it <= offset }

            override fun transformedToOriginal(offset: Int): Int {
                val passed = marks.withIndex().count { (index, at) -> at + index < offset }
                return (offset - passed).coerceIn(0, text.length)
            }
        }
        return TransformedText(shown, mapping)
    }
}

@Composable
private fun ComposerButton(
    action: ComposerAction,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    busy: Boolean = false,
    voiceActive: Boolean = false,
) {
    val voice = action == ComposerAction.VOICE
    val fill by animateColorAsState(
        when {
            outlined -> Color.Transparent
            !enabled -> DisabledFill
            voice -> VoiceButtonBlue
            else -> Color.White
        },
        tween(220),
        label = "composer-button-fill",
    )
    val ink by animateColorAsState(
        when {
            !enabled -> DisabledInk
            outlined || voice -> Color.White
            else -> Color.Black
        },
        tween(220),
        label = "composer-button-ink",
    )
    val description = when (action) {
        ComposerAction.VOICE -> if (voiceActive) "End voice conversation" else "Start voice conversation"
        ComposerAction.SEND -> "Send message"
        ComposerAction.STEER -> "Steer agent"
        ComposerAction.STOP -> "Stop agent"
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .padding(4.dp)
            .then(if (outlined) Modifier.border(1.dp, if (enabled) FieldBorderFocused else DisabledFill, CircleShape) else Modifier)
            .background(fill, CircleShape)
            .semantics { contentDescription = description },
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.6f))
                },
                label = "composer-button-icon",
            ) { shown ->
                Icon(
                    imageVector = when (shown) {
                        ComposerAction.VOICE -> Icons.Default.GraphicEq
                        ComposerAction.STOP -> Icons.Default.Stop
                        ComposerAction.SEND, ComposerAction.STEER -> Icons.Default.ArrowUpward
                    },
                    contentDescription = null,
                    tint = ink,
                    modifier = if (shown == ComposerAction.STOP) Modifier.size(20.dp) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RunStatusRow(runState: RunState, showStop: Boolean, onStop: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(start = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AgentOrb(Modifier.size(24.dp), phase = runState.phase, controlling = runState.controlling)
        Text(
            runStatusText(runState),
            Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showStop) TextButton(onClick = onStop) { Text("Stop active task") }
    }
}

private fun runStatusText(runState: RunState): String {
    val status = runState.status.trim()
    return if (status.isEmpty() || status == "Ready") readableRunPhase(runState.phase) else runStatusLabel(status)
}

@Composable
private fun QueuedTurns(state: AgentUiState, actions: AgentUiActions) {
    Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.queuedTurns.size} queued${if (state.queuePaused) " · paused" else ""}", Modifier.weight(1f))
            if (state.queuePaused) TextButton(onClick = actions.onResumeQueue) { Text("Resume queue") }
        }
        state.queuedTurns.forEach { queued ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(queued.prompt, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { actions.onCancelQueued(queued.id) }) { Text("Cancel task") }
            }
        }
    }
}

@Composable
private fun SkillSuggestions(skills: List<AgentSkill>, onPick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FieldBorder),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Codex Skills",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${skills.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                items(skills, key = { it.path }) { skill ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(skill.name) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF282828),
                            border = BorderStroke(0.5.dp, Color(0xFF444444)),
                        ) {
                            Text(
                                "\$${skill.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                skill.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                skill.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(name: String, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = AttachmentFill) {
        Row(
            Modifier.heightIn(min = 36.dp).padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = ChipInk)
            Text(
                name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, "Remove $name", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModelChip(state: AgentUiState, enabled: Boolean, onClick: () -> Unit) {
    val model = state.selectedModel?.removePrefix("gpt-")
    val label = if (model == null) "Choose model" else "$model · ${state.selectedReasoningEffort ?: "default"}"
    val ink = if (enabled) ChipInk else DisabledInk
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ChipBorder),
        modifier = Modifier.semantics { contentDescription = "Choose model and reasoning" },
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(state: AgentUiState, actions: AgentUiActions, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val efforts = state.modelCatalog.firstOrNull { it.id == state.selectedModel }?.reasoningEfforts.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = SheetFill) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetLabel("MODEL")
            state.availableModels.forEach { model ->
                val selected = model == state.selectedModel
                Surface(
                    onClick = { actions.onModelSelected(model) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) OptionFill else Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(model.removePrefix("gpt-"), Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (efforts.isNotEmpty()) {
                SheetLabel("REASONING")
                (listOf<ReasoningEffortOption?>(null) + efforts).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            EffortOption(
                                option = option,
                                selected = option?.value == state.selectedReasoningEffort,
                                modifier = Modifier.weight(1f),
                            ) { actions.onReasoningEffortSelected(option?.value) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MutedInk,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun EffortOption(option: ReasoningEffortOption?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val note = option?.description?.takeIf { it.isNotBlank() } ?: if (option == null) "Model default" else ""
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color.White else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, ChipBorder),
        modifier = modifier.heightIn(min = 60.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                option?.value ?: "Default",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (note.isNotEmpty()) {
                Text(
                    note,
                    fontSize = 12.sp,
                    color = if (selected) Color(0xFF4A4A4A) else MutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
