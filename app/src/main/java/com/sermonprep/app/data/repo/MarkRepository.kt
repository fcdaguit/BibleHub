package com.sermonprep.app.data.repo

import com.sermonprep.app.data.db.MarkDao
import com.sermonprep.app.data.db.MarkEntity
import com.sermonprep.app.domain.BibleRef
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

const val MARK_BOOKMARK = "bookmark"
const val MARK_FAVORITE = "favorite"

@Singleton
class MarkRepository @Inject constructor(private val dao: MarkDao) {
    fun byType(type: String): Flow<List<MarkEntity>> = dao.byType(type)

    private fun MarkEntity.toRef() = BibleRef(book, chapter, verseStart, verseEnd.takeIf { it != verseStart })

    fun refOf(m: MarkEntity): BibleRef = m.toRef()

    suspend fun isMarked(type: String, ref: BibleRef): Boolean = find(type, ref) != null

    private suspend fun find(type: String, ref: BibleRef): MarkEntity? {
        val vs = ref.verseStart ?: 0
        return dao.find(type, ref.book, ref.chapter, vs, ref.verseEnd ?: vs)
    }

    /** Adds the mark if absent, removes it if present. Returns the new state. */
    suspend fun toggle(type: String, ref: BibleRef): Boolean {
        val existing = find(type, ref)
        if (existing != null) { dao.delete(existing.id); return false }
        val vs = ref.verseStart ?: 0
        dao.insert(MarkEntity(type = type, book = ref.book, chapter = ref.chapter, verseStart = vs,
            verseEnd = ref.verseEnd ?: vs, createdAt = System.currentTimeMillis()))
        return true
    }

    suspend fun remove(id: Long) = dao.delete(id)
}
