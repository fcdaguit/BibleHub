package com.sermonstudio.app.data.bible

/**
 * A reference to a chapter, a verse or a verse range.
 * verseStart == null  -> whole chapter.   verseEnd == null -> single verse.
 */
data class BibleRef(
    val book: Int,
    val chapter: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
) {
    val lastVerse: Int? get() = verseEnd ?: verseStart

    fun display(): String {
        val name = BibleBooks.name(book)
        return when {
            verseStart == null -> "$name $chapter"
            verseEnd == null || verseEnd == verseStart -> "$name $chapter:$verseStart"
            else -> "$name $chapter:$verseStart-$verseEnd"
        }
    }

    /** True if verse [v] of the same chapter lies inside this reference. */
    fun covers(book: Int, chapter: Int, v: Int): Boolean {
        if (book != this.book || chapter != this.chapter) return false
        val s = verseStart ?: return true
        return v in s..(verseEnd ?: s)
    }
}

/** True if the two references share at least one verse (a whole chapter overlaps anything in it). */
fun BibleRef.overlaps(o: BibleRef): Boolean {
    if (book != o.book || chapter != o.chapter) return false
    val a1 = verseStart ?: return true
    val b1 = o.verseStart ?: return true
    return a1 <= (o.lastVerse ?: b1) && b1 <= (lastVerse ?: a1)
}

/** A reference found in free text, with its character range. */
data class RefMatch(val range: IntRange, val ref: BibleRef, val text: String)

/**
 * Finds Bible references such as "John 3:16", "Psalm 23", "Matthew 5:1-12", "1 Cor 13:4".
 * Pure Kotlin (no Android dependencies) so it is unit-testable and fast enough to run on every keystroke.
 */
object ReferenceParser {

    private val pattern: Regex by lazy {
        // Longest aliases first so "1 John" wins over "John" and "Song of Solomon" over "Song".
        val aliases = BibleBooks.all
            .flatMap { listOf(it.name) + it.aliases }
            .distinct()
            .sortedByDescending { it.length }
            .joinToString("|") { alias -> aliasPattern(alias) }
        Regex(
            "(?<![\\p{L}\\d])($aliases)\\.?\\s*(\\d{1,3})" +
                "(?:\\s*[:.]\\s*(\\d{1,3})(?:\\s*[-–—]\\s*(\\d{1,3}))?)?(?![\\d])",
            RegexOption.IGNORE_CASE,
        )
    }

    /**
     * Escapes each word separately (Regex.escape wraps text in \Q..\E, so spaces cannot be
     * replaced afterwards). The gap after a leading number is optional ("1John"), other gaps are not.
     */
    internal fun aliasPattern(alias: String): String {
        val parts = alias.split(" ")
        val sb = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (i > 0) sb.append(if (i == 1 && parts[0].all { it.isDigit() }) "\\s?" else "\\s+")
            sb.append(Regex.escape(part))
        }
        return sb.toString()
    }

    /** All references in [text], in order. */
    fun findAll(text: String): List<RefMatch> {
        val out = ArrayList<RefMatch>()
        for (m in pattern.findAll(text)) {
            val bookText = m.groupValues[1]
            // "job 2" or "is 5" in ordinary prose: require a capital letter or a leading digit.
            if (!(bookText[0].isUpperCase() || bookText[0].isDigit())) continue
            val book = BibleBooks.find(bookText) ?: continue
            val chapter = m.groupValues[2].toIntOrNull() ?: continue
            val v1 = m.groupValues[3].toIntOrNull()
            val v2 = m.groupValues[4].toIntOrNull()
            // Short abbreviations ("Is", "Am", "Na", "Re") only count when a verse is given.
            if (v1 == null && bookText.replace(" ", "").length <= 3 && BibleBooks.find(bookText)?.name != bookText) continue
            if (chapter == 0 || (v1 != null && v1 == 0)) continue
            val end = if (v2 != null && v1 != null && v2 > v1) v2 else null
            out += RefMatch(m.range, BibleRef(book, chapter, v1, end), m.value)
        }
        return out
    }

    /** Parses a string that is *entirely* one reference (e.g. a search query); null otherwise. */
    fun parseSingle(text: String): BibleRef? {
        val t = text.trim()
        val m = findAll(t).firstOrNull() ?: return null
        return if (m.range.first == 0 && m.range.last == t.length - 1) m.ref else null
    }
}
