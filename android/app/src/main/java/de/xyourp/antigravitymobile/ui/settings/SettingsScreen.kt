package de.xyourp.antigravitymobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(vm: SettingsViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    val cardShape = RoundedCornerShape(12.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (state.paired) {
                        IconButton(onClose) { Icon(Icons.Default.ArrowBack, "Back to chats") }
                    }
                },
                title = {
                    Column {
                        Text("Mobile Settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.paired) "Paired with your vault" else "Connect to Obsidian Agent Client",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bridge Connection Card
            Card(
                Modifier.fillMaxWidth(),
                shape = cardShape,
                border = cardBorder,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Laptop Bridge", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Private connection over Tailscale",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedTextField(
                        state.host,
                        vm::host,
                        Modifier.fillMaxWidth(),
                        label = { Text("Laptop Tailscale IP") },
                        placeholder = { Text("100.x.x.x") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        state.port,
                        vm::port,
                        Modifier.fillMaxWidth(),
                        label = { Text("Bridge port") },
                        supportingText = { Text("Configured in OAC desktop settings") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedButton(
                        vm::test,
                        Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (state.busy) "Checking..." else "Test connection")
                    }
                }
            }

            // Pairing Status Card
            Card(
                Modifier.fillMaxWidth(),
                shape = cardShape,
                border = cardBorder,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.paired) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text(
                                if (state.paired) "Pixel Paired" else "Pair this phone",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (state.paired)
                                    "The secure device token is active. You will reconnect automatically."
                                else
                                    "Enter the one-time six-digit code from OAC on your desktop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.paired) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Connected Host", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.host}:${state.port}", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(vm::replacePairing, modifier = Modifier.align(Alignment.End)) {
                            Text("Pair with a different bridge", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        OutlinedTextField(
                            state.pairCode,
                            vm::code,
                            Modifier.fillMaxWidth(),
                            label = { Text("Pairing code") },
                            placeholder = { Text("000000") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            vm::pair,
                            Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pair this phone")
                        }
                    }
                }
            }

            // Display & Preferences Card
            Card(
                Modifier.fillMaxWidth(),
                shape = cardShape,
                border = cardBorder,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Conversation Display", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Configure chat rendering and execution details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                    PreferenceSwitch("Follow new output", "Stop following when you scroll up.", state.display.autoScroll) {
                        vm.display(state.display.copy(autoScroll = it))
                    }
                    PreferenceSwitch("Expand sub-agents", "Open delegated work automatically.", state.display.autoExpandSubAgents) {
                        vm.display(state.display.copy(autoExpandSubAgents = it))
                    }
                    PreferenceSwitch("Expand reasoning", "Show reported reasoning without another tap.", state.display.autoExpandReasoning) {
                        vm.display(state.display.copy(autoExpandReasoning = it))
                    }
                    var toolsOpen by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Tool activity")
                            Text(
                                "Control raw tool input and output.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton({ toolsOpen = true }) {
                                Text(
                                    when (state.display.toolDisplay) {
                                        "hidden" -> "Hidden"
                                        "full" -> "Full"
                                        else -> "Names"
                                    },
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(toolsOpen, { toolsOpen = false }) {
                                listOf(
                                    "hidden" to "Hidden",
                                    "names" to "Names and status",
                                    "full" to "Full input and output"
                                ).forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            toolsOpen = false
                                            vm.display(state.display.copy(toolDisplay = id))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.message?.let {
                Text(
                    it,
                    color = if (it.contains("failed", true) || it.contains("Invalid", true))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
            Button(
                { vm.save(onClose) },
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
                enabled = state.paired,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save and open chats")
            }
        }
    }
}

@Composable private fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    change: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked,
            change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}
