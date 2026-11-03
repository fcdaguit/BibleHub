package com.sermonprep.app

import com.sermonprep.app.data.bible.BibleFormat
import com.sermonprep.app.data.bible.BibleParsers
import com.sermonprep.app.data.bible.ParsedVerse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BibleParsersTest {

    private fun parse(content: String, format: BibleFormat): List<ParsedVerse> {
        val f = File.createTempFile("bible", ".txt")
        try {
            f.writeText(content, Charsets.UTF_8)
            val out = ArrayList<ParsedVerse>()
            BibleParsers.parse(f, format) { out += it }
            return out
        } finally {
            f.delete()
        }
    }

    @Test fun csvWithQuotedCommasQuotesAndNewlines() {
        val csv = "Book,Chapter,Verse,Text\n" +
            "Genesis,1,1,In the beginning\n" +
            "Genesis,1,2,\"And the earth, was without form; \"\"void\"\"\"\n" +
            "\"I Samuel\",2,3,\"line one\nline two\"\n"
        val v = parse(csv, BibleFormat.CSV)
        assertEquals(3, v.size)
        assertEquals(ParsedVerse(1, 1, 1, "In the beginning"), v[0])
        assertEquals("And the earth, was without form; \"void\"", v[1].text)
        assertEquals(ParsedVerse(9, 2, 3, "line one\nline two"), v[2])
    }

    @Test fun csvHandlesWindowsLineEndingsAndNoTrailingNewline() {
        val v = parse("Book,Chapter,Verse,Text\r\nJohn,3,16,For God so loved\r\nJohn,3,17,Not to condemn", BibleFormat.CSV)
        assertEquals(listOf(43 to 16, 43 to 17), v.map { it.book to it.verse })
    }

    @Test fun pipeFormatAcceptsNumbersAndNames() {
        val v = parse("43|3|16|For God so loved\nRomans|5|8|But God demonstrates", BibleFormat.PIPE)
        assertEquals(listOf(43, 45), v.map { it.book })
    }
}
