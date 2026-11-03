package com.sermonstudio.app.data.knowledge

import android.content.Context
import com.sermonstudio.app.data.search.Text
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

@Serializable
data class KPassage(
    val ref: String,
    val note: String = "",
    /** Extra words that make this passage the best hit for a query ("mountain", "first miracle"). */
    val keys: List<String> = emptyList(),
    val xrefs: List<String> = emptyList(),
)

@Serializable
data class KTopic(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val synonyms: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val passages: List<KPassage>,
    /** ids of related topics */
    val related: List<String> = emptyList(),
    val ot: List<String> = emptyList(),
    val nt: List<String> = emptyList(),
    val messianic: List<String> = emptyList(),
    val intro: String = "",
    val applications: List<String> = emptyList(),
    val illustrations: List<String> = emptyList(),
    val discussion: List<String> = emptyList(),
)

@Serializable
private data class KFile(val topics: List<KTopic>)

data class TopicMatch(val topic: KTopic, val score: Double, val hits: List<String>)

/**
 * Curated, hand-authored topic lexicon (assets/knowledge/topics.json). It powers topic search,
 * question search, query expansion for the semantic index, and the offline sermon templates.
 * The content is a starter set: extend or replace the JSON to grow coverage (see docs/ARCHITECTURE.md).
 */
@Singleton
class KnowledgeBase @Inject constructor(@ApplicationContext private val ctx: Context) {

    val topics: List<KTopic> by lazy {
        val json = ctx.assets.open("knowledge/topics.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<KFile>(json).topics
    }

    private val topicTokens: Map<String, Set<String>> by lazy {
        topics.associate { t ->
            t.id to (t.keywords + t.synonyms + t.questions + t.title).flatMap(Text::tokens).toSet()
        }
    }

    private val df: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        topicTokens.values.forEach { set -> set.forEach { m[it] = (m[it] ?: 0) + 1 } }
        m
    }

    private fun idf(t: String): Double = ln(1.0 + topics.size / (df[t] ?: 0.5))

    fun byId(id: String): KTopic? = topics.firstOrNull { it.id == id }

    /** Best matching topics for free text or a question (max 3, score 0..1). */
    fun matchTopics(query: String): List<TopicMatch> {
        val q = Text.tokens(query).distinct()
        if (q.isEmpty()) return emptyList()
        val total = q.sumOf(::idf)
        val ql = query.lowercase()
        return topics.map { t ->
            val tt = topicTokens.getValue(t.id)
            val hit = q.filter { it in tt }
            var s = hit.sumOf(::idf) / total
            if (t.keywords.any { it.length > 3 && ql.contains(it.lowercase()) }) s += 0.25
            if (t.questions.any { Text.tokens(it).toSet() == q.toSet() }) s += 0.3
            TopicMatch(t, s.coerceAtMost(1.0), hit)
        }.filter { it.hits.isNotEmpty() && it.score >= 0.34 }
            .sortedByDescending { it.score }
            .take(3)
    }

    /** 0..1 - how strongly the query's tokens appear in this passage's keys and note. */
    fun passageAffinity(query: String, p: KPassage): Double {
        val q = Text.tokens(query).distinct()
        if (q.isEmpty()) return 0.0
        val pt = (p.keys + p.note).flatMap(Text::tokens).toSet()
        return q.count { it in pt }.toDouble() / q.size
    }

    /** Words worth adding to a query for semantic search: keywords/synonyms of matching topics. */
    fun expansionFor(query: String): List<String> =
        matchTopics(query).flatMap { it.topic.keywords + it.topic.synonyms }.distinct()
}
