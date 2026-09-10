package de.xyourp.antigravitymobile.ui.chat.markdown

/*
 * The Markdown subset the chat, the file viewer and the Git screen render:
 * the block model and the line-based parser that produces it.
 */
sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Ordered(val index: String, val text: String) : MarkdownBlock()
    data class Code(val language: String?, val code: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val alignments: List<TableAlignment>, val rows: List<List<String>>) : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
    data class Callout(val type: String, val title: String, val content: String) : MarkdownBlock()
    data class Mermaid(val code: String) : MarkdownBlock()
    data class TaskItem(val checked: Boolean, val text: String) : MarkdownBlock()
    data class MathBlock(val math: String) : MarkdownBlock()
    data class ChoiceCard(val title: String, val prompt: String?, val options: List<ChoiceOption>) : MarkdownBlock()
}

data class ChoiceOption(val value: String, val label: String)

enum class TableAlignment { LEFT, CENTER, RIGHT }

private fun parseTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.drop(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split('|').map { it.trim() }
}

private fun isTableDelimiterRow(line: String?): Boolean {
    if (line == null) return false
    val s = line.trim()
    if (!s.contains("-") || !s.contains("|")) return false
    val cells = parseTableRow(s)
    if (cells.isEmpty()) return false
    return cells.all { cell -> cell.matches(Regex("^:?-+:?$")) }
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            val fullCode = codeLines.joinToString("\n")
            if (lang.equals("mermaid", ignoreCase = true)) {
                blocks.add(MarkdownBlock.Mermaid(fullCode))
            } else {
                blocks.add(MarkdownBlock.Code(lang, fullCode))
            }
            continue
        }

        if (trimmed.startsWith("$$")) {
            if (trimmed.endsWith("$$") && trimmed.length > 4) {
                val math = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                blocks.add(MarkdownBlock.MathBlock(math))
                i++
                continue
            } else {
                val mathLines = mutableListOf<String>()
                val firstLine = trimmed.removePrefix("$$").trim()
                if (firstLine.isNotBlank()) mathLines.add(firstLine)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val last = lines[i].trim().removeSuffix("$$").trim()
                    if (last.isNotBlank()) mathLines.add(last)
                    i++
                }
                blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                continue
            }
        }

        if (trimmed.startsWith("<details", ignoreCase = true) && trimmed.contains("oac-choice", ignoreCase = true)) {
            val cardLines = mutableListOf<String>()
            while (i < lines.size) {
                cardLines.add(lines[i])
                if (lines[i].contains("</details>", ignoreCase = true)) {
                    i++
                    break
                }
                i++
            }
            val fullHtml = cardLines.joinToString("\n")
            val titleMatch = Regex("""<summary>(.*?)</summary>""", RegexOption.DOT_MATCHES_ALL).find(fullHtml)
            val titleRaw = titleMatch?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "Question"
            val promptMatch = Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(fullHtml)
            val promptRaw = promptMatch?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim()
            val options = mutableListOf<ChoiceOption>()
            val btnRegex = Regex("""<button[^>]*data-choice="([^"]*)"[^>]*>(.*?)</button>""", RegexOption.DOT_MATCHES_ALL)
            for (btn in btnRegex.findAll(fullHtml)) {
                val valStr = btn.groupValues[1].trim()
                val labelStr = btn.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                options.add(ChoiceOption(valStr, labelStr.ifBlank { valStr }))
            }
            if (options.isEmpty()) {
                val fallbackBtnRegex = Regex("""<button[^>]*>(.*?)</button>""", RegexOption.DOT_MATCHES_ALL)
                for (btn in fallbackBtnRegex.findAll(fullHtml)) {
                    val labelStr = btn.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    if (labelStr.isNotBlank()) {
                        options.add(ChoiceOption(labelStr, labelStr))
                    }
                }
            }
            blocks.add(MarkdownBlock.ChoiceCard(titleRaw, promptRaw, options))
            continue
        }

        if (trimmed.isBlank()) {
            i++
            continue
        }

        val calloutMatch = Regex("""^>\s*\[!([a-zA-Z_-]+)\]\s*(.*)$""").find(trimmed)
        if (calloutMatch != null) {
            val type = calloutMatch.groupValues[1].uppercase()
            val titleText = calloutMatch.groupValues[2].trim()
            val calloutLines = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (next.startsWith(">")) {
                    calloutLines.add(next.removePrefix(">").trim())
                    i++
                } else if (next.isBlank()) {
                    i++
                    break
                } else {
                    calloutLines.add(next)
                    i++
                }
            }
            blocks.add(MarkdownBlock.Callout(type, titleText, calloutLines.joinToString("\n")))
            continue
        }

        val taskMatch = Regex("""^[-*]\s+\[([ xX])\]\s+(.*)$""").find(trimmed)
        if (taskMatch != null) {
            val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            val taskText = taskMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.TaskItem(checked, taskText))
            i++
            continue
        }

        if (raw.contains("|") && i + 1 < lines.size && isTableDelimiterRow(lines[i + 1])) {
            val headerCells = parseTableRow(raw)
            val alignCells = parseTableRow(lines[i + 1])
            if (headerCells.isNotEmpty() && alignCells.isNotEmpty()) {
                val alignments = alignCells.map { cell ->
                    val left = cell.startsWith(":")
                    val right = cell.endsWith(":")
                    when {
                        left && right -> TableAlignment.CENTER
                        right -> TableAlignment.RIGHT
                        else -> TableAlignment.LEFT
                    }
                }
                i += 2
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val candidate = lines[i].trim()
                    if (candidate.isBlank() || !candidate.contains("|") || candidate.startsWith("```") || candidate.startsWith("#")) {
                        break
                    }
                    tableRows.add(parseTableRow(lines[i]))
                    i++
                }
                blocks.add(MarkdownBlock.Table(headerCells, alignments, tableRows))
                continue
            }
        }

        val imgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
        if (imgMatch != null) {
            blocks.add(MarkdownBlock.Image(imgMatch.groupValues[1], imgMatch.groupValues[2]))
            i++
            continue
        }

        val hMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (hMatch != null) {
            val level = hMatch.groupValues[1].length
            val text = hMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MarkdownBlock.Bullet(trimmed.drop(2).trim()))
            i++
            continue
        }

        val oMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
        if (oMatch != null) {
            blocks.add(MarkdownBlock.Ordered(oMatch.groupValues[1], oMatch.groupValues[2]))
            i++
            continue
        }

        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }
    return blocks
}
