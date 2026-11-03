package com.sermonprep.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sermonprep.app.data.bible.BibleFormat
import com.sermonprep.app.data.bible.BibleParsers
import com.sermonprep.app.data.bible.ParsedVerse
import com.sermonprep.app.data.db.AppDatabase
import com.sermonprep.app.data.db.BibleDao
import com.sermonprep.app.data.db.BibleVersionEntity
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.domain.BibleRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** An entry of assets/translations.json. A blank [url] means "import from file only". */
data class TranslationSource(
    val id: String,
    val name: String,
    val language: String,
    val format: String,
    val url: String,
    val license: String,
    val note: String,
)

@Singleton
class BibleRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val dao: BibleDao,
) {
    val versions: Flow<List<BibleVersionEntity>> = dao.versions()
    suspend fun versionsOnce() = dao.versionsOnce()

    /** Reads the catalog bundled in assets/translations.json. */
    fun catalog(): List<TranslationSource> {
        val text = ctx.assets.open("translations.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TranslationSource(
                id = o.getString("id"),
                name = o.getString("name"),
                language = o.optString("language"),
                format = o.optString("format", "csv"),
                url = o.optString("url"),
                license = o.optString("license"),
                note = o.optString("note"),
            )
        }
    }

    /** Downloads [url] to a temp file (reporting 0..1 progress) and installs it. */
    suspend fun download(
        id: String, name: String, language: String, url: String, format: BibleFormat,
        onProgress: (Float) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "dl_$id.tmp")
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} from server")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total) * 0.5f)
                    }
                }
            }
            install(id, name, language, tmp, format) { onProgress(0.5f + it * 0.5f) }
        } finally {
            tmp.delete()
        }
    }

    /** Copies the picked file to cache and installs it. */
    suspend fun importFile(
        uri: Uri, id: String, name: String, language: String, format: BibleFormat,
        onProgress: (Float) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "imp_$id.tmp")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: error("Cannot open the selected file")
            install(id, name, language, tmp, format, onProgress)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun install(
        id: String, name: String, language: String, file: File, format: BibleFormat,
        onProgress: (Float) -> Unit,
    ): Int {
        var count = 0
        val estimatedTotal = 31_102f
        db.withTransaction {
            dao.deleteVerses(id)
            dao.deleteVersion(id)
            // A whole Bible is ~31k short rows (a few MB), so parse first, then insert in chunks.
            // The parser callback is not a suspend context, which is why the two steps are separate.
            val all = ArrayList<VerseEntity>(32_000)
            BibleParsers.parse(file, format) { v: ParsedVerse ->
                all += VerseEntity(versionId = id, book = v.book, chapter = v.chapter, verse = v.verse, text = v.text)
                count++
            }
            all.chunked(2000).forEachIndexed { i, chunk ->
                dao.insertVerses(chunk)
                onProgress(((i + 1) * 2000f / estimatedTotal).coerceAtMost(1f))
            }
            if (count == 0) error("No verses could be read. Check the file format.")
            dao.upsertVersion(BibleVersionEntity(id, name, language, count, System.currentTimeMillis()))
        }
        return count
    }

    suspend fun delete(id: String) = db.withTransaction {
        dao.deleteVerses(id)
        dao.deleteVersion(id)
    }

    suspend fun passage(versionId: String, ref: BibleRef): List<VerseEntity> {
        val from = ref.verseStart
        return if (from == null) dao.chapter(versionId, ref.book, ref.chapter)
        else dao.range(versionId, ref.book, ref.chapter, from, ref.verseEnd ?: from)
    }

    suspend fun chapter(versionId: String, book: Int, chapter: Int) = dao.chapter(versionId, book, chapter)
    suspend fun verseCount(versionId: String, book: Int, chapter: Int) = dao.verseCount(versionId, book, chapter)
    suspend fun search(q: String, versionIds: List<String>, limit: Int) = dao.search(q, versionIds, limit)
}
