package de.xyourp.antigravitymobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs a notification button's command on the desktop without opening the
 * app: approve or reject a write, or resume or dismiss a turn that stopped at
 * a quota limit (OAC ADR 0014).
 */
class AgentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversation = intent.getStringExtra(CONVERSATION) ?: return
        val command = intent.getStringExtra(COMMAND)
        val approval = intent.getStringExtra(APPROVAL)
        val approved = intent.getBooleanExtra(APPROVED, false)
        if (command == null && approval == null) return
        val pending = goAsync(); val repo = (context.applicationContext as AntigravityApp).repository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    if (command != null) repo.api.command(command, conversation)
                    else repo.api.command(if (approved) "approve" else "reject", conversation, approvalId = approval)
                }
            } finally { pending.finish() }
        }
    }
    companion object {
        private const val CONVERSATION = "conversation"; private const val APPROVAL = "approval"; private const val APPROVED = "approved"; private const val COMMAND = "command"
        fun pendingIntent(context: Context, approved: Boolean, conversation: String, approval: String): PendingIntent {
            val intent = Intent(context, AgentActionReceiver::class.java).putExtra(CONVERSATION, conversation).putExtra(APPROVAL, approval).putExtra(APPROVED, approved)
            return PendingIntent.getBroadcast(context, (conversation + approval + approved).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        /** A button that sends `command` (such as `resume-pending`) for `conversation`. */
        fun commandIntent(context: Context, command: String, conversation: String): PendingIntent {
            val intent = Intent(context, AgentActionReceiver::class.java).putExtra(CONVERSATION, conversation).putExtra(COMMAND, command)
            return PendingIntent.getBroadcast(context, (conversation + command).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
