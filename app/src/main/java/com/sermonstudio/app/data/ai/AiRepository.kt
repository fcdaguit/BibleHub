package com.sermonstudio.app.data.ai

import com.sermonstudio.app.data.bible.BibleBooks
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.bible.overlaps
import com.sermonstudio.app.data.knowledge.KnowledgeBase
import com.sermonstudio.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a generation: the draft plus a note if we had to fall back to the template engine. */
data class GenerationResult(val draft: SermonDraft, val notice: String? = null)

/** Chooses the engine from settings and falls back to the offline template engine on failure. */
@Singleton
class AiRepository @Inject constructor(
    private val template: TemplateSermonEngine,
    private val llm: LlmSermonEngine,
    private val settings: SettingsRepository,
) {
    suspend fun llmAvailable() = llm.isAvailable()

    suspend fun generate(req: SermonRequest): GenerationResult {
        val wantLlm = settings.settings.first().aiEngine == "llm"
        if (wantLlm) {
            if (!llm.isAvailable()) return GenerationResult(template.generate(req), "No AI model installed - used the built-in engine.")
            return try {
                GenerationResult(llm.generate(req))
            } catch (e: Throwable) {
                GenerationResult(template.generate(req), "On-device model failed (${e.message?.take(120)}) - used the built-in engine.")
            }
        }
        return GenerationResult(template.generate(req))
    }
}

data class Recommendation(
    val matchedTopics: List<String>,
    val supporting: List<Pair<String, String>>,   // reference, note
    val parallels: List<String>,
    val relatedTopics: List<String>,
    val oldTestament: List<String>,
    val newTestament: List<String>,
    val messianic: List<String>,
)

/** Scripture recommendations from the curated lexicon: supporting, parallel, OT/NT and messianic. */
@Singleton
class ScriptureRecommender @Inject constructor(private val kb: KnowledgeBase) {

    fun recommend(input: String): Recommendation {
        val q = input.trim()
        val ref = ReferenceParser.parseSingle(q)
        val topics = if (ref != null) {
            kb.topics.filter { t -> t.passages.any { p -> ReferenceParser.parseSingle(p.ref)?.overlaps(ref) == true } }.take(3)
        } else kb.matchTopics(q).map { it.topic }

        val passages = topics.flatMap { it.passages }
        val own = ref?.display()
        val supporting = passages.filter { it.ref != own }.map { it.ref to it.note }.distinctBy { it.first }

        val parallelSource = if (ref != null) {
            passages.filter { ReferenceParser.parseSingle(it.ref)?.overlaps(ref) == true }
        } else passages
        val parallels = parallelSource.flatMap { it.xrefs }.distinct().filter { it != own }

        val all = (supporting.map { it.first } + parallels + topics.flatMap { it.ot + it.nt }).distinct()
        fun isOt(r: String) = ReferenceParser.parseSingle(r)?.let { it.book <= 39 } == true
        fun isNt(r: String) = ReferenceParser.parseSingle(r)?.let { it.book in 40..66 } == true

        return Recommendation(
            matchedTopics = topics.map { it.title },
            supporting = supporting,
            parallels = parallels,
            relatedTopics = topics.flatMap { it.related }.distinct().mapNotNull { kb.byId(it)?.title },
            oldTestament = all.filter(::isOt),
            newTestament = all.filter(::isNt),
            messianic = topics.flatMap { it.messianic }.distinct(),
        )
    }

    @Suppress("unused")
    fun bookName(num: Int) = BibleBooks.name(num)
}
