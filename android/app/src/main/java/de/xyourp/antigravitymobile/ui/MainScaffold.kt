package de.xyourp.antigravitymobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xyourp.antigravitymobile.net.*
import de.xyourp.antigravitymobile.ui.chat.ChatScreen
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainScaffold(vm: ChatViewModel, connected: Boolean, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val active = snapshot.conversations.firstOrNull { it.id == snapshot.activeConversationId } ?: snapshot.conversations.firstOrNull()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showModel by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.fillMaxHeight().width(330.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${snapshot.conversations.size} conversations", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ showNew = true }) { Icon(Icons.Default.Add, "New chat") }
            }
            HorizontalDivider()
            LazyColumn(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(snapshot.conversations.sortedByDescending { it.updatedAt }, key = { it.id }) { chat ->
                    NavigationDrawerItem(label = { Column {
                        Text(chat.title.ifBlank { "New chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${chat.providerId} · ${chat.model ?: "default"}${if (chat.busy) " · working" else ""}", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }, selected = chat.id == active?.id, onClick = { vm.switch(chat.id); scope.launch { drawer.close() } }, icon = { Icon(Icons.Default.ChatBubbleOutline, null) })
                }
            }
        }
    }) {
        Scaffold(topBar = { TopAppBar(
            navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Open chats") } },
            title = { Column {
                Text(active?.title?.ifBlank { "New chat" } ?: "Obsidian Agent", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (connected) "${active?.providerId ?: "OAC"} · ${active?.model ?: "default"} · ${active?.effort ?: "default"}" else "Offline · reconnecting", style = MaterialTheme.typography.labelSmall, color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            } },
            actions = {
                PowerStatus(snapshot.power, connected)
                if (active != null) TextButton({ showModel = true }) { Text("Model") }
                IconButton({ showNew = true }) { Icon(Icons.Default.Add, "New chat") }
                IconButton(onSettings) { Icon(Icons.Default.Settings, "Connection settings") }
            }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
            if (snapshot.protocolVersion != 0 && snapshot.protocolVersion != 1) Text("Protocol mismatch: phone supports 1, desktop reports ${snapshot.protocolVersion}", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(8.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) }
            ChatScreen(active, vm, Modifier.weight(1f))
        } }
    }
    if (showNew) AgentPickerDialog("New chat", snapshot.providers, onDismiss = { showNew = false }) { provider, model, effort -> vm.create(provider, model, effort); showNew = false }
    if (showModel && active != null) AgentPickerDialog("Agent, model and effort", snapshot.providers, initialProvider = active.providerId, initialModel = active.model, initialEffort = active.effort, onDismiss = { showModel = false }) { provider, model, effort -> vm.switchModel(provider, model.orEmpty(), effort); showModel = false }
}

@Composable private fun AgentPickerDialog(title: String, providers: List<RemoteProviderCatalog>, initialProvider: String? = null, initialModel: String? = null, initialEffort: String? = null, onDismiss: () -> Unit, onConfirm: (String, String?, String?) -> Unit) {
    val fallback = providers.firstOrNull() ?: RemoteProviderCatalog("codex", "Codex")
    var provider by remember { mutableStateOf(providers.firstOrNull { it.id == initialProvider } ?: fallback) }
    var model by remember(provider.id) { mutableStateOf(provider.models.firstOrNull { it.id == initialModel } ?: provider.models.firstOrNull()) }
    var effort by remember(model?.id) { mutableStateOf(initialEffort?.takeIf { it in model?.efforts.orEmpty() } ?: model?.efforts?.firstOrNull()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Picker("Agent provider", provider.label, providers, { it.label }) { provider = it; model = it.models.firstOrNull(); effort = model?.efforts?.firstOrNull() }
        Picker("Model", model?.label ?: "Provider default", provider.models, { it.label }) { model = it; effort = it.efforts.firstOrNull() }
        val efforts = model?.efforts.orEmpty()
        if (efforts.isNotEmpty()) Picker("Reasoning", effort ?: efforts.first(), efforts, { it }) { effort = it }
        model?.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } }, confirmButton = { Button({ onConfirm(provider.id, model?.id, effort) }) { Text(if (initialProvider == null) "Create" else "Apply") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable private fun <T> Picker(label: String, selected: String, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(selected, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(open, { open = false }) { options.forEach { option -> DropdownMenuItem({ Text(display(option)) }, { onSelect(option); open = false }) } }
    }
}

@Composable private fun PowerStatus(power: HostPowerStatus, connected: Boolean) {
    val available = power.available(connected)
    val label = when { !available -> "Laptop battery unavailable"; !power.hasBattery -> "Laptop on AC power"; power.chargingState == "charging" -> "Laptop ${power.percent}% charging"; power.chargingState == "full" -> "Laptop ${power.percent}% full"; else -> "Laptop ${power.percent}% battery" }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(if (available) Icons.Default.Power else Icons.Default.BatteryUnknown, label)
        Text(if (available && power.hasBattery) "${power.percent}%" else if (available) "AC" else "--", style = MaterialTheme.typography.labelMedium)
    }
}
