package com.sermonstudio.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class, SermonEntity::class, SermonFts::class,
        NoteEntity::class, BookmarkEntity::class,
        BibleVersionEntity::class, VerseEntity::class, VerseFts::class, VerseVectorEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sermons(): SermonDao
    abstract fun folders(): FolderDao
    abstract fun notes(): NoteDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun bible(): BibleDao
    abstract fun vectors(): VectorDao
}
