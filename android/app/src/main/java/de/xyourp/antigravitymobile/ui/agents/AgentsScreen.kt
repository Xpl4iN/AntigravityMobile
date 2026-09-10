package de.xyourp.antigravitymobile.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xyourp.antigravitymobile.net.ApprovalCard
import de.xyourp.antigravitymobile.net.CrewTemplate
import de.xyourp.antigravitymobile.net.PlanItem
import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.RemoteFileLease
import de.xyourp.antigravitymobile.net.RemoteSnapshot
import de.xyourp.antigravitymobile.net.ScheduledRun
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel
import de.xyourp.antigravitymobile.ui.leaseModeText
import de.xyourp.antigravitymobile.ui.stateColor
import de.xyourp.antigravitymobile.ui.stateLabel
import de.xyourp.antigravitymobile.ui.stateOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The phone's agent manager (OAC ADR 0012): provider load, each master with
 * its crew and plan, independent agents, the files agents hold, and the
 * desktop's scheduled runs. Every action names its conversation explicitly,
 * so nothing here depends on which chat is active.
 */
@Composable fun AgentsScreen(vm: ChatViewModel, onOpenChat: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val board = remember(snapshot) { agentBoard(snapshot) }
    val titles = remember(snapshot.conversations) { snapshot.conversations.associate { it.id to it.title.ifBlank { "New chat" } } }
    var showAddRun by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (snapshot.providerLoad.isNotEmpty()) item(key = "load") { ProviderLoadStrip(snapshot) }
        if (!board.legacy) item(key = "review-gate") { ReviewGateNote(snapshot.reviewGate) }
        if (board.legacy) item(key = "legacy") {
            Note(Icons.Default.Info, "This desktop does not report agent state yet, so only busy chats are listed.")
        }
        if (board.isEmpty) item(key = "empty") { EmptyAgents() }

        board.groups.forEach { group ->
            item(key = "agent-" + group.master.id) { AgentCard(group.master, snapshot, titles, vm, crew = false, onOpenChat) }
            items(group.crew, key = { "agent-" + it.id }) { AgentCard(it, snapshot, titles, vm, crew = true, onOpenChat) }
        }
        if (board.independent.isNotEmpty()) {
            if (board.groups.isNotEmpty()) item(key = "independent-header") { SectionHeader(Icons.Default.Person, "Independent agents") }
            items(board.independent, key = { "agent-" + it.id }) { AgentCard(it, snapshot, titles, vm, crew = false, onOpenChat) }
        }

        if (!board.legacy || snapshot.leases.isNotEmpty()) item(key = "leases") { LeaseSection(snapshot, vm) }
        if (!board.legacy || snapshot.scheduledRuns.isNotEmpty()) item(key = "schedules") {
            ScheduleSection(snapshot, vm, onOpenChat, onAdd = { showAddRun = true })
        }
    }

    if (showAddRun) AddRunDialog(snapshot, onDismiss = { showAddRun = false }) { draft ->
        vm.addSchedule(draft.name, draft.prompt, draft.time, draft.repeat, draft.date, draft.providerId, draft.asMaster, draft.templateId)
        showAddRun = false
    }
}

// ---------------------------------------------------------------- header strips

@Composable private fun ProviderLoadStrip(snapshot: RemoteSnapshot) {
    // Catalog order first, so the strip does not reshuffle between snapshots.
    val order = snapshot.providers.map { it.id }
    val entries = snapshot.providerLoad.entries.sortedWith(compareBy({ order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.key }))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (providerId, load) ->
            val full = load.limit > 0 && load.running >= load.limit
            val tint = when {
                load.waiting > 0 -> Color(0xFFE5A00D)
                full -> MaterialTheme.colorScheme.tertiary
                load.running > 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val name = providerId.replaceFirstChar { it.uppercase() }
            val count = if (load.limit > 0) "${load.running}/${load.limit}" else "${load.running} running"
            val text = if (load.waiting > 0) "$name $count · ${load.waiting} waiting" else "$name $count"
            Pill(Icons.Default.Speed, text, tint)
        }
    }
}

