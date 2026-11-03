package com.sermonstudio.app.data.bible

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

enum class BibleFormat { TSV, ZEFANIA }

data class RawVerse(val book: Int, val chapter: Int, val verse: Int, val text: String)

/**
 * Streaming parsers that turn a Bible file into [RawVerse]s without loading the whole file.
 *
 * TSV:      book<TAB>chapter<TAB>verse<TAB>text   (book = 1..66 or any name/alias known to [BibleBooks];
 *           lines starting with '#' are comments)
 * Zefania:  <XMLBIBLE><BIBLEBOOK bnumber="1"><CHAPTER cnumber="1"><VERS vnumber="1">text</VERS>...
 *           (a common format for public-domain Bibles, including many non-English ones)
 */
object BibleImporter {

    fun parse(input: InputStream, format: BibleFormat): Sequence<RawVerse> = when (format) {
        BibleFormat.TSV -> tsv(input)
        BibleFormat.ZEFANIA -> zefania(input)
    }

    /** Guess the format from a file name and the first bytes. */
    fun detect(name: String?, head: String): BibleFormat = when {
        name?.endsWith(".xml", true) == true -> BibleFormat.ZEFANIA
        head.trimStart('﻿', ' ', '\n', '\r').startsWith("<") -> BibleFormat.ZEFANIA
        else -> BibleFormat.TSV
    }

    private fun tsv(input: InputStream): Sequence<RawVerse> = sequence {
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trimStart('﻿')
                if (line.isBlank() || line.startsWith("#")) continue
                val p = line.split('\t', limit = 4)
                if (p.size < 4) continue
                val book = p[0].trim().toIntOrNull() ?: BibleBooks.find(p[0])?.num ?: continue
                val chapter = p[1].trim().toIntOrNull() ?: continue
                val verse = p[2].trim().toIntOrNull() ?: continue
                val text = p[3].trim()
                if (book in 1..66 && text.isNotEmpty()) yield(RawVerse(book, chapter, verse, text))
            }
        }
    }

    private fun zefania(input: InputStream): Sequence<RawVerse> = sequence {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)   // encoding is taken from the XML declaration
        var book = 0
        var chapter = 0
        var verse = 0
        var inVerse = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.uppercase()) {
                    "BIBLEBOOK" -> {
                        book = parser.getAttributeValue(null, "bnumber")?.toIntOrNull() ?: (book + 1)
                        chapter = 0
                    }
                    "CHAPTER" -> {
                        chapter = parser.getAttributeValue(null, "cnumber")?.toIntOrNull() ?: (chapter + 1)
                        verse = 0
                    }
                    "VERS" -> {
                        verse = parser.getAttributeValue(null, "vnumber")?.toIntOrNull() ?: (verse + 1)
                        inVerse = true
                        sb.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> if (inVerse) sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name.equals("VERS", ignoreCase = true)) {
                    inVerse = false
                    val t = sb.toString().replace(Regex("\\s+"), " ").trim()
                    if (t.isNotEmpty() && book in 1..66) yield(RawVerse(book, chapter, verse, t))
                }
            }
            event = parser.next()
        }
    }
}
