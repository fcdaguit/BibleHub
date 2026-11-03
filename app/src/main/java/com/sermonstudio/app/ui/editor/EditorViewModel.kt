package com.sermonstudio.app.ui.editor

import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.FolderEntity
import com.sermonstudio.app.data.db.SermonEntity
import com.sermonstudio.app.data.io.ExportFormat
import com.sermonstudio.app.data.io.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Editor state: text with undo/redo, debounced auto-save, formatting helpers and scripture insertion.
 * Text is plain with light markup (see MarkupText); styling is done by MarkupTransformation.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val db: AppDatabase,
    private val bible: BibleRepository,
    private val exporter: ExportManager,
) : ViewModel() {

    private val id: Long = handle["id"] ?: 0L
    private var sermon: SermonEntity? = null

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var loaded by mutableStateOf(false)
        private set
    var savedLabel by mutableStateOf("")
        private set
    var archived by mutableStateOf(false)
        private set

    val folders: StateFlow<List<FolderEntity>> =
        db.folders().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val past = ArrayList<String>()
    private val future = ArrayList<String>()
    private var lastEdit = 0L
    private val saveTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    init {
        viewModelScope.launch {
            sermon = db.sermons().get(id)
            sermon?.let { title = it.title; body = TextFieldValue(it.body, TextRange(it.body.length)); archived = it.archived }
            loaded = true
        }
        viewModelScope.launch { saveTrigger.debounce(800).collect { save() } }   // auto-save
    }

    fun onTitleChange(t: String) { title = t; saveTrigger.tryEmit(Unit) }

    fun onBodyChange(v: TextFieldValue) {
        if (v.text != body.text) {
            val now = SystemClock.uptimeMillis()
            if (now - lastEdit > 600 || past.isEmpty()) {          // one undo step per burst of typing
                past.add(body.text)
                if (past.size > 200) past.removeAt(0)
            }
            lastEdit = now
            future.clear()
            saveTrigger.tryEmit(Unit)
        }
        body = v
    }

    fun undo() {
        if (past.isEmpty()) return
        future.add(body.text)
        val t = past.removeAt(past.lastIndex)
        body = TextFieldValue(t, TextRange(t.length)); lastEdit = 0; saveTrigger.tryEmit(Unit)
    }

    fun redo() {
        if (future.isEmpty()) return
        past.add(body.text)
        val t = future.removeAt(future.lastIndex)
        body = TextFieldValue(t, TextRange(t.length)); lastEdit = 0; saveTrigger.tryEmit(Unit)
    }

    // ---- formatting ---------------------------------------------------------------------------
    /** Wrap the selection (or put the cursor between the markers if nothing is selected). */
    fun wrap(prefix: String, suffix: String = prefix) {
        val v = body
        val s = v.selection.min
        val e = v.selection.max
        val sel = v.text.substring(s, e)
        val text = v.text.substring(0, s) + prefix + sel + suffix + v.text.substring(e)
        lastEdit = 0
        onBodyChange(TextFieldValue(text, if (sel.isEmpty()) TextRange(s + prefix.length) else TextRange(s + prefix.length, e + prefix.length)))
    }

    /** Set a line prefix ("# ", "- ", "1. ", "> ") on every line touched by the selection. */
    fun prefixLines(prefix: String, numbered: Boolean = false) {
        val v = body
        val t = v.text
        val ls = t.lastIndexOf('\n', v.selection.min - 1) + 1
        val le = t.indexOf('\n', v.selection.max).let { if (it < 0) t.length else it }
        val strip = Regex("^(#{1,2}\\s|[-*]\\s|\\d+[.)]\\s|>\\s)")
        var n = 0
        val block = t.substring(ls, le).split('\n').joinToString("\n") { line ->
            n++
            (if (numbered) "$n. " else prefix) + line.replace(strip, "")
        }
        lastEdit = 0
        onBodyChange(TextFieldValue(t.substring(0, ls) + block + t.substring(le), TextRange(ls + block.length)))
    }

    fun insertAtCursor(text: String) {
        val v = body
        val s = v.selection.min
        lastEdit = 0
        onBodyChange(TextFieldValue(v.text.substring(0, s) + text + v.text.substring(v.selection.max), TextRange(s + text.length)))
    }

    /** Looks up [reference] in the default Bible version and inserts it as a quote line. Returns false if not found. */
    suspend fun insertScripture(reference: String): Boolean {
        val ref = ReferenceParser.parseSingle(reference.trim()) ?: return false
        val version = bible.defaultVersionId() ?: return false
        if (bible.passage(version, ref).isEmpty()) return false
        insertAtCursor("\n> " + bible.formatForInsert(version, ref) + "\n")
        return true
    }

    // ---- persistence & actions -------------------------------------------------------------------
    private suspend fun save() {
        val cur = sermon ?: return
        val main = Regex("\\*\\*Main Text:\\*\\*\\s*(.+)").find(body.text)?.groupValues?.get(1)?.trim().orEmpty()
        val updated = cur.copy(title = title, body = body.text, mainText = main, archived = archived, updatedAt = System.currentTimeMillis())
        db.sermons().update(updated)
        sermon = updated
        savedLabel = "✓"
    }

    /** Flush immediately (called when leaving the screen). */
    fun saveNow() { viewModelScope.launch { save() } }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so flush on a detached scope.
        CoroutineScope(Dispatchers.IO).launch { withContext(NonCancellable) { save() } }
    }

    fun setArchived(v: Boolean) { archived = v; saveTrigger.tryEmit(Unit) }

    fun moveTo(folderId: Long?) = viewModelScope.launch {
        sermon?.let { sermon = it.copy(folderId = folderId); db.sermons().update(sermon!!) }
    }

    fun duplicate() = viewModelScope.launch {
        val cur = sermon ?: return@launch
        save()
        val now = System.currentTimeMillis()
        db.sermons().insert(cur.copy(id = 0, title = "$title (copy)", body = body.text, archived = false, createdAt = now, updatedAt = now))
    }

    suspend fun exportIntent(f: ExportFormat): Intent {
        save()
        return exporter.shareIntent(exporter.export(sermon!!, f), f)
    }

    /** Distinct references currently in the text, for the quick-access chip row. */
    fun references(): List<BibleRef> = ReferenceParser.findAll(body.text).map { it.ref }.distinct()
}
