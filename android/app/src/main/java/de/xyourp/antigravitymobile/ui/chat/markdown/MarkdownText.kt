package de.xyourp.antigravitymobile.ui.chat.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel
import de.xyourp.antigravitymobile.ui.chat.ImagePreviewDialog

/* Block-level Markdown rendering: tables, code, images, callouts, tasks, Mermaid, math and choice cards. */
@Composable fun MarkdownText(
    markdown: String,
    vm: ChatViewModel? = null,
    onOpenFile: ((String) -> Unit)? = null
) {
    val effectiveOpenFile: ((String) -> Unit)? = onOpenFile ?: { path: String -> vm?.openFile(path) }
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    MarkdownInlineText(
                        text = block.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (22 - block.level * 2).coerceAtLeast(14).sp,
                        lineHeight = (26 - block.level * 2).coerceAtLeast(18).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenFile = effectiveOpenFile,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Bullet -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        MarkdownInlineText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            onOpenFile = effectiveOpenFile
                        )
                    }
                }
                is MarkdownBlock.Ordered -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "${block.index}. ", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        MarkdownInlineText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            onOpenFile = effectiveOpenFile
                        )
                    }
                }
                is MarkdownBlock.Code -> {
                    CodeBlockView(block)
                }
                is MarkdownBlock.Table -> {
                    TableView(block, effectiveOpenFile)
                }
                is MarkdownBlock.Image -> {
                    MarkdownImageView(block, vm)
                }
                is MarkdownBlock.Paragraph -> {
                    MarkdownInlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenFile = effectiveOpenFile
                    )
                }
                is MarkdownBlock.Callout -> {
                    CalloutView(block, effectiveOpenFile)
                }
                is MarkdownBlock.Mermaid -> {
                    MermaidView(block)
                }
                is MarkdownBlock.TaskItem -> {
                    TaskItemView(block, effectiveOpenFile)
                }
                is MarkdownBlock.MathBlock -> {
                    MathBlockView(block)
                }
                is MarkdownBlock.ChoiceCard -> {
                    ChoiceCardView(block, vm)
                }
            }
        }
    }
}

@Composable private fun TableView(
    table: MarkdownBlock.Table,
    onOpenFile: ((String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val columnCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return

    val columnWidths = remember(table) {
        (0 until columnCount).map { col ->
            val headerLen = table.headers.getOrNull(col)?.length ?: 0
            val maxRowLen = table.rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            val maxLen = maxOf(headerLen, maxRowLen)
            (maxLen * 9 + 28).coerceIn(80, 260).dp
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
            Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in 0 until columnCount) {
                    val headerText = table.headers.getOrNull(col).orEmpty()
                    val align = table.alignments.getOrNull(col) ?: TableAlignment.LEFT
                    val textAlign = when (align) {
                        TableAlignment.CENTER -> TextAlign.Center
                        TableAlignment.RIGHT -> TextAlign.End
                        TableAlignment.LEFT -> TextAlign.Start
                    }
                    MarkdownInlineText(
                        text = headerText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = textAlign,
                        onOpenFile = onOpenFile,
                        modifier = Modifier.width(columnWidths[col]).padding(horizontal = 8.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            table.rows.forEachIndexed { rowIndex, row ->
                val rowBg = if (rowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f) else Color.Transparent
                Row(
                    modifier = Modifier.background(rowBg).padding(vertical = 7.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until columnCount) {
                        val cellText = row.getOrNull(col).orEmpty()
                        val align = table.alignments.getOrNull(col) ?: TableAlignment.LEFT
                        val textAlign = when (align) {
                            TableAlignment.CENTER -> TextAlign.Center
                            TableAlignment.RIGHT -> TextAlign.End
                            TableAlignment.LEFT -> TextAlign.Start
                        }
                        MarkdownInlineText(
                            text = cellText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = textAlign,
                            onOpenFile = onOpenFile,
                            modifier = Modifier.width(columnWidths[col]).padding(horizontal = 8.dp)
                        )
                    }
                }
                if (rowIndex < table.rows.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable private fun CodeBlockView(block: MarkdownBlock.Code) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("code", block.code))
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.horizontalScroll(scrollState)
            )
        }
    }
}

@Composable private fun MarkdownImageView(block: MarkdownBlock.Image, vm: ChatViewModel?) {
    val context = LocalContext.current
    var fullPreview by remember { mutableStateOf(false) }
    val resolvedUrl = remember(block.url, vm) {
        val u = block.url
        when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("/api/") && vm != null -> vm.attachmentUrl(u.removePrefix("/api/attachment?path="))
            u.contains("XyourP-Main-Vault", ignoreCase = true) && vm != null -> {
                val rel = u.substringAfter("XyourP-Main-Vault/").replace('\\', '/')
                vm.attachmentUrl(rel)
            }
            vm != null && !u.startsWith("http") -> vm.attachmentUrl(u)
            else -> u
        }
    }
    val request = remember(resolvedUrl, vm?.deviceToken) {
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .apply { vm?.deviceToken?.takeIf { it.isNotBlank() }?.let { token -> addHeader("x-device-token", token) } }
            .crossfade(true)
            .build()
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { fullPreview = true }
    ) {
        AsyncImage(
            model = request,
            contentDescription = block.alt.ifBlank { "Markdown Image" },
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            contentScale = ContentScale.Fit
        )
    }

    if (fullPreview) {
        ImagePreviewDialog(resolvedUrl, vm?.deviceToken.orEmpty()) { fullPreview = false }
    }
}

@Composable private fun CalloutView(
    block: MarkdownBlock.Callout,
    onOpenFile: ((String) -> Unit)? = null
) {
    val (calloutColor, icon, defaultTitle) = when (block.type) {
        "NOTE", "INFO" -> Triple(Color(0xFF58A6FF), Icons.Default.Info, "Note")
        "TIP", "HINT" -> Triple(Color(0xFF8BE1C0), Icons.Default.Lightbulb, "Tip")
        "IMPORTANT" -> Triple(Color(0xFFA371F7), Icons.Default.PriorityHigh, "Important")
        "WARNING" -> Triple(Color(0xFFD29922), Icons.Default.Warning, "Warning")
        "CAUTION", "DANGER", "ERROR" -> Triple(Color(0xFFF85149), Icons.Default.Error, "Caution")
        "ABSTRACT", "SUMMARY", "TLDR" -> Triple(Color(0xFF39C5CF), Icons.Default.MenuBook, "Summary")
        "SUCCESS", "CHECK", "DONE" -> Triple(Color(0xFF3FB950), Icons.Default.CheckCircle, "Success")
        "QUESTION", "HELP", "FAQ" -> Triple(Color(0xFFE3B341), Icons.Default.HelpOutline, "Question")
        else -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Info, block.type.lowercase().replaceFirstChar { it.uppercase() })
    }
    val title = block.title.ifBlank { defaultTitle }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = calloutColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, calloutColor.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(calloutColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = calloutColor
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = calloutColor
                    )
                }
                if (block.content.isNotBlank()) {
                    MarkdownInlineText(
                        text = block.content,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenFile = onOpenFile
                    )
                }
            }
        }
    }
}

