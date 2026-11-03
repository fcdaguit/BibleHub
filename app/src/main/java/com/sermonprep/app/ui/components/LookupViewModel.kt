package com.sermonprep.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.data.db.SermonEntity
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.MARK_BOOKMARK
import com.sermonprep.app.data.repo.MARK_FAVORITE
import com.sermonprep.app.data.repo.MarkRepository
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleBooks
import com.sermonprep.app.domain.BibleRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PopupProblem { NONE, NO_TRANSLATION, NOT_IN_TRANSLATION }

data class PopupState(
    val ref: BibleRef,
    val versionName: String?,
    val verses: List<VerseEntity>,
    val bookmarked: Boolean,
    val favorite: Boolean,
    val problem: PopupProblem,
) {
    fun plainText(): String {
        val body = verses.joinToString("\n") { "${it.verse} ${it.text}" }
        val ver = versionName?.let { " ($it)" } ?: ""
        return "${ref.label()}$ver\n$body"
    }
}

/**
 * Shared by every screen that can pop up a verse (editor, reader, search, assistant).
 * The verse text always comes from the translation selected in Settings.
 */
@HiltViewModel
class LookupViewModel @Inject constructor(
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
    private val marks: MarkRepository,
    private val sermons: SermonRepository,
) : ViewModel() {

    var popup by mutableStateOf<PopupState?>(null)
        private set
    var showPicker by mutableStateOf(false)
        private set

    val sermonList: StateFlow<List<SermonEntity>> =
        sermons.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun show(ref: BibleRef) {
        viewModelScope.launch { popup = build(ref) }
    }

    fun dismiss() { popup = null; showPicker = false }

    private suspend fun currentVersion() = bible.versionsOnce().let { all ->
        val chosen = settings.versionId.first()
        all.firstOrNull { it.id == chosen } ?: all.firstOrNull()
    }

    private suspend fun build(ref: BibleRef): PopupState {
        val v = currentVersion()
        val bm = marks.isMarked(MARK_BOOKMARK, ref)
        val fav = marks.isMarked(MARK_FAVORITE, ref)
        if (v == null) return PopupState(ref, null, emptyList(), bm, fav, PopupProblem.NO_TRANSLATION)
        val verses = bible.passage(v.id, ref)
        return PopupState(
            ref, v.name, verses, bm, fav,
            if (verses.isEmpty()) PopupProblem.NOT_IN_TRANSLATION else PopupProblem.NONE,
        )
    }

    /** Verse-by-verse navigation. Moves one verse at a time and rolls over chapter boundaries. */
    fun step(delta: Int) {
        val p = popup ?: return
        val vs = p.ref.verseStart ?: return
        viewModelScope.launch {
            val v = currentVersion() ?: return@launch
            var book = p.ref.book
            var chapter = p.ref.chapter
            var verse = vs + delta
            val count = bible.verseCount(v.id, book, chapter)
            if (verse > count) {
                if (chapter < (BibleBooks.get(book)?.chapters ?: chapter)) { chapter++; verse = 1 }
                else if (book < 66) { book++; chapter = 1; verse = 1 }
                else return@launch
            } else if (verse < 1) {
                if (chapter > 1) { chapter--; verse = bible.verseCount(v.id, book, chapter) }
                else if (book > 1) {
                    book--; chapter = BibleBooks.get(book)?.chapters ?: 1
                    verse = bible.verseCount(v.id, book, chapter)
                } else return@launch
            }
            if (verse >= 1) popup = build(BibleRef(book, chapter, verse))
        }
    }

    fun toggle(type: String) {
        val p = popup ?: return
        viewModelScope.launch {
            marks.toggle(type, p.ref)
            popup = build(p.ref)
        }
    }

    fun toggleBookmark() = toggle(MARK_BOOKMARK)
    fun toggleFavorite() = toggle(MARK_FAVORITE)

    /** "Add to outline" straight from a list row: load the passage, then ask which sermon. */
    fun addToOutline(ref: BibleRef) {
        viewModelScope.launch {
            popup = build(ref)
            showPicker = true
        }
    }

    fun openPicker() { showPicker = true }
    fun closePicker() { showPicker = false }

    private fun outlineLine(p: PopupState): String {
        val ver = p.versionName?.let { " ($it)" } ?: ""
        return "${p.ref.label()}$ver — " + p.verses.joinToString(" ") { it.text }
    }

    fun appendTo(sermonId: Long, onDone: (Long) -> Unit = {}) {
        val p = popup ?: return
        viewModelScope.launch {
            sermons.append(sermonId, outlineLine(p))
            showPicker = false
            onDone(sermonId)
        }
    }

    fun appendToNew(onDone: (Long) -> Unit = {}) {
        val p = popup ?: return
        viewModelScope.launch {
            val id = sermons.create(title = p.ref.label(), content = outlineLine(p))
            showPicker = false
            onDone(id)
        }
    }
}
