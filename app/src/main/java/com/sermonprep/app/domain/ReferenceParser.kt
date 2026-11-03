package com.sermonprep.app.domain

/** A Bible reference. [verseStart] == null means "the whole chapter". */
data class BibleRef(
    val book: Int,
    val chapter: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
) {
    val lastVerse: Int? get() = verseEnd ?: verseStart

    fun label(): String {
        val base = "${BibleBooks.name(book)} $chapter"
        return when {
            verseStart == null -> base
            verseEnd == null || verseEnd == verseStart -> "$base:$verseStart"
            else -> "$base:$verseStart-$verseEnd"
        }
    }

    /** True when both refs touch at least one common verse. A whole-chapter ref overlaps anything in the chapter. */
    fun overlaps(o: BibleRef): Boolean {
        if (book != o.book || chapter != o.chapter) return false
        if (verseStart == null || o.verseStart == null) return true
        return verseStart <= (o.lastVerse ?: o.verseStart) && o.verseStart <= (lastVerse ?: verseStart)
    }
}

data class FoundRef(val ref: BibleRef, val range: IntRange, val text: String)

/**
 * Finds references such as "John 3:16", "Romans 8:28", "Psalm 23:1-6", "1 Cor 13", "I John 4:8"
 * in free text. Book names/abbreviations in English plus a few Tagalog/Cebuano names are recognised.
 *
 * Known limits: comma lists ("John 3:16, 18") and cross-chapter ranges ("John 3:16-4:2") are not parsed;
 * only the first verse range of such a reference is matched.
 */
object ReferenceParser {
    // Ambiguous short aliases that cause false positives in ordinary prose.
    private val blocked = setOf("am", "joe", "nam", "song", "pro", "jud", "ex", "sos")
    private val ordinals = mapOf(
        '1' to listOf("I", "First"),
        '2' to listOf("II", "Second"),
        '3' to listOf("III", "Third"),
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val keyToBook = HashMap<String, Int>()
    private val regex: Regex

    init {
        val variants = ArrayList<Pair<String, Int>>()
        for (book in BibleBooks.all) {
            for (alias in book.aliases) {
                val a = alias.trim()
                if (a.isEmpty() || norm(a) in blocked) continue
                variants += a to book.id
                if (a[0] in '1'..'3' && a.length > 1) {
                    val rest = a.substring(1).trim()
                    for (p in ordinals.getValue(a[0])) variants += "$p $rest" to book.id
                }
            }
        }
        for ((alias, id) in variants) keyToBook.putIfAbsent(norm(alias), id)

        val fragments = variants
            .map { it.first }
            .distinct()
            .sortedByDescending { it.length }
            .map { alias ->
                val tokens = alias.split(Regex("\\s+")).map { Regex.escape(it) }
                val firstIsNumber = alias[0].isDigit() && tokens.size > 1
                if (firstIsNumber) tokens[0] + "\\s*" + tokens.drop(1).joinToString("\\s+")
                else tokens.joinToString("\\s+")
            }

        regex = Regex(
            "(?<![\\p{L}\\p{N}])(" + fragments.joinToString("|") + ")\\.?\\s*" +
                "(\\d{1,3})(?:\\s*:\\s*(\\d{1,3})(?:\\s*[-\\u2013\\u2014]\\s*(\\d{1,3}))?)?(?!\\p{N})",
            RegexOption.IGNORE_CASE,
        )
    }

    /** Resolves a free-form book name ("1 Cor", "I John", "Revelation of John") to 1..66. */
    fun bookId(name: String): Int? = keyToBook[norm(name)]

    // "Mark 5 minutes later" is prose, not Mark 5. Only applied to chapter-only matches.
    private val proseAfter = Regex(
        "^\\s+(minutes?|hours?|days?|weeks?|months?|years?|times?|people|persons?|percent|items?)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun findAll(text: String): List<FoundRef> =
        regex.findAll(text).mapNotNull { m ->
            val bookId = keyToBook[norm(m.groupValues[1])] ?: return@mapNotNull null
            val book = BibleBooks.get(bookId) ?: return@mapNotNull null
            val number = m.groupValues[2].toInt()
            var vs = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            var ve = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()

            val chapter: Int
            if (book.chapters == 1 && vs == null) {
                // Single-chapter books are cited by verse only: "Jude 3", "Philemon 4".
                chapter = 1
                vs = number
            } else {
                chapter = number
                if (chapter < 1 || chapter > book.chapters) return@mapNotNull null
                if (vs == null && proseAfter.containsMatchIn(text.substring(m.range.last + 1).take(16))) {
                    return@mapNotNull null
                }
            }
            if (vs != null && ve != null && ve < vs) ve = null
            FoundRef(BibleRef(bookId, chapter, vs, ve), m.range, m.value)
        }.toList()

    /** Parses a string that is *entirely* one reference (trailing punctuation allowed). */
    fun parse(text: String): BibleRef? {
        val t = text.trim().trimEnd('.', ',', ';', ':', '!', '?')
        if (t.isEmpty()) return null
        val f = findAll(t).firstOrNull() ?: return null
        return if (f.range.first == 0 && f.range.last == t.lastIndex) f.ref else null
    }
}
