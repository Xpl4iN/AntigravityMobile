package de.xyourp.antigravitymobile.ui.files

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.xyourp.antigravitymobile.data.AppRepository
import de.xyourp.antigravitymobile.net.VaultFileEntry
import de.xyourp.antigravitymobile.net.VaultFilesResponse
import de.xyourp.antigravitymobile.net.VaultFolderEntry
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    repo: AppRepository,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var currentFolder by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<VaultFilesResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(folder: String, query: String = "") {
        scope.launch {
            loading = true
            error = null
            runCatching { repo.api.listFiles(folder, query) }
                .onSuccess {
                    loading = false
                    if (it.ok) {
                        data = it
                    } else {
                        error = it.error ?: "Could not load files"
                    }
                }
                .onFailure {
                    loading = false
                    error = it.message ?: "Failed to reach bridge"
                }
        }
    }

    LaunchedEffect(currentFolder, searchQuery) {
        load(currentFolder, searchQuery)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search vault notes and files...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                } else {
                    IconButton(onClick = { load(currentFolder, searchQuery) }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Breadcrumb bar when not in search mode
        if (searchQuery.isBlank()) {
            BreadcrumbBar(
                currentFolder = currentFolder,
                onNavigate = { currentFolder = it }
            )
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val folders = data?.folders ?: emptyList()
            val files = data?.files ?: emptyList()

            if (folders.isEmpty() && files.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching files" else "Folder is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Parent folder back button
                    if (searchQuery.isBlank() && currentFolder.isNotBlank()) {
                        item {
                            ParentFolderItem(onClick = {
                                val parent = data?.parent ?: ""
                                currentFolder = parent
                            })
                        }
                    }

                    // Folders
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Folders (${folders.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(folders, key = { it.path }) { folder ->
                            FolderItem(folder = folder, onClick = { currentFolder = folder.path })
                        }
                    }

                    // Files
                    if (files.isNotEmpty()) {
                        item {
                            Text(
                                text = if (searchQuery.isNotBlank()) "Found Files (${files.size})" else "Files (${files.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(files, key = { it.path }) { file ->
                            FileItem(file = file, onClick = { onOpenFile(file.path) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    currentFolder: String,
    onNavigate: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val segments = remember(currentFolder) {
        if (currentFolder.isBlank()) emptyList()
        else currentFolder.split("/")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssistChip(
            onClick = { onNavigate("") },
            label = { Text("Vault") },
            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (currentFolder.isBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        )

        var accumulated = ""
        segments.forEachIndexed { index, seg ->
            accumulated = if (accumulated.isEmpty()) seg else "$accumulated/$seg"
            val targetPath = accumulated
            val isLast = index == segments.lastIndex

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AssistChip(
                onClick = { onNavigate(targetPath) },
                label = { Text(seg) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ParentFolderItem(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Go up", tint = MaterialTheme.colorScheme.primary)
            Text(
                text = ".. (parent folder)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderItem(folder: VaultFolderEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FileItem(file: VaultFileEntry, onClick: () -> Unit) {
    val isMarkdown = file.extension.lowercase() in setOf("md", "markdown")
    val icon = if (isMarkdown) Icons.Default.Description else Icons.AutoMirrored.Filled.InsertDriveFile
    val sizeFormatted = formatFileSize(file.size)

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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isMarkdown) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.path} · $sizeFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
