package com.sermonstudio.app.data.io

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.sermonstudio.app.data.db.SermonEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val ext: String, val mime: String) {
    TXT("txt", "text/plain"),
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}

/** Writes sermons to TXT / PDF / DOCX in the app cache and builds a share intent for the file. */
@Singleton
class ExportManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun export(s: SermonEntity, format: ExportFormat): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safe = s.title.ifBlank { "sermon" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifBlank { "sermon" }
        val file = File(dir, "$safe.${format.ext}")
        val blocks = MarkupText.toBlocks(s.body)
        when (format) {
            ExportFormat.TXT -> file.writeText(MarkupText.toPlainText(s.body))
            ExportFormat.PDF -> writePdf(file, blocks)
            ExportFormat.DOCX -> writeDocx(file, blocks)
        }
        file
    }

    fun shareIntent(file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            file.name,
        )
    }

    // ---- PDF -------------------------------------------------------------------------------------
    private fun writePdf(file: File, blocks: List<Block>) {
        val pageW = 595; val pageH = 842; val margin = 48
        val width = pageW - 2 * margin
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var y = margin.toFloat()

        fun paint(type: BlockType) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt()
            when (type) {
                BlockType.H1 -> { textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
                BlockType.H2 -> { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
                BlockType.NOTE -> { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) }
                else -> { textSize = 12f }
            }
        }

        for (b in blocks) {
            val indent = if (b.type == BlockType.BULLET) 16 else 0
            val text = if (b.type == BlockType.BULLET) "• ${b.text}" else b.text
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(b.type), width - indent)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            val gap = if (b.type == BlockType.H1 || b.type == BlockType.H2) 10f else 4f
            if (y + layout.height > pageH - margin && y > margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                y = margin.toFloat()
            }
            val c = page.canvas
            c.save(); c.translate((margin + indent).toFloat(), y); layout.draw(c); c.restore()
            y += layout.height + gap
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    // ---- DOCX (minimal OOXML package, no external library) ----------------------------------------
    private fun writeDocx(file: File, blocks: List<Block>) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val body = buildString {
            for (b in blocks) {
                val (sz, bold, ital, left) = when (b.type) {
                    BlockType.H1 -> Style(40, true, false, 0)
                    BlockType.H2 -> Style(30, true, false, 0)
                    BlockType.BULLET -> Style(24, false, false, 720)
                    BlockType.NOTE -> Style(22, false, true, 360)
                    else -> Style(24, false, false, 0)
                }
                val text = if (b.type == BlockType.BULLET) "• ${b.text}" else b.text
                append("<w:p><w:pPr><w:spacing w:after=\"80\"/>")
                if (left > 0) append("<w:ind w:left=\"$left\"/>")
                append("</w:pPr><w:r><w:rPr>")
                if (bold) append("<w:b/>")
                if (ital) append("<w:i/>")
                append("<w:sz w:val=\"$sz\"/></w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>")
            }
        }
        val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            put("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="$ns"><w:body>$body</w:body></w:document>""")
        }
    }

    private data class Style(val size: Int, val bold: Boolean, val italic: Boolean, val left: Int)
}
