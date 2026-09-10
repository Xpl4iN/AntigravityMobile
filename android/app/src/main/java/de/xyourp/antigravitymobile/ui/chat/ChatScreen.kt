package de.xyourp.antigravitymobile.ui.chat

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.xyourp.antigravitymobile.data.DisplayPreferences
import de.xyourp.antigravitymobile.net.*
import de.xyourp.antigravitymobile.ui.chat.markdown.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingImage(
    val name: String,
    val mime: String,
    val bytes: ByteArray,
    val uri: Uri? = null
)

@Composable fun ChatScreen(conversation: RemoteConversation?, vm: ChatViewModel, display: DisplayPreferences, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val messages = conversation?.messages.orEmpty()
    val list = remember(conversation?.id) {
        LazyListState(firstVisibleItemIndex = if (messages.isNotEmpty()) messages.lastIndex else 0)
    }
    val scope = rememberCoroutineScope()
    var hasInitialScrolled by remember(conversation?.id) { mutableStateOf(false) }

    LaunchedEffect(conversation?.id, messages.size) {
        if (!hasInitialScrolled && messages.isNotEmpty()) {
            list.scrollToItem(messages.lastIndex)
            hasInitialScrolled = true
        }
    }

    val nearBottom by remember { derivedStateOf { val info = list.layoutInfo; info.totalItemsCount == 0 || info.visibleItemsInfo.lastOrNull()?.index.orEmptyIndex() >= info.totalItemsCount - 2 } }
    val fingerprint = messages.lastOrNull()?.let { "${it.id}:${it.text.length}:${it.streaming}:${it.subAgents.sumOf { agent -> agent.timeline.size }}" }
    LaunchedEffect(fingerprint) { if (display.autoScroll && nearBottom && messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (state.isOfflineCached) {
                OfflineBanner(onRetry = { vm.refresh() })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                if (conversation == null) item { EmptyConversation() }
                else items(messages, key = { it.id }) { Message(it, vm, display) }
            }
            Composer(conversation?.busy == true, vm)
        }
        if (!nearBottom && messages.isNotEmpty()) FilledTonalButton({ scope.launch { list.animateScrollToItem(messages.lastIndex) } }, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) { Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(6.dp)); Text("Latest answer") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FullScreenFileViewer(
    file: VaultFileResponse,
    onClose: () -> Unit,
    vm: ChatViewModel? = null,
    canGoBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    onDismissAll: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            if (canGoBack && onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to previous note")
                                }
                            } else {
                                IconButton(onClick = onClose) {
                                    Icon(Icons.Default.Close, contentDescription = "Close file")
                                }
                            }
                        },
                        actions = {
                            if (canGoBack && onDismissAll != null) {
                                IconButton(onClick = onDismissAll) {
                                    Icon(Icons.Default.Close, contentDescription = "Close all")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    item {
                        MarkdownText(file.content, vm, onOpenFile = { path -> vm?.openFile(path) })
                    }
                }
            }
        }
    }
}

private fun Int?.orEmptyIndex() = this ?: -1

