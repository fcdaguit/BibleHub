package com.sermonstudio.app.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// User data (backed up / restored as JSON, hence @Serializable)
// ---------------------------------------------------------------------------

@Serializable
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** A sermon outline. [body] is the lightweight markup described in docs/USER_MANUAL.md. */
@Serializable
@Entity(tableName = "sermons", indices = [Index("folderId")])
data class SermonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val folderId: Long? = null,
    val category: String = "",
    val mainText: String = "",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Full-text index over sermons (kept in sync by Room-generated triggers). */
@Fts4(contentEntity = SermonEntity::class)
@Entity(tableName = "sermons_fts")
data class SermonFts(val title: String, val body: String)

@Serializable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String,
    val topic: String = "",
    /** Comma separated tags, lower-case. */
    val tags: String = "",
    val sermonId: Long? = null,
    val verseRef: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "bookmarks", indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val favorite: Boolean = false,
    val label: String = "",
    val createdAt: Long,
)

// ---------------------------------------------------------------------------
// Bible data (re-installable from source files, not part of the user backup)
// ---------------------------------------------------------------------------

@Entity(tableName = "bible_versions")
data class BibleVersionEntity(
    @PrimaryKey val id: String,        // short code, e.g. "KJV"
    val name: String,
    val language: String,
    val license: String = "",
    val installedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "verses",
    indices = [Index(value = ["versionId", "book", "chapter", "verse"], unique = true)],
)
data class VerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: String,
    val book: Int,      // 1..66, Genesis = 1
    val chapter: Int,
    val verse: Int,
    val text: String,
)

/** Keyword / phrase index over verses. Porter tokenizer gives English stemming. */
@Fts4(contentEntity = VerseEntity::class, tokenizer = FtsOptions.TOKENIZER_PORTER)
@Entity(tableName = "verses_fts")
data class VerseFts(val text: String)

/** One int8-quantised embedding per verse for the on-device semantic index. */
@Entity(tableName = "verse_vectors", primaryKeys = ["verseId"], indices = [Index("versionId")])
data class VerseVectorEntity(
    val verseId: Long,
    val versionId: String,
    val vec: ByteArray,
)
