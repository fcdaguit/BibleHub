package com.sermonprep.app

import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceParserTest {

    private fun refs(text: String) = ReferenceParser.findAll(text).map { it.ref }

    @Test fun findsTheExampleFromTheSpec() {
        val found = refs("God's love is demonstrated in John 3:16 and Romans 5:8.")
        assertEquals(listOf(BibleRef(43, 3, 16), BibleRef(45, 5, 8)), found)
    }

    @Test fun parsesVerseRanges() {
        assertEquals(listOf(BibleRef(19, 23, 1, 6)), refs("Psalm 23:1-6"))
    }

    @Test fun parsesWholeChapters() {
        assertEquals(listOf(BibleRef(46, 13)), refs("Read 1 Cor 13 tonight"))
    }

    @Test fun handlesNumberedBooksInSeveralStyles() {
        val found = refs("1 John 4:7-12, I John 4:8, 1John 3:1, First John 1:9")
        assertEquals(4, found.size)
        assertTrue(found.all { it.book == 62 })
    }

    @Test fun handlesAbbreviationsAndPeriods() {
        assertEquals(
            listOf(BibleRef(1, 1, 1), BibleRef(66, 22, 21), BibleRef(40, 5, 44)),
            refs("Gen. 1:1; Rev 22:21; Mt 5:44"),
        )
    }

    @Test fun singleChapterBooksTakeAVerseNumber() {
        assertEquals(listOf(BibleRef(65, 1, 3)), refs("Jude 3"))
        assertEquals(listOf(BibleRef(57, 1, 4)), refs("Philemon 4"))
    }

    @Test fun ignoresImpossibleChaptersAndProse() {
        assertTrue(refs("John 99:1").isEmpty())
        assertTrue(refs("Mark 5 minutes later").isEmpty())
        assertTrue(refs("Is 5 enough?").isEmpty())
    }

    @Test fun recognisesTagalogBookNames() {
        assertEquals(listOf(BibleRef(19, 23, 1)), refs("Mga Awit 23:1"))
        assertEquals(listOf(BibleRef(43, 3, 16)), refs("Juan 3:16"))
    }

    @Test fun parseRequiresTheWholeStringToBeAReference() {
        assertEquals(BibleRef(43, 3, 16), ReferenceParser.parse("John 3:16."))
        assertNull(ReferenceParser.parse("see John 3:16"))
    }

    @Test fun overlapLogic() {
        val a = BibleRef(43, 3, 16)
        assertTrue(a.overlaps(BibleRef(43, 3, 14, 18)))
        assertTrue(a.overlaps(BibleRef(43, 3)))
        assertTrue(!a.overlaps(BibleRef(43, 3, 17)))
        assertTrue(!a.overlaps(BibleRef(43, 4, 16)))
    }

    @Test fun labels() {
        assertEquals("John 3:16", BibleRef(43, 3, 16).label())
        assertEquals("Psalms 23:1-6", BibleRef(19, 23, 1, 6).label())
        assertEquals("Romans 8", BibleRef(45, 8).label())
    }
}