@Composable private fun TaskItemView(
    block: MarkdownBlock.TaskItem,
    onOpenFile: ((String) -> Unit)? = null
) {
    var isChecked by remember(block.text, block.checked) { mutableStateOf(block.checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.size(32.dp).padding(end = 4.dp)
        )
        MarkdownInlineText(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isChecked) TextDecoration.LineThrough else null,
            onOpenFile = onOpenFile,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable private fun MermaidView(block: MarkdownBlock.Mermaid) {
    var showCode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "MERMAID DIAGRAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = !showCode,
                        onClick = { showCode = false },
                        label = { Text("Diagram", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = showCode,
                        onClick = { showCode = true },
                        label = { Text("Code", style = MaterialTheme.typography.labelSmall) }
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("mermaid", block.code))
                            Toast.makeText(context, "Mermaid code copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showCode) {
                Text(
                    text = block.code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.horizontalScroll(scrollState)
                )
            } else {
                val escapedCode = remember(block.code) {
                    block.code
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                }
                val html = remember(escapedCode) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                      <style>
                        body {
                          margin: 0;
                          padding: 12px;
                          background-color: #1e2224;
                          color: #e0e0e0;
                          display: flex;
                          justify-content: center;
                          align-items: center;
                          min-height: 80px;
                          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        }
                        .mermaid {
                          width: 100%;
                          text-align: center;
                        }
                        .mermaid svg {
                          max-width: 100%;
                          height: auto;
                        }
                      </style>
                      <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
                      <script>
                        mermaid.initialize({
                          startOnLoad: true,
                          theme: 'dark',
                          themeVariables: {
                            darkMode: true,
                            background: '#1e2224',
                            primaryColor: '#8BE1C0',
                            primaryTextColor: '#1a1d1e',
                            primaryBorderColor: '#8BE1C0',
                            lineColor: '#8BE1C0',
                            secondaryColor: '#2d333b',
                            tertiaryColor: '#22272e'
                          }
                        });
                      </script>
                    </head>
                    <body>
                      <div class="mermaid">
                        $escapedCode
                      </div>
                    </body>
                    </html>
                    """.trimIndent()
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            setBackgroundColor(android.graphics.Color.parseColor("#1e2224"))
                            loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                    }
                )
            }
        }
    }
}

@Composable private fun MathBlockView(block: MarkdownBlock.MathBlock) {
    val context = LocalContext.current
    var showRaw by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KATEX MATH",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = !showRaw,
                        onClick = { showRaw = false },
                        label = { Text("Rendered", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = showRaw,
                        onClick = { showRaw = true },
                        label = { Text("LaTeX", style = MaterialTheme.typography.labelSmall) }
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("latex", block.math))
                            Toast.makeText(context, "LaTeX copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy LaTeX",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showRaw) {
                Text(
                    text = block.math,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            } else {
                val jsonEscaped = remember(block.math) {
                    "\"" + block.math.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
                }
                val html = remember(jsonEscaped) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
                      <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
                      <style>
                        body {
                          margin: 0;
                          padding: 10px;
                          background-color: #1e2224;
                          color: #8BE1C0;
                          display: flex;
                          justify-content: center;
                          align-items: center;
                          font-size: 16px;
                        }
                        .katex-display { margin: 0; }
                      </style>
                    </head>
                    <body>
                      <div id="math"></div>
                      <script>
                        try {
                          katex.render($jsonEscaped, document.getElementById('math'), { displayMode: true, throwOnError: false });
                        } catch(e) {
                          document.getElementById('math').innerText = $jsonEscaped;
                        }
                      </script>
                    </body>
                    </html>
                    """.trimIndent()
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 200.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(android.graphics.Color.parseColor("#1e2224"))
                            loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
                    }
                )
            }
        }
    }
}

@Composable private fun ChoiceCardView(
    block: MarkdownBlock.ChoiceCard,
    vm: ChatViewModel?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            block.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                block.options.forEach { option ->
                    Surface(
                        onClick = { vm?.setComposerText(option.value) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { vm?.send(option.value, null) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send directly",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
