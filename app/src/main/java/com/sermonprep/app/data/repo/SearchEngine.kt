package com.sermonprep.app.data.repo

import com.sermonprep.app.ai.LocalLlm
import com.sermonprep.app.data.bible.TopicIndex
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser
import javax.inject.Inject
import javax.inject.Singleton

enum class HitKind { REFERENCE, TOPIC, KEYWORD, AI }

data class SearchHit(
    val ref: BibleRef,
    val versionId: String,
    val versionName: String,
    val verses: List<VerseEntity>,
    val kind: HitKind,
    /** Topic title (for TOPIC hits) or the AI/keyword explanation. */
    val label: String? = null,
    val related: List<BibleRef> = emptyList(),
)

/**
 * Offline search. Layers, in the order results are shown:
 *  1. direct references typed in the query ("John 3:16")
 *  2. the curated topic/question index (assets/topics.json)
 *  3. full-text keyword/phrase search over every selected downloaded translation (SQLite FTS4)
 *  4. optionally, a local LLM that translates the question into references + keywords
 */
@Singleton
class SearchEngine @Inject constructor(
    private val bible: BibleRepository,
    private val topics: TopicIndex,
    private val llm: LocalLlm,
) {
    private val stop = setOf(
        "a", "an", "the", "of", "to", "in", "on", "and", "or", "is", "are", "was", "were", "be", "did", "does", "do",
        "what", "who", "whom", "which", "where", "when", "why", "how", "about", "for", "with", "that", "this", "it",
        "can", "say", "says", "bible", "verse", "verses",
        // Tagalog / Cebuano particles
        "ang", "ng", "sa", "mga", "ay", "na", "at", "si", "nga", "ug", "mao", "kay", "ni",
    )

    private fun tokens(q: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(q.lowercase()).map { it.value }.toList()

    private fun ftsQueries(q: String): List<String> {
        val quoted = Regex("\"([^\"]+)\"").find(q)?.groupValues?.get(1)
        if (quoted != null) {
            val t = tokens(quoted)
            if (t.isNotEmpty()) return listOf("\"" + t.joinToString(" ") + "\"")
        }
        val all = tokens(q)
        val key = all.filter { it !in stop && it.length > 1 }
        val out = ArrayList<String>()
        if (all.size in 2..8) out += "\"" + all.joinToString(" ") + "\""            // exact phrase
        if (key.isNotEmpty()) {
            out += key.joinToString(" ") { "$it*" }                                // all words (prefix)
            if (key.size > 1) out += key.joinToString(" OR ") { "$it*" }           // any word
        }
        return out
    }

    suspend fun search(
        query: String,
        primaryVersionId: String,
        scopeVersionIds: List<String>,
        useAi: Boolean,
        limit: Int = 60,
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val versions = bible.versionsOnce().associateBy { it.id }
        val primary = versions[primaryVersionId] ?: return emptyList()
        val hits = LinkedHashMap<String, SearchHit>()

        suspend fun addRef(ref: BibleRef, kind: HitKind, label: String?) {
            val key = "${ref.book}:${ref.chapter}:${ref.verseStart}:${ref.verseEnd}"
            if (hits.containsKey(key)) return
            val verses = bible.passage(primary.id, ref)
            if (verses.isEmpty()) return
            hits[key] = SearchHit(ref, primary.id, primary.name, verses, kind, label, topics.relatedTo(ref))
        }

        // 1. references typed directly
        ReferenceParser.findAll(q).forEach { addRef(it.ref, HitKind.REFERENCE, null) }

        // 2. topics / questions
        for (m in topics.match(q)) for (r in m.topic.refs) addRef(r, HitKind.TOPIC, m.topic.title)

        // 3. full-text keyword / phrase search
        val verseSeen = HashSet<String>()
        val scope = scopeVersionIds.filter { it in versions }.ifEmpty { listOf(primary.id) }
        for (fq in ftsQueries(q)) {
            if (verseSeen.size >= limit) break
            val rows = runCatching { bible.search(fq, scope, limit) }.getOrDefault(emptyList())
            for (v in rows) {
                if (!verseSeen.add("${v.versionId}:${v.book}:${v.chapter}:${v.verse}")) continue
                val ref = BibleRef(v.book, v.chapter, v.verse)
                val key = "${v.versionId}|${ref.book}:${ref.chapter}:${ref.verseStart}"
                if (hits.containsKey("${ref.book}:${ref.chapter}:${ref.verseStart}:${ref.verseEnd}") && v.versionId == primary.id) continue
                hits[key] = SearchHit(
                    ref, v.versionId, versions[v.versionId]?.name ?: v.versionId,
                    listOf(v), HitKind.KEYWORD, null, topics.relatedTo(ref, 4),
                )
                if (verseSeen.size >= limit) break
            }
        }

        // 4. optional local-LLM query expansion (meaning-based)
        if (useAi && llm.hasModel()) {
            runCatching { aiExpand(q) { r, k, l -> addRef(r, k, l) } }
        }
        return hits.values.toList()
    }

    /**
     * Asks the local model which passages answer the question. The model only proposes references;
     * the verse text always comes from the downloaded translation in the database, so the app never
     * shows verse wording generated by the model. Model suggestions can be wrong – the UI labels them.
     */
    private suspend fun aiExpand(q: String, addRef: suspend (BibleRef, HitKind, String?) -> Unit) {
        val prompt = """
            You help people search the Bible. List up to 6 Bible passages that best answer the question below.
            Write only the references, separated by semicolons, in the form John 3:16 or Psalm 23:1-6.
            Do not add any other words.

            Question: $q
        """.trimIndent()
        val out = llm.generate(prompt)
        ReferenceParser.findAll(out).forEach { addRef(it.ref, HitKind.AI, "AI suggestion") }
    }
}
