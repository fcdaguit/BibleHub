package com.sermonstudio.app.data.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedDoc(val title: String, val body: String)

/** Reads TXT, DOCX and (text-based) PDF files into plain text for a new sermon outline. */
@Singleton
class ImportManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun read(uri: Uri): ImportedDoc = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val text = when {
            ext == "docx" || mime.contains("wordprocessingml") -> readDocx(uri)
            ext == "pdf" || mime == "application/pdf" -> readPdf(uri)
            else -> ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }.trim()
        require(text.isNotEmpty()) { "No readable text found (scanned PDFs need OCR, which this app does not include)." }
        val first = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("#")?.trim().orEmpty()
        val title = if (first.isNotEmpty() && first.length <= 100) first else name.substringBeforeLast('.')
        ImportedDoc(title, text)
    }

    private fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Imported sermon"

    private fun readDocx(uri: Uri): String {
        ctx.contentResolver.openInputStream(uri)!!.use { raw ->
            ZipInputStream(raw).use { z ->
                var e = z.nextEntry
                while (e != null && e.name != "word/document.xml") e = z.nextEntry
                requireNotNull(e) { "Not a valid .docx file." }
                val xml = z.readBytes().toString(Charsets.UTF_8)
                val para = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
                val run = Regex("<w:t(?: [^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                return para.findAll(xml).joinToString("\n") { p ->
                    run.findAll(p.value).joinToString("") { unescape(it.groupValues[1]) }
                }
            }
        }
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun readPdf(uri: Uri): String {
        PDFBoxResourceLoader.init(ctx)
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            PDDocument.load(ins).use { doc -> return PDFTextStripper().getText(doc) }
        }
    }
}
