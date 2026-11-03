package com.sermonstudio.app.data.ai

import com.sermonstudio.app.data.bible.BibleBooks
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.bible.overlaps
import com.sermonstudio.app.data.knowledge.KPassage
import com.sermonstudio.app.data.knowledge.KnowledgeBase
import com.sermonstudio.app.data.search.SearchEngine
import com.sermonstudio.app.data.search.SearchMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Always-available, fully offline engine. It does NOT generate free text: it assembles an outline
 * from the curated topic lexicon (topics.json) and the installed Bible text. That makes it fast,
 * deterministic and free of invented content, but also limited to the topics in the lexicon and to
 * fairly plain wording. For richer prose, install an on-device model (see LlmSermonEngine).
 */
@Singleton
class TemplateSermonEngine @Inject constructor(
    private val kb: KnowledgeBase,
    private val bible: BibleRepository,
    private val search: SearchEngine,
) : SermonEngine {

    override val id = "template"

    override suspend fun generate(req: SermonRequest): SermonDraft =
        if (req.passage != null) fromPassage(req, req.passage) else fromTopic(req)

    // ------------------------------------------------------------------------------------------
    private suspend fun fromTopic(req: SermonRequest): SermonDraft {
        val text = req.topic.orEmpty().trim()
        val match = kb.matchTopics(text).firstOrNull() ?: return fallbackTopic(req, text)
        val t = match.topic
        val count = if (req.type == SermonType.DEVOTIONAL) 1 else 3
        val ordered = t.passages.sortedByDescending { kb.passageAffinity(text, it) }
        val main = ordered.first()
        val points = ordered.take(count).map { p ->
            SermonPoint(headingFrom(p), listOf(p.ref) + p.xrefs.take(2), p.note)
        }
        val refs = (t.passages.flatMap { it.xrefs } + t.ot + t.nt + t.messianic).distinct()
            .filterNot { r -> points.any { r in it.scriptures } }.take(10)
        val related = t.related.mapNotNull { kb.byId(it)?.title }
        return SermonDraft(
            title = t.title,
            theme = t.intro.ifBlank { "What Scripture teaches about ${t.title.lowercase()}" },
            mainText = main.ref,
            introduction = t.intro.ifBlank { "Begin with a real-life situation where ${t.title.lowercase()} matters, then turn to ${main.ref}." } +
                if (related.isNotEmpty()) " (Related topics: ${related.joinToString(", ")}.)" else "",
            points = points,
            crossReferences = refs,
            illustrations = t.illustrations.ifEmpty { listOf("Idea: a short true story from your own ministry that shows ${t.title.lowercase()} at work.") },
            applications = t.applications.ifEmpty { listOf("Ask the hearers: what one step will you take this week in response to ${main.ref}?") },
            discussionQuestions = t.discussion.ifEmpty { listOf("What stands out to you in ${main.ref}?", "How does this change the way you live this week?") },
            conclusion = "Return to ${main.ref}: ${firstSentence(main.note)} Restate the central truth in one sentence people can remember.",
            invitation = invitationFor(req.type),
            engine = id,
        )
    }

    private suspend fun fallbackTopic(req: SermonRequest, text: String): SermonDraft {
        val results = search.search(text, req.versionId, SearchMode.ALL, 6).filter { it.text.isNotBlank() }
        val title = text.replaceFirstChar { it.uppercase() }.ifBlank { "New sermon" }
        if (results.isEmpty()) {
            return SermonDraft(
                title = title, theme = "", mainText = "",
                introduction = "(No matching material found offline for \"$text\". Try another word, install more Bible versions, or use an on-device AI model.)",
                points = listOf(1, 2, 3).map { SermonPoint("Point $it", emptyList(), "") },
                crossReferences = emptyList(), illustrations = emptyList(), applications = emptyList(),
                discussionQuestions = emptyList(), conclusion = "", invitation = invitationFor(req.type), engine = id,
            )
        }
        val points = results.take(3).map { SermonPoint(it.label, listOf(it.label), it.text) }
        return SermonDraft(
            title = title,
            theme = "What Scripture says about $text",
            mainText = results.first().label,
            introduction = "Passages found by searching your installed Bible for \"$text\". Arrange them into a flow and add your own context.",
            points = points,
            crossReferences = results.drop(3).map { it.label },
            illustrations = emptyList(), applications = listOf("What should a listener do differently because of these passages?"),
            discussionQuestions = listOf("Which of these passages speaks most directly to your situation?"),
            conclusion = "Summarise the common thread in ${points.joinToString(", ") { it.scriptures.first() }}.",
            invitation = invitationFor(req.type), engine = id,
        )
    }

    // ------------------------------------------------------------------------------------------
    private suspend fun fromPassage(req: SermonRequest, ref: BibleRef): SermonDraft {
        val name = ref.display()
        val verses = bible.passage(req.versionId, ref)
        val topics = kb.topics.filter { t ->
            t.passages.any { p -> ReferenceParser.parseSingle(p.ref)?.overlaps(ref) == true }
        }
        val first = topics.firstOrNull()
        val chunkSize = maxOf(1, ceil(verses.size / 3.0).toInt())
        val chunks = verses.chunked(chunkSize).let { if (req.type == SermonType.DEVOTIONAL) listOf(verses) else it }
        val points = chunks.map { c ->
            val a = c.first().verse
            val b = c.last().verse
            val r = BibleRef(ref.book, ref.chapter, a, if (b > a) b else null)
            SermonPoint(
                heading = "${if (b > a) "Verses $a-$b" else "Verse $a"}: ${firstWords(c.first().text)}",
                scriptures = listOf(r.display()),
                explanation = c.joinToString(" ") { "${it.verse} ${it.text}" },
            )
        }.ifEmpty { listOf(SermonPoint("(Passage not found in the selected Bible version)", listOf(name), "")) }

        val refs = topics.flatMap { t -> t.passages.flatMap { it.xrefs } + t.ot + t.nt + t.messianic }
            .distinct().filterNot { it == name }.take(10)

        val studyQs = if (req.type == SermonType.BIBLE_STUDY || req.type == SermonType.SMALL_GROUP) {
            points.map { "Observe: what does ${it.scriptures.first()} say? Interpret: what does it teach about God? Apply: how should we respond?" }
        } else listOf("What stands out to you in $name?", "How does this passage change what you will do this week?")

        return SermonDraft(
            title = first?.let { "${it.title}: $name" } ?: "Study of $name",
            theme = first?.intro?.ifBlank { null } ?: "Observations from $name",
            mainText = name,
            background = "Study prompt: this passage is in ${BibleBooks.name(ref.book)}, chapter ${ref.chapter}. " +
                "Read the surrounding chapters, identify the author, first audience and occasion, and note how the passage fits the book's argument. " +
                "(The built-in engine cannot supply historical background; use your commentaries or an on-device AI model.)",
            introduction = "Read $name aloud. Ask the congregation what they notice before explaining it.",
            points = points,
            crossReferences = refs,
            illustrations = first?.illustrations ?: listOf("Idea: a real-life situation that mirrors the tension or promise in $name."),
            applications = first?.applications ?: listOf("State one concrete response to $name that a hearer can do this week."),
            discussionQuestions = studyQs,
            conclusion = "Summarise the one main truth of $name and how it points to Christ.",
            invitation = invitationFor(req.type),
            engine = id,
        )
    }

    // ------------------------------------------------------------------------------------------
    private fun invitationFor(t: SermonType) = when (t) {
        SermonType.SMALL_GROUP, SermonType.BIBLE_STUDY -> "Close in prayer, and invite each person to share one step they will take this week."
        SermonType.DEVOTIONAL -> "Pause and pray over the passage, then carry one phrase with you through the day."
        SermonType.YOUTH -> "Challenge the young people to respond now: commit to one change and tell a friend about it."
        else -> "Invite the congregation to respond to what God has shown them today - in repentance, faith or a step of obedience."
    }

    private fun firstSentence(s: String): String = s.substringBefore(". ").trimEnd('.').let { if (it.isBlank()) s else "$it." }

    private fun firstWords(s: String, n: Int = 7): String {
        val w = s.split(' ')
        return w.take(n).joinToString(" ").trimEnd(',', ';', ':') + if (w.size > n) "…" else ""
    }

    private fun headingFrom(p: KPassage): String {
        val s = firstSentence(p.note).trimEnd('.')
        if (s.isBlank()) return p.ref
        return if (s.length <= 90) s else s.take(90).substringBeforeLast(' ') + "…"
    }
}
