package com.sermonstudio.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SermonDao {
    @Query("SELECT * FROM sermons WHERE archived = :archived ORDER BY updatedAt DESC")
    fun observe(archived: Boolean): Flow<List<SermonEntity>>

    @Query(
        "SELECT sermons.* FROM sermons JOIN sermons_fts ON (sermons.id = sermons_fts.docid) " +
            "WHERE sermons_fts MATCH :q ORDER BY sermons.updatedAt DESC"
    )
    fun search(q: String): Flow<List<SermonEntity>>

    @Query("SELECT * FROM sermons WHERE id = :id")
    suspend fun get(id: Long): SermonEntity?

    @Query("SELECT * FROM sermons")
    suspend fun all(): List<SermonEntity>

    @Insert suspend fun insert(s: SermonEntity): Long
    @Update suspend fun update(s: SermonEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: SermonEntity)
    @Query("DELETE FROM sermons WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM sermons") suspend fun deleteAll()
    @Query("UPDATE sermons SET folderId = NULL WHERE folderId = :folderId") suspend fun clearFolder(folderId: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name") fun observe(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders") suspend fun all(): List<FolderEntity>
    @Insert suspend fun insert(f: FolderEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(f: FolderEntity)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM folders") suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun observe(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes") suspend fun all(): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(n: NoteEntity): Long
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM notes") suspend fun deleteAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun observe(): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks") suspend fun all(): List<BookmarkEntity>
    @Query("SELECT * FROM bookmarks WHERE book = :b AND chapter = :c AND verse = :v LIMIT 1")
    suspend fun find(b: Int, c: Int, v: Int): BookmarkEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(b: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM bookmarks") suspend fun deleteAll()
}

/** Row type for loading vectors without the version column. */
class VectorRow(val verseId: Long, val vec: ByteArray)

@Dao
interface BibleDao {
    @Query("SELECT * FROM bible_versions ORDER BY name") fun observeVersions(): Flow<List<BibleVersionEntity>>
    @Query("SELECT * FROM bible_versions ORDER BY name") suspend fun versions(): List<BibleVersionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVersion(v: BibleVersionEntity)
    @Query("DELETE FROM bible_versions WHERE id = :id") suspend fun deleteVersion(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVerses(v: List<VerseEntity>)
    @Query("DELETE FROM verses WHERE versionId = :id") suspend fun deleteVerses(id: String)
    @Query("SELECT COUNT(*) FROM verses WHERE versionId = :id") suspend fun countVerses(id: String): Int

    @Query("SELECT * FROM verses WHERE versionId = :v AND book = :b AND chapter = :c ORDER BY verse")
    suspend fun chapter(v: String, b: Int, c: Int): List<VerseEntity>

    @Query(
        "SELECT * FROM verses WHERE versionId = :v AND book = :b AND chapter = :c " +
            "AND verse BETWEEN :from AND :to ORDER BY verse"
    )
    suspend fun range(v: String, b: Int, c: Int, from: Int, to: Int): List<VerseEntity>

    @Query("SELECT MAX(chapter) FROM verses WHERE versionId = :v AND book = :b")
    suspend fun chapterCount(v: String, b: Int): Int?

    @Query("SELECT DISTINCT book FROM verses WHERE versionId = :v ORDER BY book")
    suspend fun booksPresent(v: String): List<Int>

    @Query(
        "SELECT verses.* FROM verses JOIN verses_fts ON (verses.id = verses_fts.docid) " +
            "WHERE verses_fts MATCH :q AND verses.versionId = :v LIMIT :limit"
    )
    suspend fun ftsSearch(q: String, v: String, limit: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE id IN (:ids)") suspend fun byIds(ids: List<Long>): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE versionId = :v AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(v: String, afterId: Long, limit: Int): List<VerseEntity>
}

@Dao
interface VectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(rows: List<VerseVectorEntity>)
    @Query("DELETE FROM verse_vectors WHERE versionId = :v") suspend fun deleteFor(v: String)
    @Query("SELECT verseId, vec FROM verse_vectors WHERE versionId = :v") suspend fun forVersion(v: String): List<VectorRow>
}
