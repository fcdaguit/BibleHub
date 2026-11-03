package com.sermonstudio.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.sermonstudio.app.data.bible.ReferenceParser

/**
 * Styles the sermon markup as you type without changing the text (so cursor offsets stay identical):
 * headings, notes, **bold**, _italic_, ==highlight==, {red}colour{/}, and Bible references,
 * which are detected on every keystroke and shown as underlined links.
 */
class MarkupTransformation(
    private val linkColor: Color,
    private val dim: Color,
) : VisualTransformation {

    private val bold = Regex("\\*\\*(.+?)\\*\\*")
    private val italic = Regex("(?<![\\w])_(.+?)_(?![\\w])")
    private val highlight = Regex("==(.+?)==")
    private val colored = Regex("\\{(red|blue|green|orange|purple)\\}(.+?)\\{/\\}")

    private fun colorOf(name: String) = when (name) {
        "red" -> Color(0xFFD32F2F)
        "blue" -> Color(0xFF1976D2)
        "green" -> Color(0xFF2E7D32)
        "orange" -> Color(0xFFEF6C00)
        else -> Color(0xFF7B1FA2)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val b = AnnotatedString.Builder(t)

        // Line styles
        var start = 0
        for (line in t.split('\n')) {
            val end = start + line.length
            when {
                line.startsWith("## ") -> { b.addStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), start, end); b.addStyle(SpanStyle(color = dim), start, start + 3) }
                line.startsWith("# ") -> { b.addStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), start, end); b.addStyle(SpanStyle(color = dim), start, start + 2) }
                line.startsWith("> ") -> { b.addStyle(SpanStyle(fontStyle = FontStyle.Italic, background = Color(0x1A808080)), start, end); b.addStyle(SpanStyle(color = dim), start, start + 2) }
                line.startsWith("- ") || line.startsWith("* ") -> b.addStyle(SpanStyle(color = dim), start, start + 2)
            }
            start = end + 1
        }

        // Inline styles; marker characters are dimmed
        for (m in bold.findAll(t)) {
            b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            dimEnds(b, m.range.first, m.range.last + 1, 2)
        }
        for (m in italic.findAll(t)) {
            b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
            dimEnds(b, m.range.first, m.range.last + 1, 1)
        }
        for (m in highlight.findAll(t)) {
            b.addStyle(SpanStyle(background = Color(0x88FFEB3B), color = Color.Black), m.range.first, m.range.last + 1)
            dimEnds(b, m.range.first, m.range.last + 1, 2)
        }
        for (m in colored.findAll(t)) {
            val open = m.groupValues[1].length + 2
            b.addStyle(SpanStyle(color = colorOf(m.groupValues[1])), m.range.first + open, m.range.last + 1 - 3)
            b.addStyle(SpanStyle(color = dim), m.range.first, m.range.first + open)
            b.addStyle(SpanStyle(color = dim), m.range.last + 1 - 3, m.range.last + 1)
        }
        // Scripture references: live detection while typing
        for (m in ReferenceParser.findAll(t)) {
            b.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), m.range.first, m.range.last + 1)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun dimEnds(b: AnnotatedString.Builder, s: Int, e: Int, n: Int) {
        b.addStyle(SpanStyle(color = dim), s, s + n)
        b.addStyle(SpanStyle(color = dim), e - n, e)
    }
}
