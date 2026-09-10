package de.xyourp.antigravitymobile.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.xyourp.antigravitymobile.data.AppRepository
import de.xyourp.antigravitymobile.net.GitStatusDetails
import de.xyourp.antigravitymobile.net.GitStatusEntry
import de.xyourp.antigravitymobile.net.VaultFileResponse
import de.xyourp.antigravitymobile.ui.chat.markdown.MarkdownInlineText
import de.xyourp.antigravitymobile.ui.chat.markdown.MarkdownText
import kotlinx.coroutines.launch

private enum class WorktreeFilter {
    NOTES, ALL, OTHER
}

private enum class DiffViewMode {
    PROSE_DIFF, RENDERED_NOTE, RAW_PATCH
}

@Composable
fun GitScreen(
    repo: AppRepository,
    onOpenFile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<GitStatusDetails?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var filter by remember { mutableStateOf(WorktreeFilter.NOTES) }

    var selectedDiffPath by remember { mutableStateOf<String?>(null) }
    var diffContent by remember { mutableStateOf<String?>(null) }
    var diffLoading by remember { mutableStateOf(false) }
    var showDiffModal by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching { repo.api.gitStatus() }
                .onSuccess { response ->
                    loading = false
                    if (response.ok) {
                        status = response.status
                        val entries = response.status?.entries ?: emptyList()
                        val hasNotes = entries.any { isMarkdownPath(it.path) }
                        if (!hasNotes && filter == WorktreeFilter.NOTES && entries.isNotEmpty()) {
                            filter = WorktreeFilter.ALL
                        }
                    } else {
                        error = response.error ?: "Failed to read git status"
                    }
                }
                .onFailure {
                    loading = false
                    error = it.message ?: "Network error"
                }
        }
    }

    fun openDiff(path: String?) {
        selectedDiffPath = path
        showDiffModal = true
        diffLoading = true
        diffContent = null
        scope.launch {
            runCatching { repo.api.gitDiff(path) }
                .onSuccess {
                    diffLoading = false
                    diffContent = it.diff.ifBlank { "No changes in working copy." }
                }
                .onFailure {
                    diffLoading = false
                    diffContent = "Error loading diff: ${it.message}"
                }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val entries = status?.entries ?: emptyList()
    val noteEntries = remember(entries) { entries.filter { isMarkdownPath(it.path) } }
    val otherEntries = remember(entries) { entries.filter { !isMarkdownPath(it.path) } }

    val displayedEntries = when (filter) {
        WorktreeFilter.NOTES -> noteEntries
        WorktreeFilter.ALL -> entries
        WorktreeFilter.OTHER -> otherEntries
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = status?.branch ?: "Vault Worktree",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = ::refresh, enabled = !loading) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh git status")
                            }
                        }
                    }

                    status?.let { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (s.aheadCount > 0) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("+${s.aheadCount} unpushed") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                            if (s.isCommitted) {
                                Text(
                                    text = "Worktree clean",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                val notesText = if (noteEntries.isNotEmpty()) "${noteEntries.size} notes" else ""
                                val otherText = if (otherEntries.isNotEmpty()) "${otherEntries.size} other" else ""
                                val summary = listOf(notesText, otherText).filter { it.isNotBlank() }.joinToString(" · ")
                                Text(
                                    text = "$summary modified",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!s.isCommitted) {
                            OutlinedButton(
                                onClick = { openDiff(null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Difference, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Inspect full vault diff")
                            }
                        }
                    }
                }
            }

            // Segmented filter row
            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == WorktreeFilter.NOTES,
                        onClick = { filter = WorktreeFilter.NOTES },
                        label = { Text("Notes (${noteEntries.size})") },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = filter == WorktreeFilter.ALL,
                        onClick = { filter = WorktreeFilter.ALL },
                        label = { Text("All (${entries.size})") }
                    )
                    if (otherEntries.isNotEmpty()) {
                        FilterChip(
                            selected = filter == WorktreeFilter.OTHER,
                            onClick = { filter = WorktreeFilter.OTHER },
                            label = { Text("Other (${otherEntries.size})") }
                        )
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Changed items list
            if (displayedEntries.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedEntries, key = { it.path }) { entry ->
                        if (isMarkdownPath(entry.path)) {
                            MarkdownNoteItem(entry = entry, onClick = { openDiff(entry.path) })
                        } else {
                            GenericFileItem(entry = entry, onClick = { openDiff(entry.path) })
                        }
                    }
                }
            } else if (!loading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (filter == WorktreeFilter.NOTES && noteEntries.isEmpty() && otherEntries.isNotEmpty())
                                "No modified Markdown notes"
                            else
                                "Vault worktree clean",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (filter == WorktreeFilter.NOTES && otherEntries.isNotEmpty())
                                "Only non-markdown files have uncommitted changes."
                            else
                                "All notes and files are committed and up to date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showDiffModal) {
            FullScreenDiffViewer(
                target = selectedDiffPath,
                diff = diffContent,
                loading = diffLoading,
                repo = repo,
                onClose = { showDiffModal = false },
                onOpenFile = onOpenFile
            )
        }
    }
}

private fun isMarkdownPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".md") || lower.endsWith(".markdown")
}

