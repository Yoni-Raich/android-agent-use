package dev.androidagent.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import dev.androidagent.core.ChatMessage
import dev.androidagent.core.ConnectionPhase
import dev.androidagent.core.EngineEvent
import dev.androidagent.core.RunPhase
import dev.androidagent.core.RuntimePhase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val AgentDarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF18245E),
    primaryContainer = Color(0xFF303B75),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFF83D9CA),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF174E47),
    onSecondaryContainer = Color(0xFFA1F2E1),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF151820),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AgentLightColors = lightColorScheme(
    primary = Color(0xFF4658A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF21685E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA5F2E3),
    onSecondaryContainer = Color(0xFF00201B),
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF8F8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun AndroidAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AgentDarkColors else AgentLightColors,
        typography = androidx.compose.material3.Typography().copy(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAgentScreen(
    state: AgentUiState,
    actions: AgentUiActions,
) {
    AndroidAgentTheme {
        val drawerState = rememberDrawerState(
            initialValue = if (state.isDrawerOpen) DrawerValue.Open else DrawerValue.Closed,
        )
        val scope = rememberCoroutineScope()

        LaunchedEffect(state.isDrawerOpen) {
            if (state.isDrawerOpen) drawerState.open() else drawerState.close()
        }
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.currentValue }
                .collectLatest { actions.onDrawerChanged(it == DrawerValue.Open) }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 360.dp),
                ) {
                    AgentDrawer(
                        state = state,
                        actions = actions,
                        close = { scope.launch { drawerState.close() } },
                    )
                }
            },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AgentTopBar(
                        state = state,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSettings = actions.onOpenSettings,
                        onOpenFiles = actions.onOpenWorkspaceFiles,
                    )
                },
                bottomBar = {
                    AgentComposer(
                        state = state,
                        actions = actions,
                    )
                },
            ) { padding ->
                AgentChatContent(
                    state = state,
                    actions = actions,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }

        if (state.isSettingsOpen) {
            AgentSettingsSheet(state = state, actions = actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentTopBar(
    state: AgentUiState,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = state.activeSessionTitle?.takeIf { it.isNotBlank() } ?: "Android Agent",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                RunStatusPill(runState = state.runState)
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.semantics { contentDescription = "Open sessions" },
            ) {
                Icon(Icons.Default.Menu, contentDescription = null)
            }
        },
        actions = {
            IconButton(
                onClick = onOpenFiles,
                modifier = Modifier.semantics { contentDescription = "Open workspace files" },
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics { contentDescription = "Open settings" },
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun RunStatusPill(runState: dev.androidagent.core.RunState) {
    val active = runState.active
    val color = when {
        runState.phase == RunPhase.ERROR -> MaterialTheme.colorScheme.error
        runState.controlling -> MaterialTheme.colorScheme.secondary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = runState.status.ifBlank { readableRunPhase(runState.phase) },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgentDrawer(
    state: AgentUiState,
    actions: AgentUiActions,
    close: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Android Agent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Local Codex workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = close) {
                Icon(Icons.Default.Close, contentDescription = "Close sessions")
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                close()
                actions.onNewChat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New chat")
        }

        Spacer(Modifier.height(22.dp))
        Text("Sessions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        when {
            state.isLoadingSessions -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }

            state.sessions.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    "No sessions yet. Start a new chat to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == state.activeSessionId,
                        onSelect = {
                            close()
                            actions.onSelectSession(session.id)
                        },
                        onRename = { title -> actions.onRenameSession(session.id, title) },
                        onDelete = { actions.onDeleteSession(session.id) },
                    )
                }
            }
        }

        HorizontalDivider(color = DividerDefaults.color)
        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text("Workspace files") },
            selected = false,
            onClick = {
                close()
                actions.onOpenWorkspaceFiles()
            },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = {
                close()
                actions.onOpenSettings()
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        )
    }
}

