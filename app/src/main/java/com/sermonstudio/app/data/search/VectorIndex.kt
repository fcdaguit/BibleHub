package com.sermonstudio.app.data.search

import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.VerseVectorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device semantic index. One int8 vector per verse is stored in Room (table verse_vectors) and
 * cached in RAM as one flat ByteArray for fast brute-force cosine search. For ~31k verses x 512 dims
 * that is ~16 MB per Bible version and a query scans it in well under a second on a mid-range phone
 * (not benchmarked here - measure on your target device).
 */
@Singleton
class VectorIndex @Inject constructor(
    private val db: AppDatabase,
    private val embedder: TextEmbedder,
) {
    private class Cache(val ids: LongArray, val data: ByteArray)

    private val caches = HashMap<String, Cache>()

    fun invalidate(versionId: String) = synchronized(caches) { caches.remove(versionId) }

    /** (Re)builds all vectors for a version. [onProgress] gets 0f..1f. */
    suspend fun build(versionId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.Default) {
        val bible = db.bible()
        val total = bible.countVerses(versionId).coerceAtLeast(1)
        db.vectors().deleteFor(versionId)
        var after = 0L
        var done = 0
        while (true) {
            val page = bible.page(versionId, after, 2000)
            if (page.isEmpty()) break
            db.vectors().put(page.map { VerseVectorEntity(it.id, versionId, quantize(embedder.embed(it.text))) })
            after = page.last().id
            done += page.size
            onProgress(done.toFloat() / total)
        }
        invalidate(versionId)
    }

    private suspend fun load(versionId: String): Cache {
        synchronized(caches) { caches[versionId] }?.let { return it }
        val rows = db.vectors().forVersion(versionId)
        val d = embedder.dim
        val ids = LongArray(rows.size)
        val data = ByteArray(rows.size * d)
        rows.forEachIndexed { i, r ->
            ids[i] = r.verseId
            System.arraycopy(r.vec, 0, data, i * d, d)
        }
        return Cache(ids, data).also { synchronized(caches) { caches[versionId] = it } }
    }

    /** Top [k] (verseId, cosine) pairs for the query, best first. */
    suspend fun query(versionId: String, text: String, expansion: List<String>, k: Int): List<Pair<Long, Float>> =
        withContext(Dispatchers.Default) {
            val c = load(versionId)
            if (c.ids.isEmpty()) return@withContext emptyList()
            val q = embedder.embed(text, expansion)
            val d = embedder.dim
            val bestId = LongArray(k) { -1 }
            val bestScore = FloatArray(k) { Float.NEGATIVE_INFINITY }
            for (r in c.ids.indices) {
                var s = 0f
                val off = r * d
                for (i in 0 until d) s += q[i] * c.data[off + i]
                s /= 127f
                if (s > bestScore[k - 1] && s > 0.08f) {
                    var p = k - 1
                    while (p > 0 && bestScore[p - 1] < s) {
                        bestScore[p] = bestScore[p - 1]; bestId[p] = bestId[p - 1]; p--
                    }
                    bestScore[p] = s; bestId[p] = c.ids[r]
                }
            }
            (0 until k).filter { bestId[it] >= 0 }.map { bestId[it] to bestScore[it] }
        }
}
