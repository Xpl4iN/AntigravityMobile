package de.xyourp.antigravitymobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.xyourp.antigravitymobile.net.AgentEvent
import de.xyourp.antigravitymobile.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.decodeFromJsonElement

object AgentMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false
    fun start(context: Context) {
        if (started) return; started = true
        val app = context.applicationContext
        NotificationManagerCompat.from(app).createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("OAC agent alerts").setDescription("Approvals, completed turns, and errors").build())
        val repo = (app as AntigravityApp).repository
        repo.socket.frames.onEach { frame -> if (frame.kind == "turn") runCatching { ApiClient.json.decodeFromJsonElement<AgentEvent>(frame.payload) }.getOrNull()?.let { event ->
            when (event.type) {
                "approval-request" -> notify(app, "OAC needs approval", event.reason ?: event.path.orEmpty(), frame.conversationId, event.id)
                "turn-complete" -> notify(app, "Agent finished", "The active OAC turn completed", frame.conversationId, null)
                "error" -> notify(app, "Agent error", event.message.orEmpty(), frame.conversationId, null)
            }
        } }.launchIn(scope)
    }
    private fun notify(context: Context, title: String, text: String, conversationId: String?, approvalId: String?) {
        val open = PendingIntent.getActivity(context, title.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_agent).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open)
        if (approvalId != null && conversationId != null) {
            builder.addAction(0, "Approve", AgentActionReceiver.pendingIntent(context, true, conversationId, approvalId))
            builder.addAction(0, "Reject", AgentActionReceiver.pendingIntent(context, false, conversationId, approvalId))
        }
        runCatching { NotificationManagerCompat.from(context).notify((conversationId + approvalId + title).hashCode(), builder.build()) }
    }
    private const val CHANNEL = "oac_agent_alerts"
}
