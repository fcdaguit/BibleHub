package com.sermonprep.app.data.bible

import com.sermonprep.app.domain.ReferenceParser
import org.json.JSONArray
import java.io.File

data class ParsedVerse(val book: Int, val chapter: Int, val verse: Int, val text: String)

enum class BibleFormat(val key: String, val label: String) {
    /** Header "Book,Chapter,Verse,Text" – the scrollmapper/bible_databases CSV layout. */
    CSV("csv", "CSV: Book,Chapter,Verse,Text"),

    /** Array of 66 books, each {"chapters": [[verse, verse, ...], ...]} – thiagobodruk/bible layout. */
    JSON_ARRAY("json-array", "JSON array of books"),

    /** Lines of book|chapter|verse|text, where book is 1..66 or a name. */
    PIPE("pipe", "Text lines: book|chapter|verse|text");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: CSV
        fun guess(fileName: String): BibleFormat = when {
            fileName.endsWith(".json", true) -> JSON_ARRAY
            fileName.endsWith(".csv", true) -> CSV
            else -> PIPE
        }
    }
}

object BibleParsers {

    /** Streams verses out of [file]; never holds the full verse list in memory (except for JSON). */
    fun parse(file: File, format: BibleFormat, onVerse: (ParsedVerse) -> Unit) {
        when (format) {
            BibleFormat.CSV -> parseCsv(file, onVerse)
            BibleFormat.PIPE -> parsePipe(file, onVerse)
            BibleFormat.JSON_ARRAY -> parseJsonArray(file, onVerse)
        }
    }

    private fun parseJsonArray(file: File, onVerse: (ParsedVerse) -> Unit) {
        val books = JSONArray(file.readText(Charsets.UTF_8).removePrefix("﻿"))
        for (b in 0 until books.length()) {
            val chapters = books.getJSONObject(b).getJSONArray("chapters")
            for (c in 0 until chapters.length()) {
                val verses = chapters.getJSONArray(c)
                for (v in 0 until verses.length()) {
                    onVerse(ParsedVerse(b + 1, c + 1, v + 1, verses.getString(v).trim()))
                }
            }
        }
    }

    /**
     * Book names are resolved by name; if a name is unknown the book number falls back to
     * the order in which distinct names first appear (all supported sources are in canonical order).
     */
    private class BookResolver {
        private val order = LinkedHashMap<String, Int>()
        fun resolve(raw: String): Int? {
            val name = raw.trim().trim('"')
            name.toIntOrNull()?.let { return it.takeIf { n -> n in 1..66 } }
            ReferenceParser.bookId(name)?.let { return it }
            val n = order.getOrPut(name) { order.size + 1 }
            return n.takeIf { it in 1..66 }
        }
    }

    private fun parsePipe(file: File, onVerse: (ParsedVerse) -> Unit) {
        val r = BookResolver()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val p = line.removePrefix("﻿").split('|', limit = 4)
                if (p.size < 4) continue
                val book = r.resolve(p[0]) ?: continue
                val c = p[1].trim().toIntOrNull() ?: continue
                val v = p[2].trim().toIntOrNull() ?: continue
                onVerse(ParsedVerse(book, c, v, p[3].trim()))
            }
        }
    }

    private fun parseCsv(file: File, onVerse: (ParsedVerse) -> Unit) {
        val r = BookResolver()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var first = true
            while (true) {
                val row = readCsvRecord(reader) ?: break
                if (first) {
                    first = false
                    if (row.getOrNull(1)?.trim()?.equals("Chapter", true) == true) continue
                }
                if (row.size < 4) continue
                val book = r.resolve(row[0]) ?: continue
                val c = row[1].trim().toIntOrNull() ?: continue
                val v = row[2].trim().toIntOrNull() ?: continue
                onVerse(ParsedVerse(book, c, v, row[3].trim()))
            }
        }
    }

    /** Minimal RFC-4180 record reader (quoted fields, doubled quotes, embedded newlines). */
    private fun readCsvRecord(reader: java.io.BufferedReader): List<String>? {
        val fields = ArrayList<String>(4)
        val sb = StringBuilder()
        var inQuotes = false
        var any = false
        while (true) {
            val ci = reader.read()
            if (ci == -1) {
                if (!any && sb.isEmpty() && fields.isEmpty()) return null
                fields += sb.toString()
                return fields
            }
            any = true
            val ch = ci.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1)
                    val next = reader.read()
                    if (next == '"'.code) sb.append('"') else {
                        inQuotes = false
                        if (next != -1) reader.reset()
                    }
                } else sb.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                ',' -> { fields += sb.toString(); sb.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    fields += sb.toString()
                    if (fields.size == 1 && fields[0].isEmpty()) { fields.clear(); sb.setLength(0); any = false; continue }
                    return fields
                }
                else -> sb.append(ch)
            }
        }
    }
}