@Composable
private fun SessionRow(
    session: dev.androidagent.core.ChatSession,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by rememberSaveable(session.id) { mutableStateOf(false) }
    var deleteOpen by rememberSaveable(session.id) { mutableStateOf(false) }
    var renameText by rememberSaveable(session.id) { mutableStateOf(session.title) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title.ifBlank { "Untitled chat" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    formatSessionTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "Session actions" },
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            renameText = session.title
                            renameOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            deleteOpen = true
                        },
                    )
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Session name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty(),
                    onClick = {
                        renameOpen = false
                        onRename(renameText.trim())
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete session?") },
            text = { Text("This removes the session from the list. Workspace files are kept by the root app policy.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AgentChatContent(
    state: AgentUiState,
    actions: AgentUiActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lastMessage = state.messages.lastOrNull()

    LaunchedEffect(
        state.activeSessionId,
        lastMessage?.id,
        lastMessage?.text,
        state.toolCards.size,
        state.statusCards.size,
        state.runState.phase,
    ) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
    ) {
        if (state.errorMessage != null) {
            item(key = "error") {
                ErrorBanner(
                    message = state.errorMessage,
                    onRetry = actions.onRetry,
                    onDismiss = actions.onDismissError,
                )
            }
        }
        if (state.infoMessage != null) {
            item(key = "info") { InfoBanner(state.infoMessage) }
        }

        if (state.statusCards.isNotEmpty()) {
            items(state.statusCards, key = { "status-${it.id}" }) { card -> StatusCard(card) }
        }
        if (state.toolCards.isNotEmpty()) {
            items(state.toolCards, key = { "tool-${it.id}" }) { card -> ToolCard(card) }
        }
        if (state.runState.active) {
            item(key = "run-status") { RunCard(state.runState) }
        }
        state.runState.approval?.let { approval ->
            item(key = "approval-${approval.requestId}") {
                ApprovalCard(approval = approval, onApproval = actions.onApproval)
            }
        }

        if (state.isLoadingMessages) {
            item(key = "loading-messages") {
                LoadingMessagesCard()
            }
        } else if (state.messages.isEmpty()) {
            item(key = "empty-chat") {
                EmptyChatCard(hasSession = state.activeSessionId != null)
            }
        } else {
            items(state.messages, key = { it.id }) { message -> MessageBubble(message) }
        }

        if (state.workspaceFiles.isNotEmpty()) {
            item(key = "workspace-files") {
                WorkspaceFilesCard(
                    files = state.workspaceFiles,
                    onOpenFiles = actions.onOpenWorkspaceFiles,
                    onOpenFile = actions.onOpenWorkspaceFile,
                )
            }
        }
    }
}

@Composable
private fun EmptyChatCard(hasSession: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = if (hasSession) "No messages yet" else "Choose a session to begin",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasSession) {
                    "Describe the work you want the local agent to do."
                } else {
                    "Open the sessions drawer and create a chat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingMessagesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Loading messages…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val role = message.role.lowercase()
    val user = role == "user"
    val system = role == "system" || role == "tool"
    val bubbleColor = when {
        user -> MaterialTheme.colorScheme.primaryContainer
        system -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        user -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = if (user) 330.dp else 380.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (user) 20.dp else 5.dp,
                bottomEnd = if (user) 5.dp else 20.dp,
            ),
            color = bubbleColor,
            tonalElevation = if (user) 0.dp else 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!user && message.state != "complete") {
                    Text(
                        text = when (message.state.lowercase()) {
                            "streaming", "running" -> "Streaming"
                            "error", "failed" -> "Failed"
                            else -> message.state
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.state.lowercase() == "error" || message.state.lowercase() == "failed") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
                } else if (message.state.equals("streaming", ignoreCase = true)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("No text returned.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.attachmentPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(message.attachmentPaths, key = { it }) { path ->
                            AssistChip(
                                onClick = {},
                                label = { Text(path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(runState: dev.androidagent.core.RunState) {
    val controlling = runState.controlling || runState.phase == RunPhase.CONTROLLING
    val color = if (controlling) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (controlling) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (runState.phase == RunPhase.STOPPING) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (controlling) "Controlling device" else readableRunPhase(runState.phase),
                    fontWeight = FontWeight.SemiBold,
                    color = if (controlling) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    runState.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (controlling) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: EngineEvent.Approval,
    onApproval: (String, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Approval needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(approval.method, style = MaterialTheme.typography.bodyMedium)
            if (approval.details.isNotEmpty()) {
                Text(
                    approval.detailsText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onApproval(approval.requestId, false) }) { Text("Deny") }
                Button(onClick = { onApproval(approval.requestId, true) }) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun ToolCard(card: ToolStatusCard) {
    val (container, content, icon) = cardColors(card.state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content)
            Column(modifier = Modifier.weight(1f)) {
                Text(card.title, color = content, fontWeight = FontWeight.Medium)
                if (card.detail.isNotBlank()) Text(card.detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.86f))
            }
            Text(card.state.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

@Composable
private fun StatusCard(card: AgentStatusCard) {
    val (container, content, icon) = cardColors(card.state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content)
            Column {
                Text(card.title, color = content, fontWeight = FontWeight.Medium)
                if (card.detail.isNotBlank()) Text(card.detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.86f))
            }
        }
    }
}

@Composable
private fun cardColors(state: AgentCardState): Triple<Color, Color, androidx.compose.ui.graphics.vector.ImageVector> = when (state) {
    AgentCardState.ACTIVE -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Default.PlayArrow)
    AgentCardState.COMPLETE -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Default.Check)
    AgentCardState.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Default.ErrorOutline)
    AgentCardState.BLOCKED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
}

@Composable
private fun WorkspaceFilesCard(
    files: List<WorkspaceFileItem>,
    onOpenFiles: () -> Unit,
    onOpenFile: (WorkspaceFileItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Workspace files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenFiles) { Text("Open") }
            }
            files.take(5).forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenFile(file) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        file.path,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    file.sizeBytes?.let {
                        Text(formatBytes(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (files.size > 5) {
                Text("${files.size - 5} more files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Retry") }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentComposer(
    state: AgentUiState,
    actions: AgentUiActions,
) {
    var draft by rememberSaveable(state.activeSessionId) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val active = state.runState.active
    val canSend = state.activeSessionId != null && draft.trim().isNotEmpty() && !state.isLoadingMessages

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(state.attachments, key = { it.id }) { attachment ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    buildString {
                                        append(attachment.name)
                                        attachment.sizeBytes?.let { append(" · ").append(formatBytes(it)) }
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { actions.onRemoveAttachment(attachment.id) },
                                    modifier = Modifier.size(28.dp),
                                ) { Icon(Icons.Default.Close, contentDescription = "Remove ${attachment.name}", modifier = Modifier.size(16.dp)) }
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = actions.onAttach,
                    enabled = !active && state.activeSessionId != null,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Attach file" },
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                }
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp, max = 140.dp),
                    enabled = state.activeSessionId != null,
                    placeholder = {
                        Text(if (active) "Steer the active run…" else "Message Android Agent")
                    },
                    maxLines = 6,
                    minLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                if (active) {
                    FilledTonalButton(
                        onClick = {
                            keyboard?.hide()
                            actions.onStop()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Stop")
                    }
                }
                IconButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isBlank()) return@IconButton
                        draft = ""
                        if (active) actions.onSteer(text) else actions.onSend(text, state.attachments)
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = if (active) "Steer agent" else "Send message" },
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.activeSessionId == null) {
                Text("Create or select a session to send a message.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (active) {
                Text("The run is active. New text will steer it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSettingsSheet(state: AgentUiState, actions: AgentUiActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    var pairCode by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var modelsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.adbStatus.phase) {
        if (state.adbStatus.phase == ConnectionPhase.CONNECTING || state.adbStatus.phase == ConnectionPhase.CONNECTED) {
            // Pairing codes are short lived. Keep them only while the form is in use.
            pairCode = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = actions.onCloseSettings,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onCloseSettings) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close settings")
                }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            SettingsSection(title = "Runtime", icon = Icons.Default.Memory) {
                StatusLine(
                    title = readableRuntimePhase(state.runtimeStatus.phase),
                    detail = state.runtimeStatus.message,
                    color = statusColor(state.runtimeStatus.phase),
                )
                state.runtimeStatus.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = actions.onPrepareRuntime,
                    enabled = !state.isPreparingRuntime,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isPreparingRuntime) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Preparing…")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.runtimeStatus.phase == RuntimePhase.READY) "Runtime ready" else "Prepare runtime")
                    }
                }
            }

            SettingsSection(title = "Codex account", icon = Icons.Default.Link) {
                val account = state.accountStatus
                StatusLine(
                    title = account?.label ?: "Account status unavailable",
                    detail = when {
                        account == null -> "Connect the engine to read account status."
                        account.signedIn -> "Signed in"
                        else -> "Sign in to use Codex."
                    },
                    color = if (account?.signedIn == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account?.loginUrl?.let {
                    Text("Browser login is waiting for completion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { uriHandler.openUri(it) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open official login")
                    }
                }
                account?.userCode?.takeIf { it.isNotBlank() }?.let { code ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("One-time code", style = MaterialTheme.typography.labelLarge)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            SelectionContainer {
                                Text(
                                    code,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                )
                            }
                        }
                        Text("Select and copy this code in the browser if asked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (account?.signedIn == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = actions.onLogout, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Log out")
                        }
                        OutlinedButton(
                            onClick = actions.onRefreshAccount,
                            enabled = !state.isRefreshingAccount,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.isRefreshingAccount) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = actions.onLogin, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Log in")
                        }
                        OutlinedButton(
                            onClick = actions.onRefreshAccount,
                            enabled = !state.isRefreshingAccount,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.isRefreshingAccount) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }
            }

            SettingsSection(title = "Wireless ADB", icon = Icons.Default.Wifi) {
                StatusLine(
                    title = readableConnectionPhase(state.adbStatus.phase),
                    detail = buildString {
                        append(state.adbStatus.message)
                        state.adbStatus.port?.let { append(" · port ").append(it) }
                    },
                    color = statusColor(state.adbStatus.phase),
                )
                if (state.adbStatus.phase == ConnectionPhase.ERROR) {
                    Text(
                        "Check the current pairing port and connect port in Wireless debugging, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = actions.onOpenWirelessSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open wireless debugging settings")
                }
                OutlinedTextField(
                    value = pairCode,
                    onValueChange = { pairCode = it.filter(Char::isDigit).take(12) },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairPort,
                    onValueChange = { pairPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Pairing port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = connectPort,
                    onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Connect port") },
                    supportingText = { Text("Use the connect port shown by Wireless debugging, not the pairing port.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = actions.onDiscover,
                        enabled = !state.isDiscoveringAdb,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isDiscoveringAdb) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Discover")
                    }
                    Button(
                        onClick = { actions.onPair(pairCode.trim(), pairPort.trim()) },
                        enabled = pairCode.trim().isNotEmpty() && pairPort.toIntOrNull() != null && !state.isPairing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isPairing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pair")
                    }
                }
                Button(
                    onClick = { actions.onConnect(connectPort.trim()) },
                    enabled = connectPort.toIntOrNull() != null && !state.isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isConnecting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = actions.onDisconnect,
                        enabled = state.adbStatus.phase == ConnectionPhase.CONNECTED,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Disconnect")
                    }
                    TextButton(
                        onClick = actions.onForgetPairing,
                        enabled = state.adbStatus.phase != ConnectionPhase.PAIRING &&
                            state.adbStatus.phase != ConnectionPhase.CONNECTING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Forget pairing")
                    }
                }
                if (state.discoveredEndpoints.isEmpty()) {
                    Text("No endpoints found yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Discovered endpoints", style = MaterialTheme.typography.labelLarge)
                    state.discoveredEndpoints.forEach { endpoint ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (endpoint.pairing) Icons.Default.Link else Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${endpoint.host}:${endpoint.port} · ${if (endpoint.pairing) "pairing" else "connect"}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = {
                                if (endpoint.pairing) pairPort = endpoint.port.toString() else connectPort = endpoint.port.toString()
                            }) { Text("Use") }
                        }
                    }
                }
            }

            SettingsSection(title = "Visible control", icon = Icons.Default.Terminal) {
                Text(
                    "Device taps and screenshots need a visible floating control. Grant overlay access before starting a run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = actions.onOpenOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open overlay permission")
                }
            }

            SettingsSection(title = "Model", icon = Icons.Default.Tune) {
                if (state.isLoadingModels) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading models…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (state.availableModels.isEmpty()) {
                    Text("Models are unavailable until the engine connects.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box {
                        OutlinedButton(onClick = { modelsExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state.selectedModel ?: "Choose a model", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            Icon(Icons.Default.ExpandMore, contentDescription = "Choose model")
                        }
                        DropdownMenu(expanded = modelsExpanded, onDismissRequest = { modelsExpanded = false }) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    trailingIcon = if (model == state.selectedModel) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                                    onClick = {
                                        modelsExpanded = false
                                        actions.onModelSelected(model)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(title = "Workspace", icon = Icons.Default.FolderOpen) {
                if (state.isLoadingWorkspace) {
                    Text("Loading workspace files…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (state.workspaceError != null) {
                    Text(state.workspaceError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        if (state.workspaceFiles.isEmpty()) "No workspace files loaded." else "${state.workspaceFiles.size} files loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = actions.onOpenWorkspaceFiles, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open workspace files")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                content()
            },
        )
    }
}

@Composable
private fun StatusLine(title: String, detail: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(color))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun readableRunPhase(phase: RunPhase): String = when (phase) {
    RunPhase.IDLE -> "Ready"
    RunPhase.STARTING -> "Starting"
    RunPhase.THINKING -> "Thinking"
    RunPhase.TOOL -> "Running a tool"
    RunPhase.CONTROLLING -> "Controlling device"
    RunPhase.STOPPING -> "Stopping"
    RunPhase.ERROR -> "Run error"
}

private fun readableRuntimePhase(phase: RuntimePhase): String = when (phase) {
    RuntimePhase.MISSING -> "Runtime not ready"
    RuntimePhase.PREPARING -> "Preparing runtime"
    RuntimePhase.READY -> "Runtime ready"
    RuntimePhase.RUNNING -> "Runtime running"
    RuntimePhase.ERROR -> "Runtime error"
}

private fun readableConnectionPhase(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.DISCONNECTED -> "ADB disconnected"
    ConnectionPhase.DISCOVERING -> "Discovering ADB"
    ConnectionPhase.PAIRING -> "Pairing ADB"
    ConnectionPhase.CONNECTING -> "Connecting ADB"
    ConnectionPhase.CONNECTED -> "ADB connected"
    ConnectionPhase.ERROR -> "ADB error"
}

@Composable
private fun statusColor(value: Any): Color = when (value) {
    RuntimePhase.READY, RuntimePhase.RUNNING, ConnectionPhase.CONNECTED -> MaterialTheme.colorScheme.secondary
    RuntimePhase.ERROR, ConnectionPhase.ERROR -> MaterialTheme.colorScheme.error
    RuntimePhase.PREPARING, ConnectionPhase.DISCOVERING, ConnectionPhase.PAIRING, ConnectionPhase.CONNECTING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "${bytes / (1024L * 1024L * 1024L)} GB"
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return "No activity time"
    return try {
        val formatter = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        formatter.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        "Updated"
    }
}
