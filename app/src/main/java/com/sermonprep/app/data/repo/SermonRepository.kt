package com.sermonprep.app.data.repo

import android.content.Context
import com.sermonprep.app.data.db.SermonDao
import com.sermonprep.app.data.db.SermonEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SermonRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: SermonDao,
    private val settings: SettingsRepository,
) {
    val all: Flow<List<SermonEntity>> = dao.all()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun create(title: String = "", content: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.upsert(SermonEntity(title = title, content = content, createdAt = now, updatedAt = now))
    }

    suspend fun save(id: Long, title: String, content: String) {
        val old = dao.get(id) ?: return
        dao.upsert(old.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun duplicate(id: Long): Long? {
        val s = dao.get(id) ?: return null
        return create(s.title + " (copy)", s.content)
    }

    /** Appends [text] to the end of an outline. */
    suspend fun append(id: Long, text: String) {
        val s = dao.get(id) ?: return
        val sep = if (s.content.isBlank()) "" else if (s.content.endsWith("\n")) "\n" else "\n\n"
        save(id, s.title, s.content + sep + text)
    }

    /** Loads assets/sample_sermons.json the first time the app runs. */
    suspend fun seedSamplesIfNeeded() = withContext(Dispatchers.IO) {
        if (settings.isSeeded()) return@withContext
        if (dao.count() == 0) {
            val text = ctx.assets.open("sample_sermons.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                create(o.getString("title"), o.getString("content"))
            }
        }
        settings.markSeeded()
    }
}
