package com.sermonstudio.app.data.io

enum class BlockType { H1, H2, BULLET, NUMBERED, NOTE, PARA }

data class Block(val type: BlockType, val text: String)

/**
 * The sermon editor stores plain text with a light markup (documented in docs/USER_MANUAL.md):
 *   # Heading        ## Subheading      - bullet      1. numbered      > note
 *   **bold**   _italic_   ==highlight==   {red}coloured text{/}
 * This object turns that text into blocks for export, and strips inline markers.
 */
object MarkupText {
    private val numbered = Regex("^\\d+[.)]\\s+")
    private val colorOpen = Regex("\\{(red|blue|green|orange|purple)\\}")
    private val italic = Regex("(?<![\\w])_(.+?)_(?![\\w])")

    fun toBlocks(body: String): List<Block> = body.lines().mapNotNull { raw ->
        val line = raw.trimEnd()
        when {
            line.isBlank() -> null
            line.startsWith("## ") -> Block(BlockType.H2, stripInline(line.removePrefix("## ")))
            line.startsWith("# ") -> Block(BlockType.H1, stripInline(line.removePrefix("# ")))
            line.startsWith("- ") || line.startsWith("* ") -> Block(BlockType.BULLET, stripInline(line.drop(2)))
            numbered.containsMatchIn(line) -> Block(BlockType.NUMBERED, stripInline(line))
            line.startsWith("> ") -> Block(BlockType.NOTE, stripInline(line.removePrefix("> ")))
            else -> Block(BlockType.PARA, stripInline(line))
        }
    }

    fun stripInline(s: String): String =
        s.replace("**", "").replace("==", "")
            .replace(colorOpen, "").replace("{/}", "")
            .replace(italic) { it.groupValues[1] }

    /** Plain-text rendering used for TXT export and for search snippets. */
    fun toPlainText(body: String): String = toBlocks(body).joinToString("\n") {
        when (it.type) {
            BlockType.H1 -> "\n${it.text.uppercase()}\n"
            BlockType.H2 -> "\n${it.text}"
            BlockType.BULLET -> "  • ${it.text}"
            BlockType.NOTE -> "  [${it.text}]"
            else -> it.text
        }
    }.trim()
}
