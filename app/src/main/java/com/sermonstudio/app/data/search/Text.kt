package com.sermonstudio.app.data.search

/** Tiny tokenizer/stemmer shared by the topic matcher and the embedder. English-oriented. */
object Text {
    private val stop = setOf(
        "a", "an", "the", "of", "to", "in", "on", "for", "and", "or", "is", "are", "was", "were", "be", "been",
        "do", "does", "did", "how", "what", "who", "whom", "where", "when", "why", "which", "can", "could",
        "should", "would", "i", "me", "my", "we", "us", "our", "you", "your", "it", "its", "that", "this",
        "these", "those", "with", "about", "from", "by", "at", "as", "into", "there", "verses", "verse",
        "bible", "passage", "passages", "say", "says", "said", "teach", "teaches", "about", "some", "any",
        "his", "her", "he", "she", "they", "them", "their", "not", "but", "so", "if", "then", "than",
    )

    private val word = Regex("[\\p{L}']+")

    fun stem(w: String): String {
        if (w.length > 4 && w.endsWith("ies")) return w.dropLast(3) + "y"
        for (suf in listOf("ing", "ed", "s")) {
            if (w.length > suf.length + 2 && w.endsWith(suf)) return w.dropLast(suf.length)
        }
        return w
    }

    /** Lower-cased, stop-word-free, stemmed tokens. */
    fun tokens(s: String): List<String> =
        word.findAll(s.lowercase()).map { it.value.replace("'", "") }
            .filter { it.length >= 2 && it !in stop }
            .map(::stem)
            .toList()

    /** Raw words (no stop-word removal) for building FTS queries. */
    fun words(s: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(s).map { it.value }.toList()

    fun isStopWord(w: String) = w.lowercase() in stop

    fun looksLikeQuestion(s: String): Boolean {
        val t = s.trim().lowercase()
        return t.endsWith("?") || listOf("who ", "what ", "where ", "when ", "why ", "how ", "which ").any { t.startsWith(it) }
    }
}
