package com.sermonstudio.app.data.bible

/**
 * The 66 books of the Protestant canon, numbered 1..66 (Genesis = 1, Revelation = 66).
 * Every Bible version is stored by these numbers, so a reference works across translations.
 *
 * [aliases] drive reference detection. English abbreviations are included, plus a starter set of
 * Tagalog and Cebuano names (extend freely - the parser rebuilds its pattern from this list).
 */
data class Book(val num: Int, val name: String, val aliases: List<String> = emptyList())

object BibleBooks {
    val all: List<Book> = listOf(
        Book(1, "Genesis", listOf("Gen", "Ge", "Gn", "Henesis")),
        Book(2, "Exodus", listOf("Exod", "Exo", "Ex", "Exodo")),
        Book(3, "Leviticus", listOf("Lev", "Le", "Lv", "Levitico")),
        Book(4, "Numbers", listOf("Num", "Nu", "Nm", "Nb", "Mga Bilang", "Numeros")),
        Book(5, "Deuteronomy", listOf("Deut", "De", "Dt", "Deuteronomio")),
        Book(6, "Joshua", listOf("Josh", "Jos", "Josue")),
        Book(7, "Judges", listOf("Judg", "Jdg", "Jg", "Mga Hukom")),
        Book(8, "Ruth", listOf("Ru")),
        Book(9, "1 Samuel", listOf("1 Sam", "1 Sa", "I Samuel", "First Samuel")),
        Book(10, "2 Samuel", listOf("2 Sam", "2 Sa", "II Samuel", "Second Samuel")),
        Book(11, "1 Kings", listOf("1 Kgs", "1 Ki", "I Kings", "First Kings", "1 Hari")),
        Book(12, "2 Kings", listOf("2 Kgs", "2 Ki", "II Kings", "Second Kings", "2 Hari")),
        Book(13, "1 Chronicles", listOf("1 Chron", "1 Chr", "1 Ch", "I Chronicles", "1 Cronica")),
        Book(14, "2 Chronicles", listOf("2 Chron", "2 Chr", "2 Ch", "II Chronicles", "2 Cronica")),
        Book(15, "Ezra", listOf("Ezr", "Esdras")),
        Book(16, "Nehemiah", listOf("Neh", "Ne", "Nehemias")),
        Book(17, "Esther", listOf("Esth", "Est")),
        Book(18, "Job", listOf("Jb")),
        Book(19, "Psalms", listOf("Psalm", "Ps", "Psa", "Pslm", "Salmo", "Mga Awit", "Awit")),
        Book(20, "Proverbs", listOf("Prov", "Pro", "Pr", "Prv", "Kawikaan", "Panultihon")),
        Book(21, "Ecclesiastes", listOf("Eccles", "Eccl", "Ecc", "Eclesiastes")),
        Book(22, "Song of Solomon", listOf("Song of Songs", "Song", "SOS", "Awit ni Solomon")),
        Book(23, "Isaiah", listOf("Isa", "Is", "Isaias")),
        Book(24, "Jeremiah", listOf("Jer", "Je", "Jeremias")),
        Book(25, "Lamentations", listOf("Lam", "La", "Mga Panaghoy")),
        Book(26, "Ezekiel", listOf("Ezek", "Eze", "Ezk", "Ezekiel")),
        Book(27, "Daniel", listOf("Dan", "Da", "Dn")),
        Book(28, "Hosea", listOf("Hos", "Ho", "Oseas")),
        Book(29, "Joel", listOf("Jl")),
        Book(30, "Amos", listOf("Am")),
        Book(31, "Obadiah", listOf("Obad", "Ob", "Abdias")),
        Book(32, "Jonah", listOf("Jon", "Jonas")),
        Book(33, "Micah", listOf("Mic", "Mi", "Miqueas")),
        Book(34, "Nahum", listOf("Nah", "Na")),
        Book(35, "Habakkuk", listOf("Hab", "Habacuc")),
        Book(36, "Zephaniah", listOf("Zeph", "Zep", "Zefanias")),
        Book(37, "Haggai", listOf("Hag", "Hageo")),
        Book(38, "Zechariah", listOf("Zech", "Zec", "Zacarias")),
        Book(39, "Malachi", listOf("Mal", "Malaquias")),
        Book(40, "Matthew", listOf("Matt", "Mt", "Mateo")),
        Book(41, "Mark", listOf("Mk", "Mrk", "Marcos")),
        Book(42, "Luke", listOf("Lk", "Luk", "Lucas")),
        Book(43, "John", listOf("Jn", "Joh", "Juan")),
        Book(44, "Acts", listOf("Ac", "Gawa", "Mga Gawa", "Buhat")),
        Book(45, "Romans", listOf("Rom", "Ro", "Rm", "Roma")),
        Book(46, "1 Corinthians", listOf("1 Cor", "1 Co", "I Corinthians", "First Corinthians", "1 Corinto")),
        Book(47, "2 Corinthians", listOf("2 Cor", "2 Co", "II Corinthians", "Second Corinthians", "2 Corinto")),
        Book(48, "Galatians", listOf("Gal", "Ga", "Galacia")),
        Book(49, "Ephesians", listOf("Eph", "Efeso")),
        Book(50, "Philippians", listOf("Phil", "Php", "Pp", "Filipos")),
        Book(51, "Colossians", listOf("Col", "Colosas")),
        Book(52, "1 Thessalonians", listOf("1 Thess", "1 Thes", "1 Th", "I Thessalonians", "1 Tesalonica")),
        Book(53, "2 Thessalonians", listOf("2 Thess", "2 Thes", "2 Th", "II Thessalonians", "2 Tesalonica")),
        Book(54, "1 Timothy", listOf("1 Tim", "1 Ti", "I Timothy", "1 Timoteo")),
        Book(55, "2 Timothy", listOf("2 Tim", "2 Ti", "II Timothy", "2 Timoteo")),
        Book(56, "Titus", listOf("Tit", "Tito")),
        Book(57, "Philemon", listOf("Philem", "Phm", "Filemon")),
        Book(58, "Hebrews", listOf("Heb", "Hebreo")),
        Book(59, "James", listOf("Jas", "Jm", "Santiago")),
        Book(60, "1 Peter", listOf("1 Pet", "1 Pe", "1 Pt", "I Peter", "1 Pedro")),
        Book(61, "2 Peter", listOf("2 Pet", "2 Pe", "2 Pt", "II Peter", "2 Pedro")),
        Book(62, "1 John", listOf("1 Jn", "1 Jo", "I John", "1 Juan")),
        Book(63, "2 John", listOf("2 Jn", "2 Jo", "II John", "2 Juan")),
        Book(64, "3 John", listOf("3 Jn", "3 Jo", "III John", "3 Juan")),
        Book(65, "Jude", listOf("Jud", "Judas")),
        Book(66, "Revelation", listOf("Rev", "Re", "Rv", "Pahayag", "Pinadayag", "Apocalipsis")),
    )

    private val byNum = all.associateBy { it.num }
    private val byAlias: Map<String, Book> = buildMap {
        for (b in all) {
            put(normalize(b.name), b)
            b.aliases.forEach { put(normalize(it), b) }
        }
    }

    fun get(num: Int): Book? = byNum[num]
    fun name(num: Int): String = byNum[num]?.name ?: "Book $num"

    /** Resolve "john", "1 Cor", "1cor", "Mga Awit" ... to a [Book]. */
    fun find(text: String): Book? = byAlias[normalize(text)]

    internal fun normalize(s: String): String =
        s.lowercase().replace(".", "").replace(Regex("\\s+"), "").trim()
}
