package com.sermonstudio.app.data.search

import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.VerseEntity
import com.sermonstudio.app.data.knowledge.KPassage
import com.sermonstudio.app.data.knowledge.KnowledgeBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchMode { ALL, KEYWORD, PHRASE, TOPIC, SEMANTIC, QUESTION }

data class SearchResult(
    val ref: BibleRef,
    val label: String,
    /** Text in the selected translation; empty if that passage is not in the installed version. */
    val text: String,
    /** Explanation snippet from the curated knowledge base, if any. */
    val note: String?,
    val crossRefs: List<String>,
    /** Which engines found it: reference, topic, question, phrase, keyword, semantic. */
    val sources: Set<String>,
    val score: Double,
)

/**
 * Hybrid Bible search, entirely on device:
 *  1. direct reference ("John 3:16")
 *  2. curated topic / question lexicon (concept-level matches, with explanation snippets)
 *  3. FTS4 phrase and keyword search with English stemming
 *  4. vector search over the local semantic index, with query expansion from the topic lexicon
 * Hits are merged (verses inside a curated passage boost that passage) and ranked by a simple
 * additive score. The weights below are heuristics - tune them against your own test queries.
 */
@Singleton
class SearchEngine @Inject constructor(
    private val bible: BibleRepository,
    private val db: AppDatabase,
    private val kb: KnowledgeBase,
    private val index: VectorIndex,
) {
    private class Hit(
        val ref: BibleRef,
        var score: Double = 0.0,
        val sources: MutableSet<String> = linkedSetOf(),
        var note: String? = null,
        var xrefs: List<String> = emptyList(),
    )

    /** Every curated passage with its parsed reference, for note/cross-reference lookups. */
    private val curatedIndex: List<Pair<BibleRef, KPassage>> by lazy {
        kb.topics.flatMap { t -> t.passages.mapNotNull { p -> ReferenceParser.parseSingle(p.ref)?.let { it to p } } }
    }

    suspend fun search(query: String, versionId: String, mode: SearchMode = SearchMode.ALL, limit: Int = 30): List<SearchResult> =
        withContext(Dispatchers.Default) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()

            ReferenceParser.parseSingle(q)?.let { r ->
                return@withContext listOf(toResult(Hit(r, 10.0, linkedSetOf("reference")), versionId))
            }

            val curated = ArrayList<Hit>()
            val loose = LinkedHashMap<String, Hit>()

            fun addVerse(v: VerseEntity, s: Double, tag: String) {
                val cur = curated.firstOrNull { it.ref.covers(v.book, v.chapter, v.verse) }
                if (cur != null) { cur.score += s; cur.sources += tag; return }
                val h = loose.getOrPut("${v.book}:${v.chapter}:${v.verse}") { Hit(BibleRef(v.book, v.chapter, v.verse)) }
                h.score += s; h.sources += tag
            }

            // --- 1. curated topics / questions -----------------------------------------------------
            if (mode == SearchMode.ALL || mode == SearchMode.TOPIC || mode == SearchMode.QUESTION) {
                val tag = if (mode == SearchMode.QUESTION || Text.looksLikeQuestion(q)) "question" else "topic"
                for (m in kb.matchTopics(q)) {
                    m.topic.passages.forEachIndexed { j, p ->
                        val ref = ReferenceParser.parseSingle(p.ref) ?: return@forEachIndexed
                        val s = 2.0 * m.score / (1.0 + 0.1 * j) + 1.5 * kb.passageAffinity(q, p)
                        val existing = curated.firstOrNull { it.ref == ref }
                        if (existing != null) { existing.score += s * 0.5; existing.sources += tag }
                        else curated += Hit(ref, s, linkedSetOf(tag), p.note.ifBlank { null }, p.xrefs)
                    }
                }
            }

            // --- 2. full text: phrase + keywords ---------------------------------------------------
            val words = Text.words(q)
            val content = words.filterNot { Text.isStopWord(it) }.ifEmpty { words }

            suspend fun fts(match: String, weight: Double, tag: String): Int {
                val rows = runCatching { db.bible().ftsSearch(match, versionId, 40) }.getOrDefault(emptyList())
                rows.forEachIndexed { i, v -> addVerse(v, weight / (1.0 + 0.15 * i), tag) }
                return rows.size
            }

            if ((mode == SearchMode.ALL && words.size >= 2) || mode == SearchMode.PHRASE) {
                fts("\"" + words.joinToString(" ") + "\"", 1.2, "phrase")
            }
            if (mode == SearchMode.ALL || mode == SearchMode.KEYWORD) {
                val n = fts(content.joinToString(" ") { "\"$it\"" }, 0.8, "keyword")   // implicit AND
                if (n == 0 && mode == SearchMode.KEYWORD && content.size > 1) {
                    fts(content.joinToString(" OR ") { "\"$it\"" }, 0.5, "keyword")
                }
            }

            // --- 3. semantic -----------------------------------------------------------------------
            val needSemantic = mode == SearchMode.ALL || mode == SearchMode.SEMANTIC ||
                ((mode == SearchMode.TOPIC || mode == SearchMode.QUESTION) && curated.isEmpty())
            if (needSemantic) {
                val hits = index.query(versionId, q, kb.expansionFor(q), 40)
                val byId = db.bible().byIds(hits.map { it.first }).associateBy { it.id }
                hits.forEach { (id, cos) -> byId[id]?.let { addVerse(it, 0.9 * minOf(1.0, cos * 2.0), "semantic") } }
            }

            // last resort so a query never returns nothing when words exist
            if (curated.isEmpty() && loose.isEmpty() && mode == SearchMode.ALL && content.size > 1) {
                fts(content.joinToString(" OR ") { "\"$it\"" }, 0.4, "keyword")
            }

            (curated + loose.values)
                .sortedByDescending { it.score }
                .take(limit)
                .map { toResult(it, versionId) }
        }

    private suspend fun toResult(h: Hit, versionId: String): SearchResult {
        val verses = bible.passage(versionId, h.ref)
        val text = when {
            verses.isEmpty() -> ""
            verses.size == 1 -> verses[0].text
            else -> verses.joinToString(" ") { "${it.verse} ${it.text}" }
        }
        // For plain verse hits, borrow note / cross-references from a curated passage that covers it.
        val cover = if (h.note == null && h.xrefs.isEmpty()) {
            curatedIndex.firstOrNull { (r, _) -> h.ref.verseStart?.let { r.covers(h.ref.book, h.ref.chapter, it) } == true }?.second
        } else null
        return SearchResult(
            ref = h.ref,
            label = h.ref.display(),
            text = text,
            note = h.note ?: cover?.note?.ifBlank { null },
            crossRefs = h.xrefs.ifEmpty { cover?.xrefs ?: emptyList() },
            sources = h.sources,
            score = h.score,
        )
    }
}
