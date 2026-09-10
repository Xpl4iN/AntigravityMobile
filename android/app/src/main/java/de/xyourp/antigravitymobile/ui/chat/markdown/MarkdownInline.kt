package de.xyourp.antigravitymobile.ui.chat.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/* Inline Markdown (emphasis, code, math, wiki and web links) and link routing. */
fun resolveAndOpenLink(link: String, context: Context, onOpenFile: ((String) -> Unit)?) {
    val clean = link.trim().removePrefix("<").removeSuffix(">")
    when {
        clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
            }
        }
        clean.startsWith("obsidian://", ignoreCase = true) -> {
            val fileParam = Uri.parse(clean).getQueryParameter("file")
            if (!fileParam.isNullOrBlank() && onOpenFile != null) {
                onOpenFile(fileParam)
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Obsidian not available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        clean.contains("XyourP-Main-Vault", ignoreCase = true) -> {
            val vaultRelative = clean
                .substringAfter("XyourP-Main-Vault/")
                .substringAfter("XyourP-Main-Vault\\")
                .replace('\\', '/')
            if (onOpenFile != null) {
                onOpenFile(vaultRelative)
            }
        }
        clean.startsWith("file://", ignoreCase = true) -> {
            val path = clean.removePrefix("file://").removePrefix("/")
            if (path.contains("XyourP-Main-Vault", ignoreCase = true)) {
                val vaultRelative = path
                    .substringAfter("XyourP-Main-Vault/")
                    .substringAfter("XyourP-Main-Vault\\")
                    .replace('\\', '/')
                onOpenFile?.invoke(vaultRelative)
            } else {
                onOpenFile?.invoke(path)
            }
        }
        else -> {
            val target = clean.removePrefix("[[").removeSuffix("]]").split("|")[0].split("#")[0].trim()
            if (target.isNotBlank()) {
                onOpenFile?.invoke(target)
            }
        }
    }
}

fun buildAnnotatedMarkdown(
    text: String,
    primaryColor: Color,
    codeBg: Color,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val regex = Regex("""(\*\*([^*]+)\*\*|\*([^*]+)\*|~~([^~]+)~~|`([^`]+)`|\$([^$\n]+)\$|\[\[([^]|]+)(?:\|([^\]]+))?\]\]|\[([^]]+)\]\(([^)]+)\)|(https?://[^\s)\]>]+))""")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) {
            builder.append(text.substring(lastIndex, match.range.first))
        }
        val bold = match.groups[2]?.value
        val italic = match.groups[3]?.value
        val strike = match.groups[4]?.value
        val code = match.groups[5]?.value
        val math = match.groups[6]?.value
        val wikiTarget = match.groups[7]?.value
        val wikiLabel = match.groups[8]?.value
        val linkText = match.groups[9]?.value
        val linkUrl = match.groups[10]?.value
        val bareUrl = match.groups[11]?.value

        when {
            bold != null -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(bold)
                builder.pop()
            }
            italic != null -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(italic)
                builder.pop()
            }
            strike != null -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                builder.append(strike)
                builder.pop()
            }
            code != null -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                builder.append(" $code ")
                builder.pop()
            }
            math != null -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic, color = primaryColor))
                builder.append(math)
                builder.pop()
            }
            wikiTarget != null -> {
                val label = wikiLabel ?: wikiTarget
                if (onLinkClick != null) {
                    val link = LinkAnnotation.Clickable(
                        tag = wikiTarget,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onLinkClick(wikiTarget) }
                    )
                    builder.pushLink(link)
                    builder.append(label)
                    builder.pop()
                } else {
                    builder.pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
                    builder.append(label)
                    builder.pop()
                }
            }
            linkText != null && linkUrl != null -> {
                if (onLinkClick != null) {
                    val link = LinkAnnotation.Clickable(
                        tag = linkUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Normal,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onLinkClick(linkUrl) }
                    )
                    builder.pushLink(link)
                    builder.append(linkText)
                    builder.pop()
                } else {
                    builder.pushStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline))
                    builder.append(linkText)
                    builder.pop()
                }
            }
            bareUrl != null -> {
                if (onLinkClick != null) {
                    val link = LinkAnnotation.Clickable(
                        tag = bareUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onLinkClick(bareUrl) }
                    )
                    builder.pushLink(link)
                    builder.append(bareUrl)
                    builder.pop()
                } else {
                    builder.pushStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline))
                    builder.append(bareUrl)
                    builder.pop()
                }
            }
            else -> builder.append(match.value)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
    return builder.toAnnotatedString()
}

@Composable fun MarkdownInlineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    fontWeight: FontWeight? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
    onOpenFile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val annotated = remember(text, primaryColor, codeBg, onOpenFile) {
        buildAnnotatedMarkdown(text, primaryColor, codeBg) { link ->
            resolveAndOpenLink(link, context, onOpenFile)
        }
    }
    var mergedStyle = style
    if (fontWeight != null) mergedStyle = mergedStyle.copy(fontWeight = fontWeight)
    if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) mergedStyle = mergedStyle.copy(fontSize = fontSize)
    if (textDecoration != null) mergedStyle = mergedStyle.copy(textDecoration = textDecoration)
    Text(
        text = annotated,
        style = mergedStyle,
        color = color,
        lineHeight = lineHeight,
        textAlign = textAlign,
        modifier = modifier
    )
}
