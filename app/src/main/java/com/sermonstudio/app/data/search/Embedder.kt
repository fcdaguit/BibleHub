package com.sermonstudio.app.data.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns text into a fixed-length vector. This is the swap point for a real neural embedding model:
 * implement this interface with an ONNX/TFLite sentence-embedding model and bind it in AppModule;
 * the index, cache and search code do not change (the index just needs a rebuild).
 */
interface TextEmbedder {
    val dim: Int

    /** [expansion] are extra related terms mixed in at lower weight (used for query expansion). */
    fun embed(text: String, expansion: List<String> = emptyList()): FloatArray
}

/**
 * Default embedder: feature hashing of stemmed unigrams and bigrams into [dim] buckets.
 *
 * HONEST NOTE: this is lexical similarity (word overlap with stemming), not learned meaning.
 * The "meaning" part of search comes from query expansion with the curated topic lexicon
 * (assets/knowledge/topics.json). A neural embedder would generalise much better.
 */
@Singleton
class HashingEmbedder @Inject constructor() : TextEmbedder {
    override val dim = 512

    override fun embed(text: String, expansion: List<String>): FloatArray {
        val v = FloatArray(dim)
        val toks = Text.tokens(text)
        toks.forEach { add(v, it, 1f) }
        toks.zipWithNext { a, b -> add(v, "$a $b", 0.6f) }
        Text.tokens(expansion.joinToString(" ")).forEach { add(v, it, 0.5f) }
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    private fun add(v: FloatArray, feature: String, w: Float) {
        val h = fnv1a(feature)
        val idx = (h ushr 1) % dim
        v[idx] += if (h and 1 == 0) w else -w
    }

    private fun fnv1a(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (c in s) {
            h = h xor c.code
            h *= 16777619
        }
        return h
    }
}

/** int8 quantisation: unit-length float vectors -> bytes (4x smaller on disk and in memory). */
fun quantize(v: FloatArray): ByteArray =
    ByteArray(v.size) { (v[it] * 127f).roundToInt().coerceIn(-127, 127).toByte() }
