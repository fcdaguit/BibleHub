package com.sermonstudio.app.data

import android.content.Context
import com.sermonstudio.app.data.bible.BibleFormat
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.db.FolderEntity
import com.sermonstudio.app.data.db.SermonEntity
import com.sermonstudio.app.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SampleSermon(val title: String, val category: String = "", val mainText: String = "", val body: String)

/** First launch only: installs the bundled KJV excerpt and three sample outlines so the app is usable at once. */
@Singleton
class SampleDataSeeder @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
) {
    suspend fun seedIfNeeded() {
        if (settings.settings.first().seeded) return
        runCatching {
            bible.installFromAsset(
                BibleVersionEntity("KJV_SAMPLE", "King James Version (sample excerpt)", "en", "Public domain"),
                "bible/sample_kjv.tsv", BibleFormat.TSV,
            )
        }
        runCatching {
            val text = ctx.assets.open("samples/sample_sermons.json").bufferedReader().use { it.readText() }
            val samples = Json { ignoreUnknownKeys = true }.decodeFromString<List<SampleSermon>>(text)
            val folder = db.folders().insert(FolderEntity(name = "Samples"))
            val now = System.currentTimeMillis()
            samples.forEachIndexed { i, s ->
                db.sermons().insert(
                    SermonEntity(title = s.title, body = s.body, folderId = folder, category = s.category,
                        mainText = s.mainText, createdAt = now - i, updatedAt = now - i)
                )
            }
        }
        settings.setSeeded()
    }
}
