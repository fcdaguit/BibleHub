package com.sermonprep.app.data.bible

import android.content.Context
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class Topic(
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val refs: List<BibleRef>,
)

data class TopicMatch(val topic: Topic, val score: Int)

/**
 * A small curated topic → passage index (assets/topics.json). It powers topic search, question
 * search ("Who denied Jesus three times?"), related passages and the no-model sermon template.
 * It is hand-written, English-keyword based and NOT exhaustive; extend the JSON file to grow it.
 */
@Singleton
class TopicIndex @Inject constructor(@ApplicationContext private val ctx: Context) {

    val topics: List<Topic> by lazy {
        val arr = JSONArray(ctx.assets.open("topics.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Topic(
                title = o.getString("topic"),
                summary = o.optString("summary"),
                keywords = o.getJSONArray("keywords").let { k -> (0 until k.length()).map { k.getString(it).lowercase() } },
                refs = o.getJSONArray("refs").let { r ->
                    (0 until r.length()).mapNotNull { ReferenceParser.parse(r.getString(it)) }
                },
            )
        }
    }

    /** Scores each topic: a keyword/phrase found in the query earns 2 points per word it contains. */
    fun match(query: String, max: Int = 3): List<TopicMatch> {
        val q = " " + query.lowercase().replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim() + " "
        if (q.isBlank()) return emptyList()
        return topics.mapNotNull { t ->
            var score = 0
            for (k in t.keywords) {
                if (q.contains(" $k ") || q.contains(" ${k}s ") || q.contains(" ${k}es ")) {
                    score += 2 * k.split(' ').size
                }
            }
            if (q.contains(" ${t.title.lowercase()} ")) score += 4
            if (score >= 2) TopicMatch(t, score) else null
        }.sortedByDescending { it.score }.take(max)
    }

    /** Other passages that share a topic with [ref] (parallel accounts, supporting texts). */
    fun relatedTo(ref: BibleRef, max: Int = 6): List<BibleRef> =
        topics.filter { t -> t.refs.any { it.overlaps(ref) } }
            .flatMap { it.refs }
            .filterNot { it.overlaps(ref) }
            .distinct()
            .take(max)
}
