package de.xyourp.antigravitymobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.xyourp.antigravitymobile.net.DisplayMessage
import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.SubAgentCard

@Composable fun ChatScreen(conversation: RemoteConversation?, vm: ChatViewModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            if (conversation == null) item { Text("No conversations yet. Tap + to create one.") }
            else items(conversation.messages, key = { it.id }) { Message(it, vm) }
        }
        Composer(conversation?.busy == true, { vm.send(it) }, { text, immediate -> vm.steer(text, immediate) }, vm::cancel)
    }
}

@Composable private fun Message(message: DisplayMessage, vm: ChatViewModel) {
    val user = message.role == "user"
    Surface(color = if (user) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (message.text.isNotBlank()) Text(message.text)
            if (message.tools.isNotEmpty()) Card { Column(Modifier.padding(10.dp)) { Text("${message.tools.size} tool calls", fontWeight = FontWeight.SemiBold); message.tools.forEach { Text("${if (it.state == "success") "✓" else "•"} ${it.name}: ${it.description.orEmpty()}", style = MaterialTheme.typography.bodySmall) } } }
            message.subAgents.forEach { SubAgent(it) }
            message.approvals.filter { it.decision == "pending" }.forEach { approval -> Card { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Approval required", fontWeight = FontWeight.Bold); Text(approval.path); Text(approval.diff, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.approve(approval.id, true) }) { Text("Approve") }; OutlinedButton({ vm.approve(approval.id, false) }) { Text("Reject") } } } } }
            message.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

@Composable private fun Composer(busy: Boolean, send: (String) -> Unit, steer: (String, Boolean) -> Unit, stop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(tonalElevation = 3.dp) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(if (busy) "Steer the active turn" else "Message OAC") }, maxLines = 5)
        if (busy) IconButton(stop) { Icon(Icons.Default.Stop, "Stop") }
        IconButton({ val value = text.trim(); if (value.isNotEmpty()) { if (busy) steer(value, false) else send(value); text = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, if (busy) "Steer after current tool" else "Send") }
    } }
}
