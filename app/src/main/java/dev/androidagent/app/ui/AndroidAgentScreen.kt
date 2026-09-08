package dev.androidagent.app.ui

import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.DataUsage

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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import dev.androidagent.app.update.AppUpdateInfo
import dev.androidagent.app.update.UpdateStatus
import dev.androidagent.core.RunPhase
import dev.androidagent.core.RuntimePhase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val AgentDarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF252525),
    onPrimaryContainer = Color(0xFFF4F4F4),
    secondary = Color(0xFF83D9CA),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF174E47),
    onSecondaryContainer = Color(0xFFA1F2E1),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF202020),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFAAAAAA),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF2B1D1B),
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
    onSurfaceVariant = Color(0xFF303030),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun AndroidAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgentDarkColors,
        typography = androidx.compose.material3.Typography().copy(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 27.sp, textDirection = TextDirection.Content),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp, textDirection = TextDirection.Content),
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
                modifier = Modifier.fillMaxSize().imePadding(),
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AgentTopBar(
                        state = state,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSettings = actions.onOpenSettings,
                        onOpenWirelessSettings = actions.onOpenWirelessSettings,
                        onOpenFiles = actions.onOpenWorkspaceFiles,
                        onNewChat = actions.onNewChat,
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
    onOpenWirelessSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewChat: () -> Unit,
) {
    var confirmNew by remember { mutableStateOf(false) }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false },
        title = { Text("Start a new chat?") },
        text = { Text("The current task will keep running. New tasks will wait in the queue.") },
        confirmButton = { TextButton(onClick = { confirmNew = false; onNewChat() }) { Text("New chat") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } })
    TopAppBar(
        title = {
            Column {
                Text(
                    text = state.activeSessionTitle?.takeIf { it.isNotBlank() } ?: "Android Agent",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AdbStatusPill(
                        status = state.adbStatus,
                        onClick = onOpenWirelessSettings,
                    )
                    if (state.runState.active) RunStatusPill(runState = state.runState)
                }
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
            IconButton(onClick = { if (state.runState.active) confirmNew = true else onNewChat() }) {
                Icon(Icons.Default.EditNote, contentDescription = "New chat")
            }
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
private fun AdbStatusPill(
    status: dev.androidagent.core.AdbStatus,
    onClick: () -> Unit,
) {
    val color = when (status.phase) {
        ConnectionPhase.CONNECTED -> Color(0xFF4ADE80)
        ConnectionPhase.DISCOVERING,
        ConnectionPhase.PAIRING,
        ConnectionPhase.CONNECTING -> Color(0xFFFBBF24)
        ConnectionPhase.ERROR -> MaterialTheme.colorScheme.error
        ConnectionPhase.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (status.phase) {
        ConnectionPhase.CONNECTED -> status.port?.let { "ADB · $it" } ?: "ADB · connected"
        ConnectionPhase.DISCOVERING -> "ADB · searching"
        ConnectionPhase.PAIRING -> "ADB · pairing"
        ConnectionPhase.CONNECTING -> "ADB · reconnecting"
        ConnectionPhase.ERROR -> "ADB · error"
        ConnectionPhase.DISCONNECTED -> "ADB · disconnected"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$label. ${status.message}. Open Wireless Debugging settings"
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
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
            text = if (runState.controlling) "Controlling device" else readableRunPhase(runState.phase),
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
    var followLatest by remember(state.activeSessionId) { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState, state.activeSessionId) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollForward, automaticScroll) }
            .collect { (scrolling, hasMore, automatic) ->
                if (scrolling && !automatic) followLatest = !hasMore
            }
    }

    LaunchedEffect(
        state.activeSessionId,
        lastMessage?.id,
        lastMessage?.text,
        state.toolCards.size,
        state.statusCards.size,
        state.runState.phase,
    ) {
        if (followLatest && listState.layoutInfo.totalItemsCount > 0) {
            automaticScroll = true
            try {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            } finally {
                automaticScroll = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
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
        val updateInfo = state.updateInfo
        if (updateInfo?.isUpdateAvailable == true && state.isUpdateBannerVisible) {
            item(key = "update-banner") {
                UpdateBanner(
                    info = updateInfo,
                    status = state.updateStatus,
                    onDownload = actions.onDownloadUpdate,
                    onInstall = actions.onInstallUpdate,
                    onDismiss = actions.onDismissUpdateBanner,
                )
            }
        }

        if (state.statusCards.isNotEmpty()) {
            items(state.statusCards, key = { "status-${it.id}" }) { card -> StatusCard(card) }
        }
        if (state.toolCards.isNotEmpty()) {
            items(state.toolCards, key = { "tool-${it.id}" }) { card -> ToolCard(card) }
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What can I help with?", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(if (hasSession) "Ask, plan, or do something on your phone." else "Create a chat to get started.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (system) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityDetail(if (role == "tool") "Device activity" else "Run details", dev.androidagent.core.SecretRedactor.redact(message.text))
            InlineImages(message.attachmentPaths)
        }
        return
    }
    val bubbleColor = when {
        user -> MaterialTheme.colorScheme.primaryContainer
        system -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        else -> Color.Transparent
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
            modifier = if (user) Modifier.fillMaxWidth(0.88f).widthIn(max = 520.dp) else Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp,
            ),
            color = bubbleColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = if (user || system) 16.dp else 0.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!user && message.state.lowercase() in setOf("error", "failed")) {
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
                    if (user) {
                        SelectionContainer { Text(message.text, modifier = Modifier.fillMaxWidth(), color = textColor, style = MaterialTheme.typography.bodyLarge) }
                    } else {
                        MarkdownMessage(message.text, textColor)
                        val clipboard = LocalClipboardManager.current
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(message.text)) },
                                modifier = Modifier.size(32.dp).semantics { contentDescription = "Copy message" },
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy message", modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (message.state.equals("streaming", ignoreCase = true)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Text("Working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("No text returned.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                InlineImages(message.attachmentPaths)
                if (message.attachmentPaths.any { !isImagePath(it) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(message.attachmentPaths.filterNot(::isImagePath), key = { it }) { path ->
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (runState.controlling) "Controlling device" else readableRunPhase(runState.phase),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
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
private fun ActivityDetail(title: String, detail: String, failed: Boolean = false) {
    var expanded by rememberSaveable(title, detail) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.Terminal, null,
                Modifier.size(18.dp), tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotBlank()) Icon(Icons.Default.ExpandMore,
                if (expanded) "Hide activity details" else "Show activity details", Modifier.size(18.dp))
        }
        if (expanded && detail.isNotBlank()) SelectionContainer {
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun ToolCard(card: ToolStatusCard) {
    ActivityDetail(card.title, card.detail, card.state == AgentCardState.ERROR)
}

@Composable
private fun StatusCard(card: AgentStatusCard) {
    ActivityDetail(card.title, card.detail, card.state == AgentCardState.ERROR)
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
    var expanded by rememberSaveable(message) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Something went wrong", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss error") }
            }
            // Technical details stay available without occupying the conversation.
            if (expanded) SelectionContainer {
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("Retry setup") }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide details" else "Show details")
                }
            }
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
private fun UpdateBanner(
    info: AppUpdateInfo,
    status: UpdateStatus,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Update available: v${info.latestVersionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss update", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                }
            }
            if (info.releaseNotes.isNotBlank()) {
                Text(
                    info.releaseNotes.take(150) + if (info.releaseNotes.length > 150) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (status) {
                is UpdateStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Downloading: ${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                is UpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install update")
                    }
                }
                else -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val sizeStr = if (info.apkSize > 0) " (${info.apkSize / (1024 * 1024)} MB)" else ""
                        Text("Update now$sizeStr")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentComposer(state: AgentUiState, actions: AgentUiActions) {
    var draft by rememberSaveable(state.activeSessionId) { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    var reasoningMenu by remember { mutableStateOf(false) }
    val voiceActive = state.voiceState.active
    val active = state.runState.active && state.runState.sessionId == state.activeSessionId && !voiceActive
    val stopping = state.runState.phase == RunPhase.STOPPING && !voiceActive
    val voiceStopping = state.voiceState.phase == dev.androidagent.core.VoicePhase.STOPPING
    val voiceBusy = state.voiceState.phase in setOf(
        dev.androidagent.core.VoicePhase.STARTING,
        dev.androidagent.core.VoicePhase.STOPPING,
    )
    val canSend = state.activeSessionId != null && draft.isNotBlank() && !state.isLoadingMessages && !stopping && !voiceStopping
    val selectedModel = state.modelCatalog.firstOrNull { it.id == state.selectedModel }
    val reasoningOptions = selectedModel?.reasoningEfforts.orEmpty()
    val showSkillSuggestions = draft.startsWith("\$") && !draft.contains(" ") && !draft.contains("\n")
    val skillQuery = if (showSkillSuggestions) draft.removePrefix("\$").trim() else ""
    val matchingSkills = remember(showSkillSuggestions, skillQuery, state.availableSkills) {
        if (!showSkillSuggestions) {
            emptyList()
        } else if (skillQuery.isEmpty()) {
            state.availableSkills
        } else {
            state.availableSkills.filter {
                it.name.contains(skillQuery, ignoreCase = true) ||
                    it.description.contains(skillQuery, ignoreCase = true)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
        .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (state.runState.active) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RunCard(state.runState) }
                if (!active && !voiceActive) TextButton(onClick = actions.onStop) { Text("Stop active task") }
            }
        }
        if (state.queuedTurns.isNotEmpty()) {
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
        if (voiceActive) {
            Button(onClick = actions.onStop, enabled = !voiceStopping, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop voice")
            }
        }
        if (matchingSkills.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF383838)),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Codex Skills",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${matchingSkills.size} available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        items(matchingSkills, key = { it.path }) { skill ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        draft = "\$${skill.name} "
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF282828),
                                    border = BorderStroke(0.5.dp, Color(0xFF444444)),
                                ) {
                                    Text(
                                        text = "\$${skill.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF83D9CA),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = skill.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = skill.description,
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
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF383838))) {
            Column(Modifier.padding(6.dp)) {
                if (state.attachments.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.attachments, key = { it.id }) { attachment ->
                        AssistChip(onClick = { actions.onRemoveAttachment(attachment.id) },
                            label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove ${attachment.name}", Modifier.size(16.dp)) })
                    }
                }
                TextField(value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp, max = 160.dp)
                        .semantics { contentDescription = "Message input" },
                    enabled = state.activeSessionId != null,
                    placeholder = { Text(if (active) "Add an instruction…" else "Message Android Agent") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 6, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onAttach, enabled = !active && !voiceActive && state.activeSessionId != null) {
                        Icon(Icons.Default.Add, "Attach file")
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { if (state.availableModels.isEmpty()) actions.onOpenSettings() else modelMenu = true },
                            enabled = !active && !voiceActive, modifier = Modifier.widthIn(max = 190.dp)) {
                            Text(state.selectedModel?.removePrefix("gpt-") ?: "Choose model",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(text = { Text(model) }, onClick = {
                                    actions.onModelSelected(model); modelMenu = false
                                })
                            }
                        }
                    }
                    Box {
                        TextButton(
                            onClick = { reasoningMenu = true },
                            enabled = !active && !voiceActive && reasoningOptions.isNotEmpty(),
                            modifier = Modifier
                                .widthIn(max = 110.dp)
                                .semantics { contentDescription = "Choose reasoning effort" },
                        ) {
                            Text(
                                state.selectedReasoningEffort ?: "Default",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = reasoningMenu, onDismissRequest = { reasoningMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Default") },
                                trailingIcon = if (state.selectedReasoningEffort == null) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                                onClick = {
                                    reasoningMenu = false
                                    actions.onReasoningEffortSelected(null)
                                },
                            )
                            reasoningOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.value)
                                            if (option.description.isNotBlank()) {
                                                Text(option.description, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    },
                                    trailingIcon = if (option.value == state.selectedReasoningEffort) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                                    onClick = {
                                        reasoningMenu = false
                                        actions.onReasoningEffortSelected(option.value)
                                    },
                                )
                            }
                        }
                    }
                    // Stop is always reachable, including while a steering draft is typed.
                    if (active) IconButton(onClick = actions.onStop, enabled = !stopping,
                        modifier = Modifier.size(48.dp).padding(3.dp).background(Color.White, CircleShape)) {
                        Icon(Icons.Default.Stop, "Stop agent", tint = Color.Black)
                    }
                    if (!active || draft.isNotBlank()) IconButton(onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            if (active) actions.onSteer(text) else actions.onSend(text, state.attachments)
                            draft = ""
                        }
                    }, enabled = canSend, modifier = Modifier.size(48.dp).padding(3.dp)
                        .background(if (canSend) Color.White else Color(0xFF444444), CircleShape)) {
                        Icon(Icons.Default.ArrowUpward, if (active) "Steer agent" else "Send message",
                            tint = if (canSend) Color.Black else Color(0xFF999999))
                    }
                    if (!state.runState.active || voiceActive) IconButton(
                        onClick = actions.onVoiceToggle,
                        enabled = state.activeSessionId != null && !state.isLoadingMessages && !voiceStopping,
                        modifier = Modifier.size(48.dp).padding(3.dp)
                            .background(Color(0xFF2F80ED), CircleShape)
                            .semantics {
                                contentDescription = if (voiceActive) "End voice conversation" else "Start voice conversation"
                            },
                    ) {
                        if (voiceBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.GraphicEq, null, tint = Color.White)
                        }
                    }
                }
            }
        }
        when {
            voiceActive || voiceBusy -> {
                val transcript = state.voiceTranscript.trim()
                Text(
                    if (transcript.isNotEmpty()) {
                        val speaker = if (state.voiceTranscriptRole.equals("assistant", ignoreCase = true)) "Codex" else "You"
                        "$speaker · $transcript"
                    } else "Voice · ${state.voiceState.message}",
                    Modifier.padding(start = 12.dp, top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.voiceState.phase == dev.androidagent.core.VoicePhase.SPEAKING) Color(0xFF69A7FF)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            active -> Text(if (stopping) "Stopping… your draft is kept" else "Working · you can add instructions or stop", Modifier.padding(start = 12.dp, top = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            SettingsSection(title = "Usage", icon = Icons.Default.DataUsage) {
                val usage = state.tokenUsage
                Text(if (usage == null) "Token usage is not available yet." else
                    "${usage.total} tokens · ${usage.input} input · ${usage.output} output · ${usage.cachedInput} cached")
                if (state.usageLimits.isEmpty()) Text("Account quota is not available for this account yet.")
                state.usageLimits.forEach { limit ->
                    val remaining = limit.usedPercent?.let { "${(100 - it).toInt()}% remaining" } ?: "Unavailable"
                    val reset = limit.resetsAt?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it * 1000)) }
                    Text("${limit.name}: $remaining${if (reset != null) " · resets $reset" else ""}")
                }
                TextButton(onClick = actions.onRefreshAccount) { Text("Refresh usage") }
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

            SettingsSection(title = "Accessibility control", icon = Icons.Default.Settings) {
                StatusLine(
                    title = if (state.a11yStatus.connected) "On" else "Off",
                    detail = state.a11yStatus.message,
                    color = if (state.a11yStatus.connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Lets the agent read the screen and tap without Wireless Debugging. " +
                        "While a task runs it reads on-screen text and sends it to the model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.a11yStatus.blockedByRestrictedSetting) {
                    // Switched on but never connected. On Android 13+ that is
                    // what a sideloaded build looks like before the user allows
                    // restricted settings, and no API reports it directly.
                    Text(
                        "Android is blocking this because the app was installed outside the Play Store. " +
                            "Open App info, tap the three-dot menu, choose \"Allow restricted settings\", " +
                            "then turn it on again in Accessibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = actions.onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open App info")
                    }
                }
                TextButton(onClick = actions.onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open accessibility settings")
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

            SettingsSection(title = "Updates", icon = Icons.Default.Refresh) {
                StatusLine(
                    title = "Version ${dev.androidagent.app.BuildConfig.VERSION_NAME}",
                    detail = when (val s = state.updateStatus) {
                        is UpdateStatus.Checking -> "Checking for updates…"
                        is UpdateStatus.UpToDate -> "App is up to date (${s.currentVersion})"
                        is UpdateStatus.Available -> "New version v${s.info.latestVersionName} available"
                        is UpdateStatus.Downloading -> "Downloading update: ${(s.progress * 100).toInt()}%"
                        is UpdateStatus.ReadyToInstall -> "Update downloaded and ready to install"
                        is UpdateStatus.Error -> "Check failed: ${s.message}"
                        UpdateStatus.Idle -> state.updateInfo?.let {
                            if (it.isUpdateAvailable) "Update v${it.latestVersionName} available" else "Up to date"
                        } ?: "Check GitHub for new releases"
                    },
                    color = when (state.updateStatus) {
                        is UpdateStatus.Available, is UpdateStatus.ReadyToInstall -> MaterialTheme.colorScheme.secondary
                        is UpdateStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (state.updateStatus is UpdateStatus.Downloading) {
                    val dl = state.updateStatus as UpdateStatus.Downloading
                    LinearProgressIndicator(
                        progress = { dl.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.updateStatus is UpdateStatus.ReadyToInstall) {
                    Button(
                        onClick = actions.onInstallUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install update")
                    }
                } else if (state.updateStatus is UpdateStatus.Available || (state.updateInfo?.isUpdateAvailable == true && state.updateStatus !is UpdateStatus.Downloading)) {
                    Button(
                        onClick = actions.onDownloadUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Download update")
                    }
                }
                OutlinedButton(
                    onClick = actions.onCheckForUpdates,
                    enabled = state.updateStatus !is UpdateStatus.Checking && state.updateStatus !is UpdateStatus.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.updateStatus is UpdateStatus.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking…")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check for updates")
                    }
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
    RunPhase.THINKING -> "Working"
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
