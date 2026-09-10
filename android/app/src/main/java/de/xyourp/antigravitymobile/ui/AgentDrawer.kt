package de.xyourp.antigravitymobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.RemoteFileLease
import de.xyourp.antigravitymobile.net.RemoteSnapshot
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel

/**
 * The chat list as an agent list (OAC ADR 0012): a state dot per chat, each
 * master followed by its crew, and the files running agents hold. A desktop
 * older than the parallel-agents bridge sends no agent state, and the list
 * falls back to the busy flag.
 */
@Composable fun AgentDrawerList(snapshot: RemoteSnapshot, activeId: String?, vm: ChatViewModel, onOpen: (String) -> Unit) {
    val rows = remember(snapshot.conversations) { orderAgents(snapshot.conversations) }
    val masters = remember(snapshot.conversations) { snapshot.conversations.filter { it.isMaster } }
    LazyColumn(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows, key = { it.conversation.id }) { row ->
            AgentRow(row, row.conversation.id == activeId, masters, vm) { onOpen(row.conversation.id) }
        }
        if (snapshot.leases.isNotEmpty()) {
            item(key = "leases-header") {
                Column(Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)) {
                    HorizontalDivider()
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Files being edited", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        leaseModeText(snapshot.leaseMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(snapshot.leases, key = { "lease-" + it.path }) { lease -> LeaseRow(lease, vm) }
        }
    }
}

private data class AgentRowModel(val conversation: RemoteConversation, val crew: Boolean)

/** Masters first, each followed by its crew; then every other chat, newest first. */
private fun orderAgents(conversations: List<RemoteConversation>): List<AgentRowModel> {
    val byRecency = conversations.sortedByDescending { it.updatedAt }
    val masters = byRecency.filter { it.isMaster }
    val masterIds = masters.map { it.id }.toSet()
    val rows = mutableListOf<AgentRowModel>()
    for (master in masters) {
        rows += AgentRowModel(master, crew = false)
        byRecency.filter { it.masterId == master.id }.forEach { rows += AgentRowModel(it, crew = true) }
    }
    byRecency.filter { !it.isMaster && it.masterId !in masterIds }.forEach { rows += AgentRowModel(it, crew = false) }
    return rows
}

// Shared with the Agents screen, so both lists name and colour a state the same way.
internal fun stateOf(chat: RemoteConversation): String = chat.agentState ?: if (chat.busy) "running" else "idle"

internal fun stateLabel(state: String): String = when (state) {
    "approval" -> "needs approval"
    "running" -> "running"
    "waiting" -> "waiting to resume"
    "error" -> "error"
    "queued" -> "queue paused"
    "unread" -> "finished"
    else -> ""
}

@Composable internal fun stateColor(state: String): Color = when (state) {
    "approval" -> Color(0xFFE5A00D)
    "running" -> MaterialTheme.colorScheme.primary
    "waiting" -> Color(0xFF0880E8)
    "error" -> MaterialTheme.colorScheme.error
    "queued" -> Color(0xFFA882FF)
    "unread" -> Color(0xFF3FB950)
    else -> MaterialTheme.colorScheme.outlineVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AgentRow(row: AgentRowModel, selected: Boolean, masters: List<RemoteConversation>, vm: ChatViewModel, onClick: () -> Unit) {
    val chat = row.conversation
    val state = stateOf(chat)
    var menu by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        modifier = if (row.crew) Modifier.padding(start = 20.dp) else Modifier,
        selected = selected,
        onClick = onClick,
        icon = {
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(if (chat.isMaster) Icons.Default.WorkspacePremium else Icons.Default.ChatBubbleOutline, null)
                Box(Modifier.size(9.dp).clip(CircleShape).background(stateColor(state)))
            }
        },
        label = {
            Column {
                Text(chat.title.ifBlank { "New chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (chat.isMaster) FontWeight.SemiBold else null)
                val parts = listOfNotNull(chat.providerId, chat.model ?: "default", stateLabel(state).takeIf { it.isNotBlank() })
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (state == "approval" || state == "error") stateColor(state) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        badge = {
            Box {
                IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Agent actions", Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (chat.isMaster) {
                        DropdownMenuItem(text = { Text("Stop being a master agent") }, leadingIcon = { Icon(Icons.Default.WorkspacePremium, null) }, onClick = { menu = false; vm.setMaster(chat.id, false) })
                    } else {
                        DropdownMenuItem(text = { Text("Make master agent") }, leadingIcon = { Icon(Icons.Default.WorkspacePremium, null) }, onClick = { menu = false; vm.setMaster(chat.id, true) })
                        masters.filter { it.id != chat.masterId && it.id != chat.id }.forEach { master ->
                            DropdownMenuItem(text = { Text("Join crew of ${master.title.ifBlank { "master" }}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Default.Groups, null) }, onClick = { menu = false; vm.assignMaster(chat.id, master.id) })
                        }
                        if (chat.masterId != null) {
                            DropdownMenuItem(text = { Text("Leave the crew") }, leadingIcon = { Icon(Icons.Default.PersonRemove, null) }, onClick = { menu = false; vm.assignMaster(chat.id, null) })
                        }
                    }
                    if (chat.open == true) {
                        DropdownMenuItem(text = { Text("Close desktop tab") }, leadingIcon = { Icon(Icons.Default.Close, null) }, onClick = { menu = false; vm.closeAgent(chat.id) })
                    }
                }
            }
        }
    )
}

@Composable private fun LeaseRow(lease: RemoteFileLease, vm: ChatViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(lease.path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("held by ${lease.holderTitle.ifBlank { lease.holderId }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton({ vm.releaseLease(lease.path) }) { Text("Release") }
    }
}

internal fun leaseModeText(mode: String?): String = when (mode) {
    "block" -> "Other agents are refused and told to work elsewhere."
    "off" -> "Same-file protection is off."
    else -> "Other agents ask before writing a held file."
}
