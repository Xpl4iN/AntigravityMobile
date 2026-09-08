package de.xyourp.antigravitymobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xyourp.antigravitymobile.net.HostPowerStatus
import de.xyourp.antigravitymobile.ui.chat.ChatScreen
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainScaffold(vm: ChatViewModel, connected: Boolean, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active = state.snapshot.conversations.firstOrNull { it.id == state.snapshot.activeConversationId } ?: state.snapshot.conversations.firstOrNull()
    var conversations by remember { mutableStateOf(false) }
    var modelDialog by remember { mutableStateOf(false) }
    var model by remember(active?.model) { mutableStateOf(active?.model.orEmpty()) }
    var effort by remember(active?.effort) { mutableStateOf(active?.effort.orEmpty()) }
    Scaffold(topBar = { TopAppBar(title = {
        Column(Modifier.clickable { conversations = true }) {
            Text(active?.title ?: "Obsidian Agent Mobile", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (connected) "${active?.providerId ?: "OAC"} · ${active?.model ?: "default"} · ${active?.effort ?: "default"}" else "Disconnected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { if (active != null) modelDialog = true })
        }
    }, actions = {
        PowerStatus(state.snapshot.power, connected)
        IconButton({ vm.create("codex") }) { Icon(Icons.Default.Add, "New conversation") }
        IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
        DropdownMenu(conversations, { conversations = false }) {
            state.snapshot.conversations.forEach { c -> DropdownMenuItem({ Text("${c.title} · ${c.providerId}", maxLines = 1) }, { conversations = false; vm.switch(c.id) }) }
        }
    }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        if (state.snapshot.protocolVersion != 0 && state.snapshot.protocolVersion != 1) Text("Protocol mismatch: phone supports 1, desktop reports ${state.snapshot.protocolVersion}", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(8.dp))
        ChatScreen(active, vm, Modifier.weight(1f))
    } }
    if (modelDialog) AlertDialog(onDismissRequest = { modelDialog = false }, title = { Text("Model and effort") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(model, { model = it }, label = { Text("Model id") }); OutlinedTextField(effort, { effort = it }, label = { Text("Effort") }) } }, confirmButton = { Button({ vm.switchModel(model.trim(), effort.trim().ifBlank { null }); modelDialog = false }) { Text("Apply") } }, dismissButton = { TextButton({ modelDialog = false }) { Text("Cancel") } })
}

@Composable private fun PowerStatus(power: HostPowerStatus, connected: Boolean) {
    val label = when { !power.available(connected) -> "Battery unavailable"; !power.hasBattery -> "No battery (AC)"; power.chargingState == "charging" -> "${power.percent}% charging"; power.chargingState == "full" -> "${power.percent}% full"; else -> "${power.percent}% battery" }
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (power.available(connected)) Icons.Default.Power else Icons.Default.BatteryUnknown, label)
        Text(if (power.available(connected) && power.hasBattery) "${power.percent}%" else if (power.available(connected)) "AC" else "--", style = MaterialTheme.typography.labelMedium)
    }
}
