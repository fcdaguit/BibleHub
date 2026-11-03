package com.sermonprep.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {
    @Query("SELECT * FROM bible_versions ORDER BY name")
    fun versions(): Flow<List<BibleVersionEntity>>

    @Query("SELECT * FROM bible_versions ORDER BY name")
    suspend fun versionsOnce(): List<BibleVersionEntity>

    @Query("SELECT * FROM bible_versions WHERE id = :id")
    suspend fun version(id: String): BibleVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(v: BibleVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(list: List<VerseEntity>)

    @Query("DELETE FROM verses WHERE versionId = :id")
    suspend fun deleteVerses(id: String)

    @Query("DELETE FROM bible_versions WHERE id = :id")
    suspend fun deleteVersion(id: String)

    @Query("SELECT * FROM verses WHERE versionId = :v AND book = :b AND chapter = :c ORDER BY verse")
    suspend fun chapter(v: String, b: Int, c: Int): List<VerseEntity>

    @Query(
        "SELECT * FROM verses WHERE versionId = :v AND book = :b AND chapter = :c " +
            "AND verse BETWEEN :from AND :to ORDER BY verse"
    )
    suspend fun range(v: String, b: Int, c: Int, from: Int, to: Int): List<VerseEntity>

    @Query("SELECT COUNT(*) FROM verses WHERE versionId = :v AND book = :b AND chapter = :c")
    suspend fun verseCount(v: String, b: Int, c: Int): Int

    /** [q] is an FTS4 MATCH expression. The table name (not an alias) must be used with MATCH. */
    @Query(
        "SELECT verses.* FROM verses JOIN verses_fts ON verses.id = verses_fts.rowid " +
            "WHERE verses_fts MATCH :q AND verses.versionId IN (:versionIds) LIMIT :limit"
    )
    suspend fun search(q: String, versionIds: List<String>, limit: Int): List<VerseEntity>
}

@Dao
interface SermonDao {
    @Query("SELECT * FROM sermons ORDER BY updatedAt DESC")
    fun all(): Flow<List<SermonEntity>>

    @Query("SELECT * FROM sermons WHERE id = :id")
    suspend fun get(id: Long): SermonEntity?

    @Query("SELECT COUNT(*) FROM sermons")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SermonEntity): Long

    @Query("DELETE FROM sermons WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MarkDao {
    @Query("SELECT * FROM marks WHERE type = :type ORDER BY createdAt DESC")
    fun byType(type: String): Flow<List<MarkEntity>>

    @Query(
        "SELECT * FROM marks WHERE type = :type AND book = :b AND chapter = :c " +
            "AND verseStart = :vs AND verseEnd = :ve LIMIT 1"
    )
    suspend fun find(type: String, b: Int, c: Int, vs: Int, ve: Int): MarkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MarkEntity): Long

    @Query("DELETE FROM marks WHERE id = :id")
    suspend fun delete(id: Long)
}
