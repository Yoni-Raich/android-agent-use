package dev.androidagent.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.androidagent.core.RunState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as AgentApplication).graph
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Agent activity", NotificationManager.IMPORTANCE_LOW))
        startForeground(101, notification(RunState()))
        graph.adb.startAutoReconnect(scope)
        scope.launch { graph.coordinator.state.collectLatest { getSystemService(NotificationManager::class.java).notify(101, notification(it)) } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) graph.coordinator.stop()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) { graph.coordinator.stop(); stopSelf() }
    override fun onDestroy() {
        graph.coordinator.stop()
        graph.adb.stopAutoReconnect()
        graph.scope.launch { runCatching { graph.engine.close() }; runCatching { graph.adb.disconnect() } }
        graph.overlay.hide()
        scope.cancel()
        super.onDestroy()
    }
    private fun notification(state: RunState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AgentService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(if (state.active) "Android Agent is working" else "Android Agent")
            .setContentText(if (state.active) state.status else "Local runtime ready")
            .setContentIntent(open).setOngoing(state.active).setSilent(true)
            .addAction(0, "Stop", stop).build()
    }
    companion object { const val CHANNEL = "agent_activity"; const val ACTION_STOP = "dev.androidagent.STOP" }
}
