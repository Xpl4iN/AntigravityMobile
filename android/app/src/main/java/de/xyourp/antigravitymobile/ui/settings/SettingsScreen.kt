package de.xyourp.antigravitymobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(vm: SettingsViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(
        navigationIcon = { if (state.paired) IconButton(onClose) { Icon(Icons.Default.ArrowBack, "Back to chats") } },
        title = { Column { Text("Mobile settings"); Text(if (state.paired) "Paired with your vault" else "Connect to Obsidian Agent Client", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary); Column { Text("Laptop bridge", style = MaterialTheme.typography.titleMedium); Text("Private connection over Tailscale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                OutlinedTextField(state.host, vm::host, Modifier.fillMaxWidth(), label = { Text("Laptop Tailscale IP") }, placeholder = { Text("100.x.x.x") }, singleLine = true)
                OutlinedTextField(state.port, vm::port, Modifier.fillMaxWidth(), label = { Text("Bridge port") }, supportingText = { Text("Configured in OAC desktop settings") }, singleLine = true)
                OutlinedButton(vm::test, Modifier.fillMaxWidth(), enabled = !state.busy) { Text(if (state.busy) "Checking…" else "Test connection") }
            } }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (state.paired) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { if (state.paired) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Column { Text(if (state.paired) "This Pixel is paired" else "Pair this phone", style = MaterialTheme.typography.titleMedium); Text(if (state.paired) "The secure device token is saved on this phone. You will reconnect automatically." else "Enter the one-time six-digit code from OAC.", style = MaterialTheme.typography.bodySmall) } }
                if (!state.paired) {
                    OutlinedTextField(state.pairCode, vm::code, Modifier.fillMaxWidth(), label = { Text("Pairing code") }, placeholder = { Text("000000") }, singleLine = true)
                    Button(vm::pair, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Pair this phone") }
                } else TextButton(vm::replacePairing) { Text("Pair with a different bridge") }
            } }
            state.message?.let { Text(it, color = if (it.contains("failed", true) || it.contains("Invalid", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.weight(1f))
            Button({ vm.save(onClose) }, Modifier.fillMaxWidth().padding(bottom = 16.dp), enabled = state.paired) { Text("Save and open chats") }
        }
    }
}
