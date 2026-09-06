package dev.androidagent.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.androidagent.app.ui.*

class MainActivity : ComponentActivity() {
    private val model: AgentViewModel by viewModels()
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::addAttachment) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            AndroidAgentScreen(state, actions())
        }
        ensureService()
        model.prepare()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    override fun onStart() {
        super.onStart()
        // The app owns the foreground surface. Keep the run state in the
        // overlay, but remove its window until another app is visible.
        model.graph.overlay.setAppForeground(true)
    }
    override fun onStop() {
        model.graph.overlay.setAppForeground(false)
        super.onStop()
    }
    override fun onResume() { super.onResume(); model.refreshAccount() }
    private fun ensureService() { runCatching { ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java)) }.onFailure { model.error("Could not start the agent service: ${it.message}") } }
    private fun actions() = AgentUiActions(
        onDrawerChanged = { open -> model.editUi { it.copy(isDrawerOpen = open) } },
        onNewChat = { model.newChat() },
        onSelectSession = model::select,
        onAttach = { filePicker.launch(arrayOf("*/*")) },
        onRemoveAttachment = model::removeAttachment,
        onSend = { text, attachments -> ensureService(); model.send(text, attachments) },
        onSteer = { model.graph.coordinator.steer(it) },
        onStop = model::stop,
        onOpenSettings = { model.editUi { it.copy(isSettingsOpen = true) } },
        onCloseSettings = { model.editUi { it.copy(isSettingsOpen = false) } },
        onPrepareRuntime = { ensureService(); model.prepare() },
        onLogin = { ensureService(); model.login() },
        onLogout = { model.logout() },
        onRefreshAccount = { model.refreshAccount() },
        onOpenWirelessSettings = { startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")) },
        onOpenOverlayPermission = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
        onDisconnect = { model.disconnect() },
        onForgetPairing = { model.forgetPairing() },
        onPair = { code, port -> model.pair(code, port) },
        onConnect = { model.connect(it) },
        onDiscover = { model.discover() },
        onModelSelected = model::model,
        onReasoningEffortSelected = model::reasoningEffort,
        onRenameSession = { id, title -> model.rename(id, title) },
        onDeleteSession = { model.delete(it) },
        onRetry = { model.prepare() },
        onDismissError = { model.editUi { it.copy(errorMessage = null) } },
        onOpenWorkspaceFiles = { model.listFiles() },
        onOpenWorkspaceFile = { item -> openFile(item) },
        onApproval = { _, allow -> model.graph.coordinator.approve(allow) },
    )
    private fun openFile(item: WorkspaceFileItem) {
        runCatching {
            check(!item.isDirectory) { "Choose a file inside this folder." }
            val file = model.resolveWorkspaceFile(item)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open file"))
        }.onFailure { model.error(it.message ?: "No app can open this file.") }
    }
}
