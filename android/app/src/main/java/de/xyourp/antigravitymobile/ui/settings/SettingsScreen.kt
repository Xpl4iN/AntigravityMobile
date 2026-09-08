package de.xyourp.antigravitymobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(vm: SettingsViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Connect to OAC") }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Enter the Tailscale address shown by Obsidian Agent Client. The bridge is reachable only through your private Tailscale network.")
        OutlinedTextField(state.host, vm::host, Modifier.fillMaxWidth(), label = { Text("Laptop Tailscale IP") }, placeholder = { Text("100.x.x.x") }, singleLine = true)
        OutlinedTextField(state.port, vm::port, Modifier.fillMaxWidth(), label = { Text("Bridge port") }, singleLine = true)
        OutlinedButton(vm::test, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Test bridge") }
        OutlinedTextField(state.pairCode, vm::code, Modifier.fillMaxWidth(), label = { Text("Six-digit pairing code") }, singleLine = true)
        Button(vm::pair, Modifier.fillMaxWidth(), enabled = !state.busy && !state.paired) { Text(if (state.paired) "Paired" else "Pair this phone") }
        state.message?.let { Text(it) }
        Button({ vm.save(onClose) }, Modifier.fillMaxWidth(), enabled = state.paired) { Text("Open Obsidian Agent Mobile") }
    } }
}
