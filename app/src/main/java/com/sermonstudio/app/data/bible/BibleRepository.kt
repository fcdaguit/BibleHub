package com.sermonstudio.app.data.bible

import android.content.Context
import androidx.room.withTransaction
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.db.VerseEntity
import com.sermonstudio.app.data.prefs.SettingsRepository
import com.sermonstudio.app.data.search.VectorIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Progress callback: message, and 0..1 when a determinate fraction is known. */
typealias Progress = (String, Float?) -> Unit

/**
 * Single access point for Bible text: reading passages, installing/removing versions.
 * All text lives in Room, so everything here works offline once a version is installed.
 */
@Singleton
class BibleRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val index: VectorIndex,
    private val settings: SettingsRepository,
) {
    private val dao get() = db.bible()

    val versions: Flow<List<BibleVersionEntity>> get() = dao.observeVersions()

    suspend fun versionsNow(): List<BibleVersionEntity> = dao.versions()

    /** The default version if it is installed, otherwise the first installed one. */
    suspend fun defaultVersionId(): String? {
        val installed = dao.versions()
        val wanted = settings.settings.first().defaultVersionId
        return installed.firstOrNull { it.id == wanted }?.id ?: installed.firstOrNull()?.id
    }

    suspend fun passage(versionId: String, ref: BibleRef): List<VerseEntity> {
        val start = ref.verseStart ?: return dao.chapter(versionId, ref.book, ref.chapter)
        return dao.range(versionId, ref.book, ref.chapter, start, ref.verseEnd ?: start)
    }

    suspend fun chapter(versionId: String, book: Int, chapter: Int) = dao.chapter(versionId, book, chapter)
    suspend fun chapterCount(versionId: String, book: Int): Int = dao.chapterCount(versionId, book) ?: 0
    suspend fun booksPresent(versionId: String): List<Int> = dao.booksPresent(versionId)

    /** "John 3:16 (KJV) - For God so loved ..." ready to paste into a sermon. */
    suspend fun formatForInsert(versionId: String, ref: BibleRef): String =
        formatVerses(ref, versionId, passage(versionId, ref))

    suspend fun delete(versionId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.vectors().deleteFor(versionId)
            dao.deleteVerses(versionId)
            dao.deleteVersion(versionId)
        }
        index.invalidate(versionId)
        if (settings.settings.first().defaultVersionId == versionId) settings.setDefaultVersion(null)
    }

    /**
     * Installs (or re-installs) a version from a stream. Verses go into Room (+FTS index), then the
     * semantic vectors are built. Throws if the file contains no verses.
     */
    suspend fun install(
        meta: BibleVersionEntity,
        input: InputStream,
        format: BibleFormat,
        onProgress: Progress = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            db.withTransaction {
                db.vectors().deleteFor(meta.id)
                dao.deleteVerses(meta.id)
                BibleImporter.parse(input, format).chunked(2000).forEach { chunk ->
                    dao.insertVerses(chunk.map { VerseEntity(versionId = meta.id, book = it.book, chapter = it.chapter, verse = it.verse, text = it.text) })
                    count += chunk.size
                    onProgress("Importing… $count verses", null)
                }
                require(count > 0) { "No verses found. Check the file format (see docs/BIBLE_IMPORT.md)." }
                dao.putVersion(meta.copy(installedAt = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            runCatching { delete(meta.id) }
            throw e
        } finally {
            runCatching { input.close() }
        }
        index.invalidate(meta.id)
        index.build(meta.id) { onProgress("Building search index…", it) }
        if (settings.settings.first().defaultVersionId == null) settings.setDefaultVersion(meta.id)
        count
    }

    suspend fun installFromAsset(meta: BibleVersionEntity, assetPath: String, format: BibleFormat, onProgress: Progress = { _, _ -> }) =
        install(meta, ctx.assets.open(assetPath), format, onProgress)

    /**
     * Installs from a local file that is plain TSV/Zefania XML, or a .zip containing one.
     * [nameHint] (a file name or URL) helps pick the format.
     */
    suspend fun installFromFile(meta: BibleVersionEntity, file: File, nameHint: String?, onProgress: Progress = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val extracted = File(ctx.cacheDir, "bible-extracted.tmp")
            try {
                val isZip = file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }
                var name = nameHint
                val source: File = if (isZip) {
                    ZipInputStream(file.inputStream()).use { zin ->
                        var e = zin.nextEntry
                        while (e != null && !e.name.matches(Regex(".*\\.(xml|tsv|txt)$", RegexOption.IGNORE_CASE))) e = zin.nextEntry
                        requireNotNull(e) { "The zip file contains no .xml / .tsv / .txt Bible file." }
                        name = e.name
                        extracted.outputStream().use { zin.copyTo(it) }
                    }
                    extracted
                } else file
                // (InputStream.readNBytes needs API 33, so read the first bytes by hand)
                val head = source.inputStream().use {
                    val buf = ByteArray(256)
                    val n = it.read(buf).coerceAtLeast(0)
                    String(buf, 0, n, Charsets.UTF_8)
                }
                install(meta, source.inputStream(), BibleImporter.detect(name, head), onProgress)
            } finally {
                extracted.delete()
            }
        }

    /** Downloads [url] (plain TSV/XML, or a .zip containing one) and installs it. */
    suspend fun installFromUrl(meta: BibleVersionEntity, url: String, onProgress: Progress = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "bible-download.tmp")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                require(conn.responseCode in 200..299) { "Download failed: HTTP ${conn.responseCode}" }
                val total = conn.contentLengthLong
                conn.inputStream.use { ins ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress("Downloading…", if (total > 0) done.toFloat() / total else null)
                        }
                    }
                }
                installFromFile(meta, tmp, url.substringBefore('?'), onProgress)
            } finally {
                tmp.delete()
            }
        }
}

/** "John 3:16 (KJV) — text" (verse numbers are added when the passage has several verses). */
fun formatVerses(ref: BibleRef, versionId: String, verses: List<VerseEntity>): String {
    if (verses.isEmpty()) return ref.display()
    val body = if (verses.size == 1) verses[0].text else verses.joinToString(" ") { "${it.verse} ${it.text}" }
    return "${ref.display()} ($versionId) — $body"
}
