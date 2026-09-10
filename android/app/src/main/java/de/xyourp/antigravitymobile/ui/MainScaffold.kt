package de.xyourp.antigravitymobile.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import de.xyourp.antigravitymobile.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xyourp.antigravitymobile.net.*
import de.xyourp.antigravitymobile.data.DisplayPreferences
import de.xyourp.antigravitymobile.ui.chat.ChatScreen
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel
import de.xyourp.antigravitymobile.ui.chat.FullScreenFileViewer
import de.xyourp.antigravitymobile.ui.files.FilesScreen
import de.xyourp.antigravitymobile.ui.git.GitScreen
import kotlinx.coroutines.launch

enum class MainTab { CHATS, FILES, GIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainScaffold(vm: ChatViewModel, connected: Boolean, display: DisplayPreferences, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val active = snapshot.conversations.firstOrNull { it.id == snapshot.activeConversationId } ?: snapshot.conversations.firstOrNull()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var currentTab by remember { mutableStateOf(MainTab.CHATS) }
    var showModel by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var clearConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearFeedback()
        }
    }

    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = currentTab == MainTab.CHATS, drawerContent = {
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
        val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        // Row 1: Menu icon + Full-page Chat Title + Battery + Overflow Menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (currentTab == MainTab.CHATS) scope.launch { drawer.open() }
                                else currentTab = MainTab.CHATS
                            }) {
                                Icon(
                                    imageVector = if (currentTab == MainTab.CHATS) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Menu"
                                )
                            }

                            Text(
                                text = when (currentTab) {
                                    MainTab.CHATS -> active?.title?.ifBlank { "New chat" } ?: "Obsidian Agent"
                                    MainTab.FILES -> "Vault Files"
                                    MainTab.GIT -> "Git Changes"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            )

                            PowerStatus(snapshot.power, connected)

                            IconButton(onClick = onSettings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Row 2: Horizontally Scrollable Action Strip (on Chats tab)
                        if (currentTab == MainTab.CHATS) {
                            val subtitle = remember(connected, active?.providerId, active?.model, active?.effort) {
                                if (!connected) {
                                    "Offline · reconnecting"
                                } else {
                                    val provider = active?.providerId ?: "OAC"
                                    val model = active?.model?.takeIf { it.isNotBlank() && it != "default" }
                                    val effort = active?.effort?.takeIf { it.isNotBlank() && it != "default" }
                                    buildString {
                                        append(provider)
                                        if (model != null) append(" · $model")
                                        if (effort != null) append(" ($effort)")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Model & effort pill
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { showModel = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // 2. New Chat Pill
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { showNew = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "New",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // 2. Context & Quota Pill
                                val quota = state.snapshot.quota
                                val ctxSummary = remember(quota) {
                                    if (quota == null) "Usage"
                                    else {
                                        val pct = quota.contextRemainingPercent?.let { "${100 - it}% ctx" } ?: ""
                                        val primary = "${quota.primaryRemainingPercent}%"
                                        if (pct.isNotEmpty()) "$pct · $primary" else "Quota $primary"
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { vm.setQuotaSheet(true) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Speed,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = ctxSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // 3. Handover Pill
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (state.isDraftingHandover) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable(enabled = !state.isDraftingHandover) { vm.draftHandover() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (state.isDraftingHandover) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.TaskAlt,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = if (state.isDraftingHandover) "Drafting..." else "Handover",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // 4. Commit & Push Pill
                                val dirtyCount = state.gitStatus?.dirtyPaths?.size ?: 0
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { vm.openCommitSheet() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = if (dirtyCount > 0) "Commit ($dirtyCount)" else "Commit & Push",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // 5. Sub-agents Pill
                                val subAgents = active?.messages?.flatMap { it.subAgents }?.distinctBy { it.id }.orEmpty()
                                val runningSubAgents = subAgents.count { it.state == "running" }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (runningSubAgents > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (runningSubAgents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { vm.setSubAgentSheet(true) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Groups,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (runningSubAgents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (runningSubAgents > 0) "Sub-agents ($runningSubAgents)" else if (subAgents.isNotEmpty()) "Sub-agents (${subAgents.size})" else "Sub-agents",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (runningSubAgents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // 6. Options Pill
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { vm.setMoreOptionsSheet(true) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreHoriz,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Options",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            },
            bottomBar = {
                if (!isImeVisible) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == MainTab.CHATS,
                            onClick = { currentTab = MainTab.CHATS },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_agent),
                                    contentDescription = "Agent",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Agent") }
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.FILES,
                            onClick = { currentTab = MainTab.FILES },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_obsidian),
                                    contentDescription = "Vault Files",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Vault") }
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.GIT,
                            onClick = { currentTab = MainTab.GIT },
                            icon = { Icon(Icons.Default.Difference, contentDescription = "Git Changes") },
                            label = { Text("Git") }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                if (snapshot.protocolVersion != 0 && snapshot.protocolVersion != 1) Text("Protocol mismatch: phone supports 1, desktop reports ${snapshot.protocolVersion}", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(8.dp))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) }
                when (currentTab) {
                    MainTab.CHATS -> ChatScreen(active, vm, display, Modifier.weight(1f))
                    MainTab.FILES -> FilesScreen(vm.repo, onOpenFile = vm::openFile, Modifier.weight(1f))
                    MainTab.GIT -> GitScreen(vm.repo, onOpenFile = vm::openFile, Modifier.weight(1f))
                }
            }
        }
    }

    if (showNew) AgentPickerDialog("New chat", snapshot.providers, onDismiss = { showNew = false }) { provider, model, effort -> vm.create(provider, model, effort); showNew = false }
    if (showModel && active != null) AgentPickerDialog("Agent, model and effort", snapshot.providers, initialProvider = active.providerId, initialModel = active.model, initialEffort = active.effort, onDismiss = { showModel = false }) { provider, model, effort -> vm.switchModel(provider, model.orEmpty(), effort); showModel = false }

    if (state.showCommitSheet) {
        val dirtyCount = state.gitStatus?.dirtyPaths?.size ?: 0
        AlertDialog(
            onDismissRequest = { if (!state.isCommitting) vm.closeCommitSheet() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Commit Vault Changes")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dirtyCount > 0) "$dirtyCount changed file(s)" else "Working tree clean",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable(enabled = !state.isGeneratingCommitMessage && !state.isCommitting) {
                                vm.generateCommitMessage()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (state.isGeneratingCommitMessage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Generating...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        if (state.commitMessage.isBlank()) "Generate with AI" else "Regenerate AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.commitMessage,
                        onValueChange = { vm.updateCommitMessage(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text("Commit message") },
                        placeholder = { Text("feat: summary of changes...") },
                        maxLines = 5,
                        shape = RoundedCornerShape(8.dp)
                    )
                    if (state.isCommitting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Committing changes...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.commitAndPush(push = true) },
                    enabled = !state.isCommitting && state.commitMessage.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Commit & Push")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { vm.commitAndPush(push = false) },
                        enabled = !state.isCommitting && state.commitMessage.isNotBlank()
                    ) {
                        Text("Commit Only")
                    }
                    TextButton(
                        onClick = { vm.closeCommitSheet() },
                        enabled = !state.isCommitting
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (state.showQuotaSheet) {
        val quota = state.snapshot.quota
        AlertDialog(
            onDismissRequest = { vm.setQuotaSheet(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Context & Usage")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = active?.providerId?.replaceFirstChar { it.uppercase() } ?: "Provider",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = active?.model ?: "Default model",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Source: ${quota?.source ?: "Local"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Context Window", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${quota?.contextRemainingPercent ?: 100}% free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val usedTokens = quota?.contextTokens ?: 0
                            val limitTokens = quota?.contextLimit ?: 200000
                            val usedPercent = ((usedTokens.toFloat() / limitTokens.toFloat()) * 100f).coerceIn(0f, 100f)

                            LinearProgressIndicator(
                                progress = { (usedPercent / 100f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (usedPercent > 90f) MaterialTheme.colorScheme.error else if (usedPercent > 70f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatTokens(usedTokens)} used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatTokens(limitTokens)} capacity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Allowance Limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                            if (quota?.fiveHourRemainingPercent != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("5-Hour Quota", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${quota.fiveHourRemainingPercent}% left",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                quota.fiveHourReset?.let {
                                    Text(
                                        text = "Resets in $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (quota?.weeklyRemainingPercent != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Weekly Quota", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${quota.weeklyRemainingPercent}% left",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                quota.weeklyReset?.let {
                                    Text(
                                        text = "Resets on $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (quota?.creditsBalanceUsd != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gateway Balance", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "$${String.format("%.2f", quota.creditsBalanceUsd)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (quota?.awaitingNativeReading == true) {
                                Text(
                                    text = "Provider limits have not been queried yet. Tap refresh below.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.refreshQuota(force = true) },
                    enabled = !state.isRefreshingQuota,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.isRefreshingQuota) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (state.isRefreshingQuota) "Refreshing..." else "Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setQuotaSheet(false) }) {
                    Text("Close")
                }
            }
        )
    }

    if (state.showSubAgentSheet) {
        val subAgents = active?.messages?.flatMap { it.subAgents }?.distinctBy { it.id }.orEmpty()
        var selectedFilter by remember { mutableStateOf("all") }
        val filteredSubAgents = remember(subAgents, selectedFilter) {
            when (selectedFilter) {
                "running" -> subAgents.filter { it.state == "running" }
                "done" -> subAgents.filter { it.state == "completed" }
                "failed" -> subAgents.filter { it.state == "failed" }
                else -> subAgents
            }
        }
        val runningCount = subAgents.count { it.state == "running" }
        val completedCount = subAgents.count { it.state == "completed" }
        val failedCount = subAgents.count { it.state == "failed" }

        Dialog(
            onDismissRequest = { vm.setSubAgentSheet(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Sub-agents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${subAgents.size} total · $runningCount active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { vm.setSubAgentSheet(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "all",
                            onClick = { selectedFilter = "all" },
                            label = { Text("All (${subAgents.size})") }
                        )
                        FilterChip(
                            selected = selectedFilter == "running",
                            onClick = { selectedFilter = "running" },
                            label = { Text("Running ($runningCount)") }
                        )
                        FilterChip(
                            selected = selectedFilter == "done",
                            onClick = { selectedFilter = "done" },
                            label = { Text("Done ($completedCount)") }
                        )
                        if (failedCount > 0) {
                            FilterChip(
                                selected = selectedFilter == "failed",
                                onClick = { selectedFilter = "failed" },
                                label = { Text("Failed ($failedCount)") }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (filteredSubAgents.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Groups, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Text(
                                    text = if (subAgents.isEmpty()) "No sub-agents have run in this conversation yet." else "No sub-agents match this filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredSubAgents, key = { it.id }) { agent ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(agent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                agent.model?.let {
                                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                val duration = formatDuration(agent.startedAt, agent.endedAt)
                                                if (duration.isNotEmpty()) {
                                                    Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = when (agent.state) {
                                                        "running" -> MaterialTheme.colorScheme.primaryContainer
                                                        "failed" -> MaterialTheme.colorScheme.errorContainer
                                                        else -> MaterialTheme.colorScheme.surface
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (agent.state == "running") {
                                                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                        Text(
                                                            text = agent.state.replaceFirstChar { it.uppercase() },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = when (agent.state) {
                                                                "running" -> MaterialTheme.colorScheme.primary
                                                                "failed" -> MaterialTheme.colorScheme.error
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            agent.toolCalls?.let {
                                                Text("$it tool calls", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            agent.tokens?.let {
                                                Text("${formatTokens(it)} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        if (agent.mutatedPaths.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                        Text("Modified ${agent.mutatedPaths.size} file(s)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                                    }
                                                    Text("This sub-agent modified files in the workspace. Tap to inspect:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                    agent.mutatedPaths.forEach { path ->
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.surface,
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                            modifier = Modifier.fillMaxWidth().clickable {
                                                                vm.openFile(path)
                                                                vm.setSubAgentSheet(false)
                                                            }
                                                        ) {
                                                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.Description, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                                Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        agent.task?.let { task ->
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("Task:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(task, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                                                }
                                            }
                                        }

                                        agent.result?.let { result ->
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("Result:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(result, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                                                }
                                            }
                                        }

                                        agent.error?.let { err ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                                            }
                                        }

                                        if (agent.timeline.isNotEmpty()) {
                                            var timelineOpen by remember { mutableStateOf(false) }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().clickable { timelineOpen = !timelineOpen },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Tool Activity (${agent.timeline.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                        Icon(if (timelineOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                                                    }
                                                    if (timelineOpen) {
                                                        agent.timeline.take(15).forEach { act ->
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Icon(Icons.Default.Build, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                Text("${act.title}: ${act.detail ?: ""}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (agent.result != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(agent.result))
                                                    Toast.makeText(context, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Copy Result", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showMoreOptionsSheet) {
        AlertDialog(
            onDismissRequest = { vm.setMoreOptionsSheet(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Conversation Options")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Reasoning Effort", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("high", "medium", "low").forEach { effortLevel ->
                            val isSelected = (active?.effort ?: "").lowercase() == effortLevel
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    active?.let { conv ->
                                        vm.command("switch-model", providerId = conv.providerId, model = conv.model, effort = effortLevel)
                                    }
                                },
                                label = { Text(effortLevel.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                    Text("Conversation Actions", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedButton(
                        onClick = {
                            renameText = active?.title ?: ""
                            renameDialogOpen = true
                            vm.setMoreOptionsSheet(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rename Conversation")
                    }

                    OutlinedButton(
                        onClick = {
                            clearConfirmOpen = true
                            vm.setMoreOptionsSheet(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Chat Messages")
                    }

                    OutlinedButton(
                        onClick = {
                            deleteConfirmOpen = true
                            vm.setMoreOptionsSheet(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Conversation")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                    Text("Export & Sharing", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedButton(
                        onClick = {
                            val md = vm.generateTranscriptMarkdown()
                            clipboardManager.setText(AnnotatedString(md))
                            Toast.makeText(context, "Transcript copied as Markdown", Toast.LENGTH_SHORT).show()
                            vm.setMoreOptionsSheet(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Markdown Transcript")
                    }

                    OutlinedButton(
                        onClick = {
                            val uri = "obsidian://open?vault=XyourP-Main-Vault&chat=${active?.id ?: ""}"
                            clipboardManager.setText(AnnotatedString(uri))
                            Toast.makeText(context, "Obsidian URI copied to clipboard", Toast.LENGTH_SHORT).show()
                            vm.setMoreOptionsSheet(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Obsidian Link")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setMoreOptionsSheet(false) }) {
                    Text("Close")
                }
            }
        )
    }

    if (renameDialogOpen) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        active?.let { vm.renameConversation(it.id, renameText.trim()) }
                        renameDialogOpen = false
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (clearConfirmOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmOpen = false },
            title = { Text("Clear Chat Messages?") },
            text = { Text("This will clear all messages in this conversation. The conversation itself will be kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        active?.let { vm.clearConversation(it.id) }
                        clearConfirmOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Messages")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete Conversation?") },
            text = { Text("Are you sure you want to delete this entire conversation? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        active?.let { vm.deleteConversation(it.id) }
                        deleteConfirmOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    state.openFile?.let { file ->
        FullScreenFileViewer(
            file = file,
            onClose = vm::closeFile,
            vm = vm,
            canGoBack = state.fileHistory.isNotEmpty(),
            onBack = vm::closeFile,
            onDismissAll = vm::dismissFileViewer
        )
    }
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
    var showDialog by remember { mutableStateOf(false) }
    val available = power.available(connected)
    val pct = power.percent ?: 0
    val isAc = power.powerSource == "ac" || !power.hasBattery
    val isCharging = power.chargingState == "charging"

    val icon = when {
        !available -> Icons.AutoMirrored.Filled.BatteryUnknown
        isAc -> if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Power
        pct >= 95 -> Icons.Default.BatteryFull
        pct >= 80 -> Icons.Default.Battery6Bar
        pct >= 65 -> Icons.Default.Battery5Bar
        pct >= 50 -> Icons.Default.Battery4Bar
        pct >= 35 -> Icons.Default.Battery3Bar
        pct >= 20 -> Icons.Default.Battery2Bar
        pct >= 10 -> Icons.Default.Battery1Bar
        pct > 0 -> Icons.Default.Battery0Bar
        else -> Icons.Default.BatteryAlert
    }

    val label = when {
        !available -> "Laptop battery unavailable"
        !power.hasBattery -> "Laptop on AC power"
        power.chargingState == "charging" -> "Laptop ${power.percent}% charging"
        power.chargingState == "full" -> "Laptop ${power.percent}% full"
        else -> "Laptop ${power.percent}% battery"
    }

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (available && power.hasBattery) "${power.percent}%" else if (available) "AC" else "--",
                style = MaterialTheme.typography.labelMedium,
                color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Laptop Battery & Power",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!available) {
                        Text(
                            text = if (!connected) "Disconnected from host laptop." else "Power status not reported yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Power source", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (isAc) "Connected to AC" else "Battery", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        if (power.hasBattery && power.percent != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Level", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${power.percent}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("State", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val stateLabel = when (power.chargingState) {
                                "charging" -> "Charging"
                                "discharging" -> "Discharging"
                                "full" -> "Fully charged"
                                else -> if (isAc) "AC connected" else "Unknown"
                            }
                            Text(stateLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Estimated Runtime", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        val estimateText = when {
                            isAc && power.chargingState == "charging" -> "Plugged in and charging."
                            isAc && power.chargingState == "full" -> "Plugged in and fully charged (100%)."
                            isAc -> "Running on AC adapter."
                            power.secondsRemaining != null && power.secondsRemaining > 0 -> {
                                val hrs = power.secondsRemaining / 3600
                                val mins = (power.secondsRemaining % 3600) / 60
                                if (hrs > 0) "${hrs}h ${mins}m remaining (approx. ${power.secondsRemaining / 60} minutes)"
                                else "${mins}m remaining"
                            }
                            power.percent != null -> "Calculating estimate... Windows is sampling active discharge rate."
                            else -> "Runtime estimate not available."
                        }

                        Text(
                            text = estimateText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (power.observedAtMs > 0) {
                            val agoSec = ((System.currentTimeMillis() - power.observedAtMs) / 1000).coerceAtLeast(0)
                            Text(
                                text = "Last updated ${agoSec}s ago",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun formatTokens(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
        else -> tokens.toString()
    }
}

private fun formatDuration(start: Long, end: Long?): String {
    if (start <= 0) return ""
    val durationMs = (end ?: System.currentTimeMillis()) - start
    if (durationMs < 0) return ""
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

