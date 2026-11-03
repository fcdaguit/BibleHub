package com.sermonprep.app.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedDoc(val title: String, val content: String)

/** Export to PDF / DOCX and import from TXT / DOCX / PDF, all done without extra network access. */
@Singleton
class DocumentIO @Inject constructor(@ApplicationContext private val ctx: Context) {

    // ───────────────────────── Export ─────────────────────────

    suspend fun exportPdf(title: String, content: String, dest: Uri) = withContext(Dispatchers.IO) {
        val pageW = 595   // A4 @ 72 dpi
        val pageH = 842
        val margin = 54
        val textW = pageW - 2 * margin
        val textH = pageH - 2 * margin

        val text = SpannableStringBuilder().apply {
            val heading = title.ifBlank { "Untitled sermon" }
            append(heading)
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.5f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("\n\n")
            append(content)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .build()

        val pdf = PdfDocument()
        try {
            var startLine = 0
            var pageNo = 1
            while (startLine < layout.lineCount) {
                val top = layout.getLineTop(startLine)
                var endLine = layout.getLineForVertical(top + textH)
                if (layout.getLineBottom(endLine) > top + textH) endLine--
                if (endLine < startLine) endLine = startLine
                val bottom = layout.getLineBottom(endLine)

                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                val c = page.canvas
                c.save()
                c.translate(margin.toFloat(), (margin - top).toFloat())
                c.clipRect(0f, top.toFloat(), textW.toFloat(), bottom.toFloat())
                layout.draw(c)
                c.restore()
                pdf.finishPage(page)

                startLine = endLine + 1
                pageNo++
            }
            if (pdf.pages.isEmpty()) {
                pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()))
            }
            ctx.contentResolver.openOutputStream(dest, "w")?.use { pdf.writeTo(it) }
                ?: error("Cannot write to the selected location")
        } finally {
            pdf.close()
        }
    }

    suspend fun exportDocx(title: String, content: String, dest: Uri) = withContext(Dispatchers.IO) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun para(line: String, bold: Boolean = false, sizeHalfPts: Int = 24): String {
            val rPr = "<w:rPr>" + (if (bold) "<w:b/>" else "") + "<w:sz w:val=\"$sizeHalfPts\"/></w:rPr>"
            return "<w:p><w:r>$rPr<w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r></w:p>"
        }
        val body = StringBuilder()
        body.append(para(title.ifBlank { "Untitled sermon" }, bold = true, sizeHalfPts = 36))
        body.append(para(""))
        content.split("\n").forEach { body.append(para(it.trimEnd('\r'))) }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        ctx.contentResolver.openOutputStream(dest, "w")?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                fun put(name: String, data: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                put("[Content_Types].xml", contentTypes)
                put("_rels/.rels", rels)
                put("word/document.xml", documentXml)
            }
        } ?: error("Cannot write to the selected location")
    }

    // ───────────────────────── Import ─────────────────────────

    fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Imported outline"

    suspend fun import(uri: Uri): ImportedDoc = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val title = name.substringBeforeLast('.', name)
        val lower = name.lowercase()
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val text = when {
            lower.endsWith(".docx") || mime.contains("wordprocessingml") -> readDocx(uri)
            lower.endsWith(".pdf") || mime == "application/pdf" -> readPdf(uri)
            else -> ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Cannot open the selected file")
        }
        ImportedDoc(title, text.trim())
    }

    private fun readDocx(uri: Uri): String {
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == "word/document.xml") {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        return xml
                            .replace(Regex("</w:p>"), "\n")
                            .replace(Regex("<w:tab\\s*/>"), "\t")
                            .replace(Regex("<w:br\\s*/>"), "\n")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                            .replace("&apos;", "'").replace("&amp;", "&")
                    }
                    e = zip.nextEntry
                }
            }
        }
        error("This DOCX file has no readable text (word/document.xml not found).")
    }

    /**
     * PDFs are rasterised page by page and read with ML Kit's bundled (offline) text recogniser.
     * This works for scanned and digital PDFs alike, but it is OCR: accuracy depends on print
     * quality, and very long PDFs take a while (capped at [MAX_PDF_PAGES] pages).
     */
    private suspend fun readPdf(uri: Uri): String {
        val tmp = File(ctx.cacheDir, "import.pdf")
        ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            ?: error("Cannot open the selected file")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val scale = 2.0f
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                            )
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                            sb.append(result.text).append("\n\n")
                            bmp.recycle()
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
            tmp.delete()
        }
        return sb.toString()
    }

    companion object { const val MAX_PDF_PAGES = 40 }
}