@Composable
private fun MarkdownNoteItem(entry: GitStatusEntry, onClick: () -> Unit) {
    val rawName = entry.path.substringAfterLast("/")
    val noteTitle = rawName.removeSuffix(".md").removeSuffix(".markdown")
    val dirPath = entry.path.substringBeforeLast("/", "").takeIf { it.isNotBlank() } ?: "Vault Root"

    val (badgeText, badgeColor, containerColor) = when (entry.status) {
        "modified" -> Triple("Modified Note", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        "untracked" -> Triple("New Note", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
        "staged" -> Triple("Staged Note", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        "deleted" -> Triple("Deleted Note", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        else -> Triple("Renamed Note", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = noteTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dirPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = containerColor
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Inspect changes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun GenericFileItem(entry: GitStatusEntry, onClick: () -> Unit) {
    val fileName = entry.path.substringAfterLast("/")
    val dirPath = entry.path.substringBeforeLast("/", "").takeIf { it.isNotBlank() }

    val statusLabel = when (entry.status) {
        "modified" -> "M"
        "untracked" -> "?"
        "staged" -> "S"
        "deleted" -> "D"
        else -> "R"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dirPath != null) {
                    Text(
                        text = dirPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenDiffViewer(
    target: String?,
    diff: String?,
    loading: Boolean,
    repo: AppRepository,
    onClose: () -> Unit,
    onOpenFile: ((String) -> Unit)? = null
) {
    val isMarkdown = target != null && isMarkdownPath(target)
    var viewMode by remember { mutableStateOf(if (isMarkdown) DiffViewMode.PROSE_DIFF else DiffViewMode.RAW_PATCH) }

    var renderedNoteContent by remember { mutableStateOf<String?>(null) }
    var noteLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(target, viewMode) {
        if (viewMode == DiffViewMode.RENDERED_NOTE && target != null && renderedNoteContent == null) {
            noteLoading = true
            scope.launch {
                runCatching { repo.api.vaultFile(target) }
                    .onSuccess {
                        noteLoading = false
                        renderedNoteContent = it.content
                    }
                    .onFailure {
                        noteLoading = false
                        renderedNoteContent = "Failed to load note: ${it.message}"
                    }
            }
        }
    }

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
                                Text(
                                    text = target?.substringAfterLast("/")?.removeSuffix(".md") ?: "Full Vault Changes",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = target ?: "Working tree changes against HEAD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, contentDescription = "Close diff")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // View Mode Switcher for Markdown files
                    if (isMarkdown) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = viewMode == DiffViewMode.PROSE_DIFF,
                                onClick = { viewMode = DiffViewMode.PROSE_DIFF },
                                label = { Text("Prose Diff") }
                            )
                            FilterChip(
                                selected = viewMode == DiffViewMode.RENDERED_NOTE,
                                onClick = { viewMode = DiffViewMode.RENDERED_NOTE },
                                label = { Text("Rendered Note") }
                            )
                            FilterChip(
                                selected = viewMode == DiffViewMode.RAW_PATCH,
                                onClick = { viewMode = DiffViewMode.RAW_PATCH },
                                label = { Text("Raw Patch") }
                            )
                        }
                    }

                    if (loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (viewMode == DiffViewMode.RENDERED_NOTE) {
                        if (noteLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                item {
                                    MarkdownText(renderedNoteContent.orEmpty(), null, onOpenFile = onOpenFile)
                                }
                            }
                        }
                    } else if (viewMode == DiffViewMode.PROSE_DIFF && isMarkdown) {
                        val lines = remember(diff) { diff?.lines() ?: emptyList() }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(lines) { line ->
                                MarkdownDiffLine(line, onOpenFile = onOpenFile)
                            }
                        }
                    } else {
                        // Raw Patch mode with monospace font and horizontal scroll
                        val scrollState = rememberScrollState()
                        val lines = remember(diff) { diff?.lines() ?: emptyList() }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(scrollState)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            items(lines) { line ->
                                RawCodeDiffLine(line)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownDiffLine(line: String, onOpenFile: ((String) -> Unit)? = null) {
    if (line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ")) {
        return
    }

    if (line.startsWith("@@")) {
        val headingIndex = line.indexOf("@@", 2)
        val headingText = if (headingIndex != -1) line.substring(headingIndex + 2).trim() else ""
        if (headingText.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = headingText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        return
    }

    val isAdded = line.startsWith("+")
    val isDeleted = line.startsWith("-")
    val rawText = if (isAdded || isDeleted) line.substring(1) else line

    if (rawText.isBlank()) {
        Spacer(Modifier.height(4.dp))
        return
    }

    val bg = when {
        isAdded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    val stripeColor = when {
        isAdded -> MaterialTheme.colorScheme.primary
        isDeleted -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }

    val fg = when {
        isAdded -> MaterialTheme.colorScheme.onSurface
        isDeleted -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val isHeading = rawText.trimStart().startsWith("#")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isAdded || isDeleted) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(stripeColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(6.dp))
        }

        MarkdownInlineText(
            text = rawText,
            style = if (isHeading) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = fg,
            textDecoration = if (isDeleted) TextDecoration.LineThrough else null,
            onOpenFile = onOpenFile,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RawCodeDiffLine(line: String) {
    val isAdded = line.startsWith("+") && !line.startsWith("+++")
    val isDeleted = line.startsWith("-") && !line.startsWith("---")
    val isHunk = line.startsWith("@@")

    val bg = when {
        isAdded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isHunk -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    val fg = when {
        isAdded -> MaterialTheme.colorScheme.primary
        isDeleted -> MaterialTheme.colorScheme.error
        isHunk -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = line.ifEmpty { " " },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = fg
        )
    }
}