@Composable private fun EmptyConversation() {
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text("Start with your vault", style = MaterialTheme.typography.headlineSmall)
        Text("Ask, attach a note, or delegate research.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Prefix OAC puts on the message a crew agent's result reaches its master in (ADR 0012). */
private const val WORKER_REPORT_MARKER = "[OAC worker report]"

@Composable private fun Message(message: DisplayMessage, vm: ChatViewModel, display: DisplayPreferences) {
    // Plugin notes (orchestration receipts, provider switches) and crew reports
    // are not the user's words or the agent's answer, so they get their own look.
    if (message.role == "system") { PluginNote(message.text); return }
    if (message.role == "user" && message.text.startsWith(WORKER_REPORT_MARKER)) { CrewReport(message.text, vm); return }
    val user = message.role == "user"
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = if (user) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, shape = MaterialTheme.shapes.large, modifier = if (user) Modifier.fillMaxWidth(.88f) else Modifier.fillMaxWidth()) {
            Column(Modifier.padding(if (user) 14.dp else 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (user) "You" else "Agent",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (user) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    if (message.streaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (message.text.isBlank()) "Thinking..." else "Generating...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                val imageRefs = remember(message.context) {
                    message.context.filter { ref ->
                        ref.mime?.startsWith("image/") == true ||
                        listOf(".png", ".jpg", ".jpeg", ".webp", ".gif").any { ref.path.endsWith(it, ignoreCase = true) }
                    }
                }
                if (imageRefs.isNotEmpty()) {
                    ImageAttachmentStrip(imageRefs, vm) { previewImageUrl = it }
                }

                if (message.text.isNotBlank()) {
                    MarkdownText(message.text, vm)
                } else if (!user && message.streaming && message.tools.isEmpty()) {
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                val cited = remember(message.text, message.context) { extractVaultReferences(message.text, message.context) }
                if (cited.isNotEmpty()) ReferenceStrip(cited, vm)
                if (message.tools.isNotEmpty() && display.toolDisplay != "hidden") ToolActivity(message.tools, display.toolDisplay)
                message.subAgents.forEach { SubAgent(it, display) { guidance -> vm.steer(guidance, false, null) } }
                message.approvals.filter { it.decision == "pending" }.forEach { approval -> Approval(approval, vm) }
                message.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    previewImageUrl?.let { url ->
        ImagePreviewDialog(url, vm.deviceToken) { previewImageUrl = null }
    }
}

@Composable private fun PluginNote(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, null, Modifier.size(14.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A crew agent's result as its master received it. The first line names the
 * agent and the outcome; the rest is its final reply, collapsed by default
 * because a busy crew produces several of these per master turn.
 */
@Composable private fun CrewReport(text: String, vm: ChatViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val reports = remember(text) { text.split("\n\n$WORKER_REPORT_MARKER").mapIndexed { i, part -> if (i == 0) part else WORKER_REPORT_MARKER + part } }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Groups, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(
                    if (reports.size == 1) "Crew report" else "${reports.size} crew reports",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand", Modifier.size(18.dp))
            }
            reports.forEach { report ->
                val headline = report.removePrefix(WORKER_REPORT_MARKER).lineSequence().first().trim()
                Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (expanded) {
                    val body = report.lineSequence().drop(1).joinToString("\n").trim()
                    if (body.isNotBlank()) MarkdownText(body, vm)
                }
            }
        }
    }
}

@Composable private fun ImageAttachmentStrip(refs: List<ContextReference>, vm: ChatViewModel, onExpand: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        refs.forEach { ref ->
            val url = vm.attachmentUrl(ref.path)
            val request = remember(url, vm.deviceToken) {
                ImageRequest.Builder(context)
                    .data(url)
                    .apply { if (vm.deviceToken.isNotBlank()) addHeader("x-device-token", vm.deviceToken) }
                    .crossfade(true)
                    .build()
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().clickable { onExpand(url) }
            ) {
                Column {
                    AsyncImage(
                        model = request,
                        contentDescription = ref.name ?: "Image attachment",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 280.dp),
                        contentScale = ContentScale.Fit
                    )
                    ref.name?.takeIf { it.isNotBlank() }?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun ImagePreviewDialog(url: String, token: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.92f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                val request = remember(url, token) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .apply { if (token.isNotBlank()) addHeader("x-device-token", token) }
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = "Full image preview",
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable private fun ReferenceStrip(paths: List<String>, vm: ChatViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Vault references", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        paths.forEach { path -> TextButton({ vm.openFile(path) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Icon(Icons.Default.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    }
}

@Composable private fun ToolActivity(tools: List<ToolCard>, mode: String) {
    var expanded by remember { mutableStateOf(false) }
    val running = tools.count { it.state == "running" }; val failed = tools.count { it.state == "error" }
    Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (running > 0) Icons.Default.Sync else if (failed > 0) Icons.Default.Warning else Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(if (running > 0) "$running active · ${tools.size} tools" else "${tools.size} tools used", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
            if (expanded) tools.forEach { tool -> ToolRow(tool, mode == "full") }
        }
    }
}

@Composable private fun ToolRow(tool: ToolCard, raw: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (tool.state == "success") "✓" else if (tool.state == "error") "!" else "•", color = if (tool.state == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Column { Text(tool.name, style = MaterialTheme.typography.labelLarge); tool.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
        if (raw) { tool.input?.let { RawBlock("Input", it.toString()) }; tool.output?.let { RawBlock("Output", it.toString()) }; if (tool.updates.isNotEmpty()) RawBlock("Progress", tool.updates.joinToString("\n")) }
    }
}

@Composable private fun SubAgent(agent: SubAgentCard, display: DisplayPreferences, steer: (String) -> Unit) {
    var expanded by remember(agent.id) { mutableStateOf(display.autoExpandSubAgents) }
    val complete = agent.state == "completed"; val elapsed = formatElapsed((agent.endedAt ?: System.currentTimeMillis()) - agent.startedAt)
    Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (complete) Icons.Default.CheckCircle else if (agent.state == "failed") Icons.Default.Error else Icons.Default.AccountTree, null, tint = if (agent.state == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(agent.name, fontWeight = FontWeight.SemiBold); Text("${agent.state.replaceFirstChar { it.uppercase() }} · $elapsed${agent.toolCalls?.let { " · $it tools" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
            if (!expanded) agent.result?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) } ?: agent.detail?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            if (expanded) {
                agent.result?.let { Section("Result") { Text(it, lineHeight = 21.sp) } }
                agent.error?.let { Section("Error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                agent.task?.let { Disclosure("Task") { Text(it, style = MaterialTheme.typography.bodySmall) } }
                val reasoning = agent.timeline.filter { it.kind == "thinking" }
                if (reasoning.isNotEmpty()) Disclosure("Reasoning · ${reasoning.size}", display.autoExpandReasoning) { Timeline(reasoning, display.toolDisplay == "full") }
                val tools = agent.timeline.filter { it.kind == "tool" }
                if (tools.isNotEmpty() && display.toolDisplay != "hidden") Disclosure("Tools used · ${tools.size}") { Timeline(tools, display.toolDisplay == "full") }
                val activity = agent.timeline.filter { it.kind != "thinking" && it.kind != "tool" }
                if (activity.isNotEmpty()) Disclosure("Activity · ${activity.size}") { Timeline(activity, display.toolDisplay == "full") }
                if (agent.state == "running") TextButton({ steer("Please steer sub-agent \"${agent.name}\" (${agent.id}) with this guidance: ") }) { Text("Steer via parent") }
            }
        }
    }
}

@Composable private fun Section(title: String, body: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); body() } }
@Composable private fun Disclosure(title: String, initiallyOpen: Boolean = false, body: @Composable () -> Unit) { var open by remember { mutableStateOf(initiallyOpen) }; Column { TextButton({ open = !open }, contentPadding = PaddingValues(0.dp)) { Text(title, Modifier.weight(1f)); Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }; if (open) body() } }
@Composable private fun Timeline(items: List<SubAgentActivity>, details: Boolean) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { items.forEach { item -> Column { Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium); if (details) item.detail?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun RawBlock(title: String, value: String) { Column { Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, maxLines = 12, overflow = TextOverflow.Ellipsis) } }
private fun formatElapsed(milliseconds: Long): String { val seconds = (milliseconds.coerceAtLeast(0) / 1000); return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s" }

@Composable private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Offline - Browsing cached snapshot",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("Retry", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun Approval(approval: ApprovalCard, vm: ChatViewModel) {
    val isExternal = approval.kind.equals("external", ignoreCase = true) ||
            approval.kind.equals("external_access", ignoreCase = true) ||
            approval.kind.equals("network", ignoreCase = true)
    var showFullDiff by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExternal) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (isExternal) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExternal) Icons.Default.Security else Icons.Default.Gavel,
                    contentDescription = null,
                    tint = if (isExternal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isExternal) "External Access Approval" else "Approval Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isExternal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            if (isExternal) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Agent requested permission to access network or resources outside the vault.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            approval.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (approval.path.isNotBlank()) {
                Text(
                    text = approval.path,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (approval.paths.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Target paths:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    approval.paths.forEach { p ->
                        Text(p, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (approval.diff.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF181A1B),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val diffLines = remember(approval.diff) { approval.diff.lines() }
                    val displayLines = if (!showFullDiff && diffLines.size > 12) diffLines.take(12) else diffLines

                    Column(modifier = Modifier.padding(8.dp)) {
                        displayLines.forEach { line ->
                            val (lineColor, lineBg) = when {
                                line.startsWith("+") -> Pair(Color(0xFF8BE1C0), Color(0x1F8BE1C0))
                                line.startsWith("-") -> Pair(Color(0xFFFF6B6B), Color(0x1FFF6B6B))
                                line.startsWith("@@") -> Pair(Color(0xFF61AFEF), Color.Transparent)
                                else -> Pair(Color(0xFFD4D4D4), Color.Transparent)
                            }
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = lineColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(lineBg)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (diffLines.size > 12) {
                            TextButton(
                                onClick = { showFullDiff = !showFullDiff },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(if (showFullDiff) "Collapse diff" else "Show all ${diffLines.size} lines")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { vm.approve(approval.id, true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = { vm.approve(approval.id, false) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reject")
                }
            }
        }
    }
}

private fun extractVaultReferences(text: String, context: List<ContextReference>): List<String> {
    val isImage = { path: String -> listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".bmp").any { path.endsWith(it, ignoreCase = true) } }
    val wiki = Regex("\\[\\[([^]|#]+)").findAll(text).map { it.groupValues[1] }
    val files = Regex("\\[[^]]+]\\(file:///C:/XyourP/XyourP-Main-Vault/([^)]+)\\)", RegexOption.IGNORE_CASE).findAll(text).map { java.net.URLDecoder.decode(it.groupValues[1], Charsets.UTF_8.name()).replace('\\', '/') }
    return (wiki + files + context.asSequence().map { it.path }).filterNot { isImage(it) }.distinct().toList()
}

@Composable private fun Composer(busy: Boolean, vm: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    val state by vm.state.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var contextPath by remember { mutableStateOf("") }
    var showContext by remember { mutableStateOf(false) }
    var pendingImages by remember { mutableStateOf<List<PendingImage>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(state.composerText) {
        if (state.composerText.isNotBlank()) {
            text = state.composerText
            vm.clearComposerText()
        }
    }

    LaunchedEffect(Unit) {
        if (state.skills.isEmpty()) {
            vm.refreshSkills()
        }
    }

    fun pasteImageFromClipboard() {
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(context, "No image found in clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            val newPending = mutableListOf<PendingImage>()
            var errorMessage: String? = null

            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i) ?: continue
                val uri = item.uri
                val text = item.text?.toString()?.trim() ?: ""
                val html = item.htmlText ?: ""

                if (uri != null) {
                    try {
                        val mime = context.contentResolver.getType(uri) ?: "image/png"
                        var bytes = try {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (_: Exception) {
                            null
                        }
                        if (bytes == null && uri.scheme == "file" && uri.path != null) {
                            val f = java.io.File(uri.path!!)
                            if (f.exists() && f.canRead()) {
                                bytes = f.readBytes()
                            }
                        }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                            val name = "clipboard-${System.currentTimeMillis()}-$i.$ext"
                            newPending.add(PendingImage(name, mime, bytes, uri))
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to read image"
                    }
                } else if (text.startsWith("data:image/", ignoreCase = true)) {
                    try {
                        val commaIdx = text.indexOf(',')
                        if (commaIdx != -1) {
                            val header = text.substring(0, commaIdx)
                            val b64 = text.substring(commaIdx + 1)
                            val mime = header.substringAfter("data:").substringBefore(";")
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            if (bytes.isNotEmpty()) {
                                val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                                val name = "clipboard-${System.currentTimeMillis()}-$i.$ext"
                                newPending.add(PendingImage(name, mime, bytes, null))
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to decode base64 image"
                    }
                } else if (html.contains("data:image/", ignoreCase = true)) {
                    try {
                        val match = Regex("""data:(image\/[a-zA-Z0-9\+\-\.]+);base64,([A-Za-z0-9+/=]+)""").find(html)
                        if (match != null) {
                            val mime = match.groupValues[1]
                            val b64 = match.groupValues[2]
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            if (bytes.isNotEmpty()) {
                                val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                                val name = "clipboard-${System.currentTimeMillis()}-$i.$ext"
                                newPending.add(PendingImage(name, mime, bytes, null))
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to decode HTML image"
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (newPending.isNotEmpty()) {
                    pendingImages = pendingImages + newPending
                    val label = if (newPending.size == 1) "Image pasted from clipboard" else "${newPending.size} images pasted"
                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = if (errorMessage != null) "Clipboard image error: $errorMessage" else "No image found in clipboard"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val newPending = mutableListOf<PendingImage>()
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val mime = context.contentResolver.getType(uri) ?: "image/png"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                            val name = "photo-${System.currentTimeMillis()}-$index.$ext"
                            newPending.add(PendingImage(name, mime, bytes, uri))
                        }
                    } catch (_: Exception) {
                    }
                }
                if (newPending.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        pendingImages = pendingImages + newPending
                    }
                }
            }
        }
    }

    fun submit(steerImmediate: Boolean? = null, enqueueOnly: Boolean = false) {
        val msgText = text.trim()
        val cPath = contextPath.trim().ifBlank { null }
        if (msgText.isEmpty() && pendingImages.isEmpty() && cPath == null) return

        scope.launch {
            isUploading = true
            val uploadedRefs = mutableListOf<ContextReference>()
            for (img in pendingImages) {
                val ref = vm.uploadAttachment(img.name, img.mime, img.bytes)
                if (ref != null) {
                    uploadedRefs.add(ref)
                }
            }
            isUploading = false
            pendingImages = emptyList()

            val finalText = if (msgText.isNotBlank()) msgText else if (uploadedRefs.isNotEmpty()) "Attached image." else ""

            when {
                steerImmediate == true -> vm.steer(finalText, true, cPath, uploadedRefs)
                steerImmediate == false -> vm.steer(finalText, false, cPath, uploadedRefs)
                enqueueOnly || busy -> vm.queue(finalText, cPath, uploadedRefs)
                else -> vm.send(finalText, cPath, uploadedRefs)
            }
            text = ""
        }
    }

    var hasClipboardImage by remember { mutableStateOf(false) }
    DisposableEffect(clipboard) {
        fun update() {
            hasClipboardImage = try {
                val desc = clipboard?.primaryClipDescription
                val hasMime = desc?.hasMimeType("image/*") == true
                if (hasMime) {
                    true
                } else {
                    val clip = clipboard?.primaryClip
                    clip != null && (0 until clip.itemCount).any { i ->
                        val item = clip.getItemAt(i)
                        item?.uri != null ||
                        item?.text?.startsWith("data:image/", ignoreCase = true) == true ||
                        item?.htmlText?.contains("data:image/", ignoreCase = true) == true
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
        update()
        val listener = ClipboardManager.OnPrimaryClipChangedListener { update() }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboard?.removePrimaryClipChangedListener(listener)
        }
    }

    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val queuedList = vm.active()?.queued.orEmpty()
            if (queuedList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Task Queue (${queuedList.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!busy) {
                                FilledTonalButton(
                                    onClick = { vm.startQueue() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Run next",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            TextButton(
                                onClick = { vm.clearQueue() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    "Clear all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    queuedList.forEachIndexed { idx, item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Queued #${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (idx > 0) {
                                            IconButton(
                                                onClick = { vm.promoteQueue(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowUpward,
                                                    contentDescription = "Move to top",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        FilledTonalButton(
                                            onClick = { vm.pushQueue(idx) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RocketLaunch,
                                                null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Push now",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                text = item.text
                                                vm.dequeue(idx)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                "Edit",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = { vm.dequeue(idx) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Remove",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.context.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        for (ctxRef in item.context) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = ctxRef.name?.ifBlank { null } ?: ctxRef.path.substringAfterLast('/'),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (text.startsWith("/")) {
                val query = text.removePrefix("/").trim()
                val filteredSkills = remember(state.skills, query) {
                    if (query.isBlank()) state.skills
                    else state.skills.filter {
                        it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                    }
                }
                if (filteredSkills.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredSkills, key = { it.name }) { skill ->
                            Surface(
                                onClick = { text = "/${skill.name} " },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column {
                                        Text(
                                            text = "/${skill.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (skill.description.isNotBlank()) {
                                            Text(
                                                text = skill.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                modifier = Modifier.widthIn(max = 180.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (hasClipboardImage && pendingImages.isEmpty()) {
                Surface(
                    onClick = { pasteImageFromClipboard() },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Paste image from clipboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            if (pendingImages.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pendingImages) { img ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(modifier = Modifier.size(64.dp)) {
                                AsyncImage(
                                    model = img.uri ?: img.bytes,
                                    contentDescription = img.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { pendingImages = pendingImages.filterNot { it === img } },
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (showContext) {
                OutlinedTextField(
                    value = contextPath,
                    onValueChange = { contextPath = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    label = { Text("Vault note or file path") },
                    singleLine = true
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = if (busy) "Queue next message..." else "Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        minLines = 1,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showContext = !showContext },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "Attach vault note",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (showContext || contextPath.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Choose image from gallery",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { pasteImageFromClipboard() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Paste image from clipboard",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isUploading) {
                                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                if (busy) {
                                    IconButton(
                                        onClick = vm::cancel,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(
                                        onClick = { submit(steerImmediate = true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, "Steer now", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (!busy && (text.isNotBlank() || pendingImages.isNotEmpty() || contextPath.isNotBlank())) {
                                    IconButton(
                                        onClick = { submit(enqueueOnly = true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlaylistAdd,
                                            contentDescription = "Add to queue",
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                FilledIconButton(
                                    onClick = { submit() },
                                    enabled = text.isNotBlank() || pendingImages.isNotEmpty() || contextPath.isNotBlank(),
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = if (busy) "Queue" else "Send",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
