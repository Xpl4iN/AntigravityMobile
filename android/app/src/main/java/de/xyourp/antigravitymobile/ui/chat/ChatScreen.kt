package de.xyourp.antigravitymobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xyourp.antigravitymobile.net.DisplayMessage
import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.SubAgentCard

@Composable fun ChatScreen(conversation: RemoteConversation?, vm: ChatViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            if (conversation == null) item { Text("No conversations yet. Tap + to create one.") }
            else items(conversation.messages, key = { it.id }) { Message(it, vm) }
        }
        Composer(conversation?.busy == true, vm::send, vm::steer, vm::cancel)
    }
    state.openFile?.let { file -> AlertDialog(onDismissRequest = vm::closeFile, title = { Text(file.name) }, text = { LazyColumn { item { Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); HorizontalDivider(Modifier.padding(vertical = 8.dp)); MarkdownText(file.content) } } }, confirmButton = { TextButton(vm::closeFile) { Text("Close") } }) }
}

@Composable private fun Message(message: DisplayMessage, vm: ChatViewModel) {
    val user = message.role == "user"
    Surface(color = if (user) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (message.text.isNotBlank()) MarkdownText(message.text)
            val cited = remember(message.text, message.context) { (Regex("\\[\\[([^]|#]+)").findAll(message.text).map { it.groupValues[1] } + message.context.asSequence().map { it.path }).distinct().toList() }
            if (cited.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vault references", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                cited.forEach { path -> TextButton({ vm.openFile(path) }) { Text("Open $path", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) } }
            }
            if (message.tools.isNotEmpty()) Card { Column(Modifier.padding(10.dp)) { Text("${message.tools.size} tool calls", fontWeight = FontWeight.SemiBold); message.tools.forEach { Text("${if (it.state == "success") "✓" else "•"} ${it.name}: ${it.description.orEmpty()}", style = MaterialTheme.typography.bodySmall) } } }
            message.subAgents.forEach { SubAgent(it) }
            message.approvals.filter { it.decision == "pending" }.forEach { approval -> Card { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Approval required", fontWeight = FontWeight.Bold); Text(approval.path); Text(approval.diff, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.approve(approval.id, true) }) { Text("Approve") }; OutlinedButton({ vm.approve(approval.id, false) }) { Text("Reject") } } } } }
            message.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable private fun MarkdownText(markdown: String) {
    var fenced = false
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markdown.lines().forEach { raw ->
            if (raw.trimStart().startsWith("```")) { fenced = !fenced; return@forEach }
            val line = raw.trimEnd()
            val header = line.takeWhile { it == '#' }.length.takeIf { it in 1..4 && line.getOrNull(it) == ' ' }
            Text(
                text = when { header != null -> line.drop(header + 1); line.startsWith("- ") -> "• ${line.drop(2)}"; else -> line },
                fontFamily = if (fenced || line.startsWith("    ")) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (header != null) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (header != null) (22 - header * 2).sp else MaterialTheme.typography.bodyMedium.fontSize,
                color = if (fenced) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable private fun SubAgent(agent: SubAgentCard) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }) { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${agent.name} · ${agent.state}", fontWeight = FontWeight.SemiBold)
        agent.task?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (expanded) { HorizontalDivider(); agent.detail?.let { Text(it) }; agent.timeline.forEach { Text("${it.kind}: ${it.title}${it.detail?.let { d -> " · $d" }.orEmpty()}", style = MaterialTheme.typography.bodySmall) }; agent.result?.let { Text(it) }; agent.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    } }
}

@Composable private fun Composer(busy: Boolean, send: (String, String?) -> Unit, steer: (String, Boolean, String?) -> Unit, stop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var contextPath by remember { mutableStateOf("") }; var showContext by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) { Column(Modifier.fillMaxWidth().padding(10.dp)) {
        if (showContext) OutlinedTextField(contextPath, { contextPath = it }, Modifier.fillMaxWidth(), label = { Text("Vault note or file path") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton({ showContext = !showContext }) { Icon(Icons.Default.AttachFile, "Attach vault file") }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(if (busy) "Steer the active turn" else "Message OAC") }, maxLines = 5)
            if (busy) { IconButton(stop) { Icon(Icons.Default.Stop, "Stop") }; IconButton({ val value = text.trim(); if (value.isNotEmpty()) { steer(value, true, contextPath.ifBlank { null }); text = "" } }) { Icon(Icons.Default.Bolt, "Steer immediately") } }
            IconButton({ val value = text.trim(); if (value.isNotEmpty()) { if (busy) steer(value, false, contextPath.ifBlank { null }) else send(value, contextPath.ifBlank { null }); text = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, if (busy) "Steer after current tool" else "Send") }
        }
    } }
}
