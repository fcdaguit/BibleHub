package com.sermonstudio.app.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.knowledge.KnowledgeBase
import com.sermonstudio.app.data.prefs.SettingsRepository
import com.sermonstudio.app.data.search.SearchEngine
import com.sermonstudio.app.data.search.SearchMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional engine that runs a downloadable LLM fully on the device through MediaPipe's LLM Inference
 * API. The user imports a compatible model file in Settings (model choice, size and licence are the
 * user's responsibility); nothing leaves the phone.
 *
 * Grounding: verses from the user's own installed Bible are placed in the prompt, the model is told
 * to quote only those, and every reference in its answer is re-validated by [ReferenceParser] so
 * malformed citations are dropped. Content can still be wrong - drafts must be reviewed.
 *
 * NOTE: the MediaPipe API surface changes between releases. If the build fails in [runModel],
 * check the current "LLM Inference guide for Android" and adjust that one function.
 */
@Singleton
class LlmSermonEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val bible: BibleRepository,
    private val search: SearchEngine,
    private val kb: KnowledgeBase,
    private val settings: SettingsRepository,
) : SermonEngine {

    override val id = "llm"

    private var llm: LlmInference? = null
    private var loadedPath: String? = null

    suspend fun isAvailable(): Boolean =
        settings.settings.first().llmModelPath?.let { File(it).exists() } == true

    override suspend fun generate(req: SermonRequest): SermonDraft = withContext(Dispatchers.Default) {
        val path = settings.settings.first().llmModelPath
        require(path != null && File(path).exists()) { "No AI model installed. Import one in Settings." }
        val grounding = groundingVerses(req)
        val raw = runModel(path, buildPrompt(req, grounding))
        parse(raw, req)
    }

    private suspend fun groundingVerses(req: SermonRequest): List<Pair<String, String>> {
        req.passage?.let { ref ->
            return bible.passage(req.versionId, ref).take(40).map { "${ref.display().substringBefore(':')}:${it.verse}" to it.text }
        }
        val topic = req.topic.orEmpty()
        return search.search(topic, req.versionId, SearchMode.ALL, 8)
            .filter { it.text.isNotBlank() }.map { it.label to it.text }
    }

    @Synchronized
    private fun runModel(modelPath: String, prompt: String): String {
        if (llm == null || loadedPath != modelPath) {
            runCatching { llm?.close() }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1536)
                .build()
            llm = LlmInference.createFromOptions(ctx, options)
            loadedPath = modelPath
        }
        return llm!!.generateResponse(prompt)
    }

    private fun buildPrompt(req: SermonRequest, verses: List<Pair<String, String>>): String = buildString {
        appendLine("You are a careful, orthodox Bible teacher helping a pastor prepare a ${req.type.label}.")
        appendLine("Rules: quote Scripture only from the VERSES list. Never invent quotations, statistics or historical claims. Cite references as Book Chapter:Verse.")
        appendLine("Reply in plain text using exactly these headers, each on its own line:")
        appendLine("## TITLE\n## THEME\n## MAIN TEXT\n## INTRODUCTION")
        appendLine("## POINT 1: <short heading>   (2-4 sentences, then a line starting 'Scriptures:' with references separated by semicolons)")
        appendLine("## POINT 2: <short heading>\n## POINT 3: <short heading>")
        appendLine("## CROSS REFERENCES   (one reference per line)\n## ILLUSTRATIONS   (idea starters, one per line, no invented facts)")
        appendLine("## APPLICATIONS   (one per line)\n## DISCUSSION QUESTIONS   (one per line)\n## CONCLUSION\n## INVITATION")
        appendLine()
        appendLine("SUBJECT: ${req.passage?.display() ?: req.topic}")
        req.topic?.let { t -> kb.matchTopics(t).firstOrNull()?.let { appendLine("HINT: related themes - ${it.topic.keywords.joinToString(", ")}") } }
        appendLine("VERSES (${req.versionId}):")
        verses.forEach { (r, t) -> appendLine("- $r: $t") }
    }

    /** Tolerant parser: missing sections become empty rather than failing. */
    private fun parse(raw: String, req: SermonRequest): SermonDraft {
        val sections = LinkedHashMap<String, String>()
        var key: String? = null
        val buf = StringBuilder()
        fun flush() { key?.let { sections[it] = buf.toString().trim() }; buf.setLength(0) }
        for (line in raw.lines()) {
            val m = Regex("^\\s*#{2,}\\s*(.+?)\\s*$").find(line)
            if (m != null) { flush(); key = m.groupValues[1].uppercase().trimEnd(':') } else buf.appendLine(line)
        }
        flush()

        fun items(name: String) = sections[name].orEmpty().lines()
            .map { it.trim().removePrefix("-").removePrefix("•").replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.isNotBlank() }

        val points = sections.entries.filter { it.key.startsWith("POINT") }.map { (k, body) ->
            val heading = k.substringAfter(':', "").trim().ifBlank { k.lowercase().replaceFirstChar { c -> c.uppercase() } }
            val scriptLine = body.lines().firstOrNull { it.trim().startsWith("Scriptures", ignoreCase = true) }.orEmpty()
            val refs = ReferenceParser.findAll(scriptLine).map { it.ref.display() }.distinct()
            SermonPoint(heading, refs, body.lines().filterNot { it == scriptLine && scriptLine.isNotEmpty() }.joinToString(" ").trim())
        }
        val main = ReferenceParser.findAll(sections["MAIN TEXT"].orEmpty()).firstOrNull()?.ref?.display()
            ?: req.passage?.display().orEmpty()

        return SermonDraft(
            title = sections["TITLE"]?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
                ?: (req.topic ?: req.passage?.display() ?: "Sermon"),
            theme = sections["THEME"].orEmpty(),
            mainText = main,
            introduction = sections["INTRODUCTION"].orEmpty(),
            points = points.ifEmpty { listOf(SermonPoint("Model output", emptyList(), raw.take(2000))) },
            crossReferences = ReferenceParser.findAll(sections["CROSS REFERENCES"].orEmpty()).map { it.ref.display() }.distinct(),
            illustrations = items("ILLUSTRATIONS"),
            applications = items("APPLICATIONS"),
            discussionQuestions = items("DISCUSSION QUESTIONS"),
            conclusion = sections["CONCLUSION"].orEmpty(),
            invitation = sections["INVITATION"].orEmpty(),
            engine = id,
        )
    }
}
