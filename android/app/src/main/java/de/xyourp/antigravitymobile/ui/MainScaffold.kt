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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    Scaffold(topBar = { TopAppBar(title = {
        Column(Modifier.clickable { conversations = true }) {
            Text(active?.title ?: "Obsidian Agent Mobile", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (connected) "${active?.providerId ?: "OAC"} · connected" else "Disconnected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, actions = {
        PowerStatus(state.snapshot.power, connected)
        IconButton({ vm.create("codex") }) { Icon(Icons.Default.Add, "New conversation") }
        IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
        DropdownMenu(conversations, { conversations = false }) {
            state.snapshot.conversations.forEach { c -> DropdownMenuItem({ Text("${c.title} · ${c.providerId}", maxLines = 1) }, { conversations = false; vm.switch(c.id) }) }
        }
    }) }) { padding -> ChatScreen(active, vm, Modifier.fillMaxSize().padding(padding)) }
}

@Composable private fun PowerStatus(power: HostPowerStatus, connected: Boolean) {
    val label = when { !power.available(connected) -> "Battery unavailable"; !power.hasBattery -> "No battery (AC)"; power.chargingState == "charging" -> "${power.percent}% charging"; power.chargingState == "full" -> "${power.percent}% full"; else -> "${power.percent}% battery" }
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (power.available(connected)) Icons.Default.Power else Icons.Default.BatteryUnknown, label)
        Text(if (power.available(connected) && power.hasBattery) "${power.percent}%" else if (power.available(connected)) "AC" else "--", style = MaterialTheme.typography.labelMedium)
    }
}
