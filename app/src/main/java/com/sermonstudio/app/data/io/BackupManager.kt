package com.sermonstudio.app.data.io

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.BookmarkEntity
import com.sermonstudio.app.data.db.FolderEntity
import com.sermonstudio.app.data.db.NoteEntity
import com.sermonstudio.app.data.db.SermonEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the user created. Bible text is not included: it can be re-imported from its source. */
@Serializable
data class BackupFile(
    val format: Int = 1,
    val createdAt: Long,
    val folders: List<FolderEntity>,
    val sermons: List<SermonEntity>,
    val notes: List<NoteEntity>,
    val bookmarks: List<BookmarkEntity>,
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir get() = File(ctx.filesDir, "backups").apply { mkdirs() }

    suspend fun createJson(): String = withContext(Dispatchers.IO) {
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                createdAt = System.currentTimeMillis(),
                folders = db.folders().all(), sermons = db.sermons().all(),
                notes = db.notes().all(), bookmarks = db.bookmarks().all(),
            ),
        )
    }

    /** Writes backups/backup-yyyyMMdd-HHmmss.json inside app storage and keeps the newest 7. */
    suspend fun writeAutoBackup(): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "backup-$stamp.json")
        f.writeText(createJson())
        dir.listFiles { x -> x.name.startsWith("backup-") }?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
        f
    }

    fun listBackups(): List<File> = dir.listFiles { x -> x.name.startsWith("backup-") }?.sortedByDescending { it.name } ?: emptyList()

    /** [replace] = wipe current user data first; otherwise records are merged (same ids are overwritten). */
    suspend fun restore(text: String, replace: Boolean): Int = withContext(Dispatchers.IO) {
        val b = json.decodeFromString(BackupFile.serializer(), text)
        db.withTransaction {
            if (replace) {
                db.sermons().deleteAll(); db.folders().deleteAll(); db.notes().deleteAll(); db.bookmarks().deleteAll()
            }
            b.folders.forEach { db.folders().upsert(it) }
            b.sermons.forEach { db.sermons().upsert(it) }
            b.notes.forEach { db.notes().upsert(it) }
            b.bookmarks.forEach { db.bookmarks().upsert(it) }
        }
        b.sermons.size + b.notes.size + b.bookmarks.size
    }
}

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        backups.writeAutoBackup(); Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

object BackupScheduler {
    private const val NAME = "auto_backup"
    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build())
        } else wm.cancelUniqueWork(NAME)
    }
}
