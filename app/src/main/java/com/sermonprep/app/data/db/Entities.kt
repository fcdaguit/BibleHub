package com.sermonprep.app.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/** A downloaded / imported Bible translation. */
@Entity(tableName = "bible_versions")
data class BibleVersionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val language: String,
    val verseCount: Int,
    val downloadedAt: Long,
)

/** One verse of one translation. [book] is 1..66 in canonical order. */
@Entity(
    tableName = "verses",
    indices = [Index(value = ["versionId", "book", "chapter", "verse"], unique = true)],
)
data class VerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)

/** Full-text index over [VerseEntity.text]; Room keeps it in sync through triggers. */
@Fts4(contentEntity = VerseEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "verses_fts")
data class VerseFts(val text: String)

@Entity(tableName = "sermons")
data class SermonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** type is "bookmark" or "favorite". Marks are translation-independent. */
@Entity(
    tableName = "marks",
    indices = [Index(value = ["type", "book", "chapter", "verseStart", "verseEnd"], unique = true)],
)
data class MarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val book: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val createdAt: Long,
)