@Composable private fun ReviewGateNote(on: Boolean) {
    Note(
        Icons.Default.RateReview,
        if (on) "Review step is on: a reviewer checks crew work before the master accepts it." else "Review step is off: the master accepts crew work directly."
    )
}

@Composable private fun EmptyAgents() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Groups, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text("No agents are running", style = MaterialTheme.typography.titleSmall)
        Text("Open chats and busy agents on the desktop show up here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- agent card

@Composable private fun AgentCard(chat: RemoteConversation, snapshot: RemoteSnapshot, titles: Map<String, String>, vm: ChatViewModel, crew: Boolean, onOpenChat: (String) -> Unit) {
    val state = stateOf(chat)
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = if (crew) 20.dp else 0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (crew) 0.35f else 0.55f)),
        border = BorderStroke(1.dp, if (chat.isMaster) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(stateColor(state)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.isMaster) {
                            Icon(Icons.Default.WorkspacePremium, "Master agent", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(chat.title.ifBlank { "New chat" }, style = MaterialTheme.typography.titleSmall, fontWeight = if (chat.isMaster) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(listOf(chat.providerId, chat.model ?: "default").joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Text(stateLabel(state).ifBlank { "idle" }, style = MaterialTheme.typography.labelSmall, color = if (state == "idle") MaterialTheme.colorScheme.onSurfaceVariant else stateColor(state))
            }

            val hasChips = chat.crewRole != null || chat.held || chat.worktree != null
            if (hasChips) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chat.crewRole?.let { Pill(Icons.Default.Badge, it, MaterialTheme.colorScheme.secondary) }
                    if (chat.held) Pill(Icons.Default.HourglassTop, "waiting for a slot", Color(0xFFE5A00D))
                    chat.worktree?.takeIf { it.branch.isNotBlank() }?.let { Pill(Icons.Default.AccountTree, it.branch, MaterialTheme.colorScheme.primary) }
                }
            }

            // Only live requests: a finished turn can leave an approval the desktop never resolved.
            val approvals = if (chat.busy || chat.agentState == "approval") chat.messages.flatMap { it.approvals }.filter { it.decision == "pending" } else emptyList()
            approvals.forEach { approval -> ApprovalRow(approval) { approved -> vm.approveFor(chat.id, approval.id, approved) } }

            if (chat.isMaster) PlanBoard(chat, snapshot.crewTemplates, titles, vm)
            if (chat.queued.isNotEmpty()) QueueSection(chat, vm)
            AgentComposer(chat, vm)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (chat.busy) {
                    TextButton({ vm.cancelFor(chat.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Stop")
                    }
                }
                TextButton({ onOpenChat(chat.id) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Open chat")
                }
            }
        }
    }
}

@Composable private fun ApprovalRow(approval: ApprovalCard, onDecide: (Boolean) -> Unit) {
    val amber = Color(0xFFE5A00D)
    Surface(shape = RoundedCornerShape(8.dp), color = amber.copy(alpha = 0.1f), border = BorderStroke(1.dp, amber.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Gavel, null, Modifier.size(16.dp), tint = amber)
                Text("Needs approval · ${approval.kind}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = amber)
            }
            if (approval.path.isNotBlank()) Text(approval.path, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            approval.paths.filter { it != approval.path }.forEach { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            approval.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onDecide(true) }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Approve")
                }
                OutlinedButton({ onDecide(false) }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reject")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- plan board

@Composable private fun PlanBoard(master: RemoteConversation, templates: List<CrewTemplate>, titles: Map<String, String>, vm: ChatViewModel) {
    val items = master.plan?.items.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Checklist, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Plan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text("${items.count { it.status == "done" }}/${items.size} done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (templates.isNotEmpty()) CrewTemplateMenu(templates) { template -> vm.spawnCrew(master.id, template.id) }
        }
        if (items.isEmpty()) {
            Text("No plan yet. The master posts one when it splits the work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { item -> PlanItemRow(item, titles) { status -> vm.setPlanTaskStatus(master.id, item.id, status) } }
    }
}

@Composable private fun CrewTemplateMenu(templates: List<CrewTemplate>, onPick: (CrewTemplate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Icon(Icons.Default.GroupAdd, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Start crew", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(open, { open = false }) {
            Text("Start crew from template", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            templates.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(template.name.ifBlank { template.id })
                            Text(template.members.joinToString(", ") { m -> m.title.ifBlank { m.crewRole ?: "member" } }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Groups, null) },
                    onClick = { open = false; onPick(template) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PlanItemRow(item: PlanItem, titles: Map<String, String>, onStatus: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box {
            val color = planStatusColor(item.status)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                // Tap walks the common path; long-press opens every status, including blocked.
                modifier = Modifier.width(72.dp).clip(RoundedCornerShape(6.dp)).combinedClickable(
                    onClickLabel = "Mark ${planStatusLabel(nextPlanStatus(item.status)).lowercase()}",
                    onLongClickLabel = "Choose status",
                    onLongClick = { menu = true },
                    onClick = { onStatus(nextPlanStatus(item.status)) }
                )
            ) {
                Text(planStatusLabel(item.status), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                PLAN_STATUSES.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(planStatusLabel(status)) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(planStatusColor(status))) },
                        trailingIcon = if (status == item.status) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                        onClick = { menu = false; if (status != item.status) onStatus(status) }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { item.id }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (item.status == "done") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            val meta = listOfNotNull(
                item.owner?.let { titles[it] ?: it },
                item.dependsOn.takeIf { it.isNotEmpty() }?.let { "after ${it.joinToString(", ")}" },
                item.files.takeIf { it.isNotEmpty() }?.let { if (it.size == 1) it.first().substringAfterLast('/') else "${it.size} files" }
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun planStatusLabel(status: String) = when (status) {
    "doing" -> "Doing"
    "done" -> "Done"
    "blocked" -> "Blocked"
    else -> "To do"
}

@Composable private fun planStatusColor(status: String): Color = when (status) {
    "doing" -> MaterialTheme.colorScheme.primary
    "done" -> Color(0xFF3FB950)
    "blocked" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------------------------------------------------------------- queue and composer

@Composable private fun QueueSection(chat: RemoteConversation, vm: ChatViewModel) {
    var editing by remember(chat.id) { mutableStateOf<Int?>(null) }
    val queue = chat.queued
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Queue (${queue.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // The desktop starts the next item itself while a turn runs, so Run next only helps an idle agent.
            if (!chat.busy) TextButton({ vm.startQueueFor(chat.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("Run next", style = MaterialTheme.typography.labelMedium)
            }
            TextButton({ vm.clearQueueFor(chat.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        queue.forEachIndexed { index, item ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
                    Text("${index + 1}. ${item.text}", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SmallIconButton(Icons.Default.ArrowUpward, "Move up", enabled = index > 0) { vm.moveQueued(chat.id, index, index - 1) }
                        SmallIconButton(Icons.Default.ArrowDownward, "Move down", enabled = index < queue.lastIndex) { vm.moveQueued(chat.id, index, index + 1) }
                        SmallIconButton(Icons.Default.VerticalAlignTop, "Run first", enabled = index > 0) { vm.promoteQueued(chat.id, index) }
                        SmallIconButton(Icons.Default.Edit, "Edit") { editing = index }
                        SmallIconButton(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error) { vm.removeQueued(chat.id, index) }
                    }
                }
            }
        }
    }
    editing?.let { index ->
        val original = queue.getOrNull(index)
        if (original == null) editing = null
        else EditQueueDialog(original.text, onDismiss = { editing = null }) { text -> vm.updateQueued(chat.id, index, text); editing = null }
    }
}

@Composable private fun EditQueueDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit queued message") },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 8) },
        confirmButton = { Button({ onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun AgentComposer(chat: RemoteConversation, vm: ChatViewModel) {
    var text by rememberSaveable(chat.id) { mutableStateOf("") }
    val ready = text.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (chat.busy) "Queue or steer…" else "Message ${chat.title.ifBlank { "agent" }}…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            shape = RoundedCornerShape(10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (chat.busy) {
                // After the current tool, so steering never cuts a file write in half.
                OutlinedButton({ vm.steerTo(chat.id, text.trim(), immediate = false); text = "" }, enabled = ready, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Steer")
                }
            }
            Button(
                onClick = { if (chat.busy) vm.queueTo(chat.id, text.trim()) else vm.sendTo(chat.id, text.trim()); text = "" },
                enabled = ready,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(if (chat.busy) Icons.Default.PlaylistAdd else Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (chat.busy) "Queue" else "Send")
            }
        }
    }
}

// ---------------------------------------------------------------- leases

@Composable private fun LeaseSection(snapshot: RemoteSnapshot, vm: ChatViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(Icons.Default.Lock, "Files being edited")
        Text(leaseModeText(snapshot.leaseMode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (snapshot.leases.isEmpty()) Text("No agent holds a file right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot.leases.forEach { lease -> LeaseRow(lease, vm) }
    }
}

@Composable private fun LeaseRow(lease: RemoteFileLease, vm: ChatViewModel) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lease.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A reservation is a claim made before the first write, typically a crew member's planned files.
                if (lease.reserved) Pill(null, "reserved", MaterialTheme.colorScheme.tertiary)
                Text("${if (lease.reserved) "reserved by" else "held by"} ${lease.holderTitle.ifBlank { lease.holderId }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton({ vm.releaseLease(lease.path) }) { Text("Release") }
    }
}

// ---------------------------------------------------------------- scheduled runs

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

private fun repeatLabel(repeat: String) = when (repeat) {
    "daily" -> "daily"
    "weekdays" -> "weekdays"
    else -> "once"
}

@Composable private fun ScheduleSection(snapshot: RemoteSnapshot, vm: ChatViewModel, onOpenChat: (String) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(Icons.Default.Alarm, "Scheduled runs", Modifier.weight(1f))
            TextButton(onAdd) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add run") }
        }
        if (snapshot.scheduledRuns.isEmpty()) Text("Nothing scheduled. A run starts a prompt on the desktop at a set time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot.scheduledRuns.forEach { run -> ScheduleRow(run, snapshot, vm, onOpenChat) }
    }
}

@Composable private fun ScheduleRow(run: ScheduledRun, snapshot: RemoteSnapshot, vm: ChatViewModel, onOpenChat: (String) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val next = remember(run) { nextRunAt(run, LocalDateTime.now()) }
    val template = snapshot.crewTemplates.firstOrNull { it.id == run.templateId }
    val lastChat = run.lastConversationId?.takeIf { id -> snapshot.conversations.any { it.id == id } }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(run.name.ifBlank { "Untitled run" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val whenText = buildString {
                        append(run.time.ifBlank { "--:--" }); append(" · "); append(repeatLabel(run.repeat))
                        if (run.repeat == "once" && run.date != null) append(" · ${run.date}")
                    }
                    Text(whenText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = run.enabled, onCheckedChange = { vm.toggleSchedule(run.id, it) })
                IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete run", tint = MaterialTheme.colorScheme.error) }
            }
            if (run.prompt.isNotBlank()) Text(run.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (run.providerId.isNotBlank()) Pill(null, run.providerId, MaterialTheme.colorScheme.onSurfaceVariant)
                if (run.asMaster) Pill(Icons.Default.WorkspacePremium, "as master", MaterialTheme.colorScheme.primary)
                if (run.templateId != null) Pill(Icons.Default.Groups, template?.name ?: run.templateId, MaterialTheme.colorScheme.secondary)
            }
            val nextText = when {
                !run.enabled -> "Paused"
                next != null -> "Next: ${next.format(WHEN)}"
                run.repeat == "once" -> "No upcoming run"
                else -> "Next: unknown"
            }
            val lastText = run.lastRunAt?.let { "Last: ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().format(WHEN)}" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(nextText, lastText).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (lastChat != null) TextButton({ onOpenChat(lastChat) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Last run", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete scheduled run?") },
            text = { Text("\"${run.name.ifBlank { "Untitled run" }}\" will no longer start on the desktop.") },
            confirmButton = { Button({ confirmDelete = false; vm.removeSchedule(run.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** What the Add run dialog hands back; mirrors the `schedule` object of `schedule-add`. */
private data class RunDraft(val name: String, val prompt: String, val time: String, val repeat: String, val date: String?, val providerId: String, val asMaster: Boolean, val templateId: String?)

@Composable private fun AddRunDialog(snapshot: RemoteSnapshot, onDismiss: () -> Unit, onAdd: (RunDraft) -> Unit) {
    // Provider catalog first; an offline or older desktop may only report load.
    val providers = remember(snapshot) {
        snapshot.providers.map { it.id to it.label }.ifEmpty { snapshot.providerLoad.keys.map { it to it.replaceFirstChar { c -> c.uppercase() } } }
    }
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("09:00") }
    var repeat by remember { mutableStateOf("daily") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var providerId by remember { mutableStateOf(providers.firstOrNull()?.first.orEmpty()) }
    var asMaster by remember { mutableStateOf(false) }
    var templateId by remember { mutableStateOf<String?>(null) }

    val timeOk = isValidTime(time)
    val dateOk = repeat != "once" || isValidDate(date)
    val valid = name.isNotBlank() && prompt.isNotBlank() && timeOk && dateOk && providerId.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add scheduled run") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Prompt") }, minLines = 3, maxLines = 6)
                OutlinedTextField(
                    time, { time = it.take(5) }, Modifier.fillMaxWidth(),
                    label = { Text("Time (HH:MM, desktop clock)") }, singleLine = true, isError = !timeOk,
                    supportingText = if (!timeOk) ({ Text("Use 24-hour HH:MM, e.g. 22:30") }) else null
                )
                Column {
                    Text("Repeat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("once", "daily", "weekdays").forEach { option ->
                            FilterChip(selected = repeat == option, onClick = { repeat = option }, label = { Text(repeatLabel(option).replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
                if (repeat == "once") {
                    OutlinedTextField(
                        date, { date = it.take(10) }, Modifier.fillMaxWidth(),
                        label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = !dateOk,
                        supportingText = if (!dateOk) ({ Text("Use YYYY-MM-DD, e.g. ${LocalDate.now()}") }) else null
                    )
                }
                if (providers.isEmpty()) {
                    Text("The desktop has not reported its providers yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    OptionPicker("Provider", providers.firstOrNull { it.first == providerId }?.second ?: providerId, providers, { it.second }) { providerId = it.first }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Start as master", style = MaterialTheme.typography.bodyMedium)
                        Text("The run's chat can direct a crew.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = asMaster, onCheckedChange = { asMaster = it; if (!it) templateId = null })
                }
                // A crew template only means something for a master, so it is offered only then.
                if (asMaster && snapshot.crewTemplates.isNotEmpty()) {
                    val options = listOf<CrewTemplate?>(null) + snapshot.crewTemplates
                    val selected = snapshot.crewTemplates.firstOrNull { it.id == templateId }
                    OptionPicker("Crew template (optional)", selected?.name ?: "No crew", options, { it?.name?.ifBlank { it.id } ?: "No crew" }) { templateId = it?.id }
                }
            }
        },
        confirmButton = {
            Button({
                onAdd(RunDraft(name.trim(), prompt.trim(), time, repeat, date.takeIf { repeat == "once" }, providerId, asMaster, templateId.takeIf { asMaster }))
            }, enabled = valid) { Text("Add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun <T> OptionPicker(label: String, selected: String, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
                Text(selected, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) { options.forEach { option -> DropdownMenuItem({ Text(display(option)) }, { onSelect(option); open = false }) } }
        }
    }
}

// ---------------------------------------------------------------- small pieces

@Composable private fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable private fun Note(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The rounded label chip the top bar uses, in a given accent. */
@Composable private fun Pill(icon: ImageVector?, text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = 0.1f), border = BorderStroke(1.dp, tint.copy(alpha = 0.4f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon?.let { Icon(it, null, Modifier.size(12.dp), tint = tint) }
            Text(text, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        }
    }
}

@Composable private fun SmallIconButton(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(32.dp), enabled = enabled) {
        Icon(icon, description, Modifier.size(16.dp), tint = if (enabled) tint else tint.copy(alpha = 0.3f))
    }
}
