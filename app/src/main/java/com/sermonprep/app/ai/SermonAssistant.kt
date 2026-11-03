package com.sermonprep.app.ai

import com.sermonprep.app.data.bible.TopicIndex
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantResult(
    val text: String,
    /** true = written by the local model; false = built from the topic index / blank template. */
    val usedModel: Boolean,
    /** References in [text] that do not exist in the selected translation (possible model mistakes). */
    val unverified: List<BibleRef>,
)

@Singleton
class SermonAssistant @Inject constructor(
    private val llm: LocalLlm,
    private val topics: TopicIndex,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
) {
    suspend fun generate(topic: String, outputLanguage: String): AssistantResult {
        val hasModel = llm.hasModel()
        val text = if (hasModel) fromModel(topic, outputLanguage) else fromTemplate(topic)
        return AssistantResult(text, hasModel, unverifiedRefs(text))
    }

    private suspend fun fromModel(topic: String, language: String): String {
        val prompt = """
            You are an assistant for pastors preparing sermons. Write a sermon outline on the topic below.
            Write the outline in $language. Use exactly these headings, each on its own line:
            Title:
            Main Text:
            Supporting Verses:
            Key Points: (3 to 4 numbered points, each with one sentence of explanation)
            Themes and Lessons:
            Introduction:
            Conclusion:
            Application Questions: (3 questions)
            Discussion Questions: (3 questions)

            Rules: cite Bible passages only by reference (for example James 1:2-4). Never quote verse text.
            Use only real passages and do not invent references. Keep it faithful to Scripture and concise.

            Topic: $topic
        """.trimIndent()
        return llm.generate(prompt).trim()
    }

    /** Without a model there is nothing to "write": give the curated passages plus a skeleton to fill in. */
    private fun fromTemplate(topic: String): String {
        val match = topics.match(topic, max = 3)
        val refs = match.flatMap { it.topic.refs }.distinct()
        val main = refs.firstOrNull()
        val supporting = refs.drop(1).take(5)
        return buildString {
            appendLine("Title: $topic")
            appendLine()
            appendLine("Main Text: ${main?.label() ?: "(choose a passage)"}")
            appendLine("Supporting Verses: ${if (supporting.isEmpty()) "(add verses)" else supporting.joinToString("; ") { it.label() }}")
            if (match.isNotEmpty()) {
                appendLine("Related topics: ${match.joinToString(", ") { it.topic.title }}")
            }
            appendLine()
            appendLine("Introduction:")
            appendLine("- ")
            appendLine()
            appendLine("Key Points:")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("Conclusion:")
            appendLine("- ")
            appendLine()
            appendLine("Application Questions:")
            appendLine("1. What does this passage say about God?")
            appendLine("2. What does it ask of us?")
            appendLine("3. What is one step I will take this week in response?")
            appendLine()
            appendLine("Discussion Questions:")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
        }.trimEnd()
    }

    private suspend fun unverifiedRefs(text: String): List<BibleRef> {
        val versions = bible.versionsOnce()
        if (versions.isEmpty()) return emptyList()
        val vid = settings.versionId.first()?.takeIf { id -> versions.any { it.id == id } } ?: versions.first().id
        return ReferenceParser.findAll(text).map { it.ref }.distinct()
            .filter { bible.passage(vid, it).isEmpty() }
    }
}
