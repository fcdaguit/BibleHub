package com.sermonprep.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BibleVersionEntity::class,
        VerseEntity::class,
        VerseFts::class,
        SermonEntity::class,
        MarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
    abstract fun sermonDao(): SermonDao
    abstract fun markDao(): MarkDao
}
