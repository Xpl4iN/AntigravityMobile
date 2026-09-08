package de.xyourp.antigravitymobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversation = intent.getStringExtra(CONVERSATION) ?: return
        val approval = intent.getStringExtra(APPROVAL) ?: return
        val approved = intent.getBooleanExtra(APPROVED, false)
        val pending = goAsync(); val repo = (context.applicationContext as AntigravityApp).repository
        CoroutineScope(Dispatchers.IO).launch { try { runCatching { repo.api.command(if (approved) "approve" else "reject", conversation, approvalId = approval) } } finally { pending.finish() } }
    }
    companion object {
        private const val CONVERSATION = "conversation"; private const val APPROVAL = "approval"; private const val APPROVED = "approved"
        fun pendingIntent(context: Context, approved: Boolean, conversation: String, approval: String): PendingIntent {
            val intent = Intent(context, AgentActionReceiver::class.java).putExtra(CONVERSATION, conversation).putExtra(APPROVAL, approval).putExtra(APPROVED, approved)
            return PendingIntent.getBroadcast(context, (conversation + approval + approved).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
