package com.sermonprep.app.domain

data class Book(
    val id: Int,
    val name: String,
    val chapters: Int,
    val aliases: List<String>,
)

/** The 66 books of the Protestant canon, in canonical order. */
object BibleBooks {
    private fun b(id: Int, name: String, chapters: Int, vararg aliases: String) =
        Book(id, name, chapters, listOf(name) + aliases)

    val all: List<Book> = listOf(
        b(1, "Genesis", 50, "Gen", "Gn", "Genesi"),
        b(2, "Exodus", 40, "Exod", "Ex", "Exo", "Exodo"),
        b(3, "Leviticus", 27, "Lev", "Lv", "Levitico"),
        b(4, "Numbers", 36, "Num", "Nm", "Mga Bilang"),
        b(5, "Deuteronomy", 34, "Deut", "Dt", "Deuteronomio"),
        b(6, "Joshua", 24, "Josh", "Jos", "Josue"),
        b(7, "Judges", 21, "Judg", "Jdg", "Mga Hukom"),
        b(8, "Ruth", 4, "Rut"),
        b(9, "1 Samuel", 31, "1 Sam", "1 Sm", "1 Samuel"),
        b(10, "2 Samuel", 24, "2 Sam", "2 Sm"),
        b(11, "1 Kings", 22, "1 Kgs", "1 Ki", "1 Hari"),
        b(12, "2 Kings", 25, "2 Kgs", "2 Ki", "2 Hari"),
        b(13, "1 Chronicles", 29, "1 Chron", "1 Chr", "1 Cronica"),
        b(14, "2 Chronicles", 36, "2 Chron", "2 Chr", "2 Cronica"),
        b(15, "Ezra", 10, "Ezr", "Esdras"),
        b(16, "Nehemiah", 13, "Neh", "Nehemias"),
        b(17, "Esther", 10, "Esth", "Est"),
        b(18, "Job", 42, "Jb"),
        b(19, "Psalms", 150, "Psalm", "Ps", "Psa", "Pslm", "Salmo", "Mga Awit", "Awit"),
        b(20, "Proverbs", 31, "Prov", "Pro", "Prv", "Kawikaan", "Mga Kawikaan", "Proverbio"),
        b(21, "Ecclesiastes", 12, "Eccles", "Eccl", "Ecc", "Eclesiastes"),
        b(22, "Song of Solomon", 8, "Song of Songs", "Song", "SOS", "Canticles", "Awit ng mga Awit"),
        b(23, "Isaiah", 66, "Isa", "Isaias"),
        b(24, "Jeremiah", 52, "Jer", "Jeremias"),
        b(25, "Lamentations", 5, "Lam", "Mga Panaghoy", "Panaghoy"),
        b(26, "Ezekiel", 48, "Ezek", "Eze", "Ezequiel"),
        b(27, "Daniel", 12, "Dan"),
        b(28, "Hosea", 14, "Hos", "Oseas"),
        b(29, "Joel", 3, "Joe"),
        b(30, "Amos", 9, "Am"),
        b(31, "Obadiah", 1, "Obad", "Oba", "Abdias"),
        b(32, "Jonah", 4, "Jon", "Jonas"),
        b(33, "Micah", 7, "Mic", "Mikas"),
        b(34, "Nahum", 3, "Nah", "Nam"),
        b(35, "Habakkuk", 3, "Hab", "Habacuc"),
        b(36, "Zephaniah", 3, "Zeph", "Zep", "Zefanias"),
        b(37, "Haggai", 2, "Hag", "Hagai"),
        b(38, "Zechariah", 14, "Zech", "Zec", "Zacarias"),
        b(39, "Malachi", 4, "Mal", "Malakias"),
        b(40, "Matthew", 28, "Matt", "Mt", "Mateo"),
        b(41, "Mark", 16, "Mrk", "Mk", "Marcos"),
        b(42, "Luke", 24, "Luk", "Lk", "Lucas"),
        b(43, "John", 21, "Jhn", "Jn", "Juan"),
        b(44, "Acts", 28, "Act", "Mga Gawa", "Gawa", "Binuhatan"),
        b(45, "Romans", 16, "Rom", "Rm", "Roma", "Mga Taga-Roma"),
        b(46, "1 Corinthians", 16, "1 Cor", "1 Co", "1 Corinto", "1 Mga Taga-Corinto"),
        b(47, "2 Corinthians", 13, "2 Cor", "2 Co", "2 Corinto", "2 Mga Taga-Corinto"),
        b(48, "Galatians", 6, "Gal", "Galacia", "Mga Taga-Galacia"),
        b(49, "Ephesians", 6, "Eph", "Efeso", "Mga Taga-Efeso"),
        b(50, "Philippians", 4, "Phil", "Php", "Filipos", "Mga Taga-Filipos"),
        b(51, "Colossians", 4, "Col", "Colosas", "Mga Taga-Colosas"),
        b(52, "1 Thessalonians", 5, "1 Thess", "1 Thes", "1 Th", "1 Tesalonica", "1 Mga Taga-Tesalonica"),
        b(53, "2 Thessalonians", 3, "2 Thess", "2 Thes", "2 Th", "2 Tesalonica", "2 Mga Taga-Tesalonica"),
        b(54, "1 Timothy", 6, "1 Tim", "1 Ti", "1 Timoteo"),
        b(55, "2 Timothy", 4, "2 Tim", "2 Ti", "2 Timoteo"),
        b(56, "Titus", 3, "Tit", "Tito"),
        b(57, "Philemon", 1, "Philem", "Phm", "Filemon"),
        b(58, "Hebrews", 13, "Heb", "Hebreo", "Mga Hebreo"),
        b(59, "James", 5, "Jas", "Jm", "Santiago"),
        b(60, "1 Peter", 5, "1 Pet", "1 Pe", "1 Pedro"),
        b(61, "2 Peter", 3, "2 Pet", "2 Pe", "2 Pedro"),
        b(62, "1 John", 5, "1 Jhn", "1 Jn", "1 Juan"),
        b(63, "2 John", 1, "2 Jhn", "2 Jn", "2 Juan"),
        b(64, "3 John", 1, "3 Jhn", "3 Jn", "3 Juan"),
        b(65, "Jude", 1, "Jud", "Judas"),
        b(66, "Revelation", 22, "Revelation of John", "Rev", "Rv", "Apocalipsis", "Pahayag", "Pinadayag"),
    )

    private val byId: Map<Int, Book> = all.associateBy { it.id }
    fun get(id: Int): Book? = byId[id]
    fun name(id: Int): String = byId[id]?.name ?: "?"
}
