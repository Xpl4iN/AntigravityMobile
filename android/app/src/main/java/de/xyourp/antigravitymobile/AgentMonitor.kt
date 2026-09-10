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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

/** Just the fields notifications need, decoded from each snapshot without the transcripts. */
@Serializable private data class LiteConversation(val id: String, val title: String = "", val masterId: String? = null)
@Serializable private data class LiteSnapshot(val conversations: List<LiteConversation> = emptyList())

object AgentMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false
    /** Latest known title and master per conversation, refreshed from snapshots. */
    @Volatile private var known: Map<String, LiteConversation> = emptyMap()
    fun start(context: Context) {
        if (started) return; started = true
        val app = context.applicationContext
        NotificationManagerCompat.from(app).createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("OAC agent alerts").setDescription("Approvals, completed turns, and errors").build())
        val repo = (app as AntigravityApp).repository
        repo.socket.frames.onEach { frame ->
            if (frame.kind == "snapshot") {
                runCatching { ApiClient.json.decodeFromJsonElement<LiteSnapshot>(frame.payload) }.getOrNull()?.let { snapshot ->
                    known = snapshot.conversations.associateBy { it.id }
                }
            }
            if (frame.kind == "turn") runCatching { ApiClient.json.decodeFromJsonElement<AgentEvent>(frame.payload) }.getOrNull()?.let { event ->
                val chat = frame.conversationId?.let { known[it] }
                val name = chat?.title?.takeIf { it.isNotBlank() } ?: "Agent"
                when (event.type) {
                    "approval-request" -> notify(app, "$name needs approval", event.reason ?: event.path.orEmpty(), frame.conversationId, event.id)
                    // A crew agent's finish goes to its master as a report on the
                    // desktop; one notification per crew agent would bury the master's.
                    "turn-complete" -> if (chat?.masterId == null) notify(app, "$name finished", "The agent completed its turn.", frame.conversationId, null)
                    "error" -> notify(app, "$name stopped with an error", event.message.orEmpty(), frame.conversationId, null)
                }
            }
        }.launchIn(scope)
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
