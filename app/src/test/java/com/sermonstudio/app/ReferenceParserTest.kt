package com.sermonstudio.app

import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.bible.overlaps
import com.sermonstudio.app.data.search.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceParserTest {
    @Test fun singleVerse() = assertEquals(BibleRef(43, 3, 16), ReferenceParser.parseSingle("John 3:16"))

    @Test fun range() = assertEquals(BibleRef(40, 5, 1, 12), ReferenceParser.parseSingle("Matthew 5:1-12"))

    @Test fun wholeChapter() = assertEquals(BibleRef(19, 23), ReferenceParser.parseSingle("Psalm 23"))

    @Test fun numberedBooks() {
        assertEquals(BibleRef(62, 4, 8), ReferenceParser.parseSingle("1 John 4:8"))
        assertEquals(BibleRef(46, 13, 4), ReferenceParser.parseSingle("1 Cor 13:4"))
    }

    @Test fun tagalog() = assertEquals(BibleRef(43, 3, 16), ReferenceParser.parseSingle("Juan 3:16"))

    @Test fun proseIsNotAReference() {
        assertTrue(ReferenceParser.findAll("he told job 2 people").isEmpty())
        assertTrue(ReferenceParser.findAll("this is 5 things").isEmpty())
    }

    @Test fun findsSeveralInOneParagraph() {
        val t = "Compare Romans 8:28 with Genesis 50:20 today."
        assertEquals(2, ReferenceParser.findAll(t).size)
    }

    @Test fun notAReference() = assertNull(ReferenceParser.parseSingle("faith that moves mountains"))

    @Test fun overlap() {
        assertTrue(BibleRef(43, 3, 16).overlaps(BibleRef(43, 3)))
        assertTrue(!BibleRef(43, 3, 16).overlaps(BibleRef(43, 4)))
    }

    @Test fun textTokens() {
        assertTrue(Text.tokens("The Healing of the blind").containsAll(listOf("heal", "blind")))
    }
}
