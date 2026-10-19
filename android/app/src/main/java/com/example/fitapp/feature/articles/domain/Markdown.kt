package com.example.fitapp.feature.articles.domain

/** A block of an article. The articles use only headings, paragraphs and lists, so that is all this understands. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class Bullet(val text: String) : MdBlock

    data class Numbered(val number: Int, val text: String) : MdBlock
}

/** A piece of a line: plain or **bold**. */
data class MdSpan(
    val text: String,
    val bold: Boolean,
)

/**
 * A small Markdown reader for the article library: `#`, `##`, `###` headings, `-` and `*` bullets, `1.` numbered items,
 * paragraphs (consecutive lines join into one) and `**bold**`. Anything else is plain text, and nothing in an article can
 * run code or open a link.
 */
object Markdown {
    private val NUMBERED = Regex("^(\\d{1,3})\\.\\s+(.*)$")

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.toString().trim())
                paragraph.clear()
            }
        }

        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("### ") -> {
                    flush()
                    blocks += MdBlock.Heading(3, line.removePrefix("### ").trim())
                }
                line.startsWith("## ") -> {
                    flush()
                    blocks += MdBlock.Heading(2, line.removePrefix("## ").trim())
                }
                line.startsWith("# ") -> {
                    flush()
                    blocks += MdBlock.Heading(1, line.removePrefix("# ").trim())
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    blocks += MdBlock.Bullet(line.substring(2).trim())
                }
                NUMBERED.matches(line) -> {
                    flush()
                    val match = NUMBERED.matchEntire(line)!!
                    blocks += MdBlock.Numbered(match.groupValues[1].toInt(), match.groupValues[2].trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }

    /** Splits a line at every `**`, alternating plain and bold. A marker left open bolds the rest of the line. */
    fun inline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var bold = false
        for (part in text.split("**")) {
            if (part.isNotEmpty()) spans += MdSpan(part, bold)
            bold = !bold
        }
        return spans
    }
}