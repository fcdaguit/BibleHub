package com.sermonstudio.app.ui.bible

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleBooks
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.db.BookmarkEntity
import com.sermonstudio.app.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One verse number with its text in each displayed version (one version unless comparing). */
data class VerseRow(val verse: Int, val texts: Map<String, String>)

@HiltViewModel
class BibleViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
) : ViewModel() {

    var versions by mutableStateOf<List<BibleVersionEntity>>(emptyList())
        private set
    var versionId by mutableStateOf<String?>(null)
        private set
    var book by mutableStateOf(43)
        private set
    var chapter by mutableStateOf(1)
        private set
    var chapterCount by mutableStateOf(1)
        private set
    var booksPresent by mutableStateOf<List<Int>>(emptyList())
        private set
    var compare by mutableStateOf(false)
        private set
    var compareIds by mutableStateOf<List<String>>(emptyList())
        private set
    var rows by mutableStateOf<List<VerseRow>>(emptyList())
        private set
    var highlightVerse by mutableStateOf(0)
        private set

    val bookmarked: StateFlow<Set<String>> = db.bookmarks().observe()
        .map { list -> list.map { "${it.book}:${it.chapter}:${it.verse}" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        val b = handle.get<Int>("book") ?: 0
        val c = handle.get<Int>("chapter") ?: 0
        val v = handle.get<Int>("verse") ?: 0
        viewModelScope.launch {
            versions = bible.versionsNow()
            versionId = bible.defaultVersionId()
            compareIds = settings.settings.first().compareVersionIds.filter { id -> versions.any { it.id == id } }
            if (b > 0) { book = b; chapter = c.coerceAtLeast(1); highlightVerse = v }
            reload()
        }
    }

    private suspend fun reload() {
        val ids = if (compare && compareIds.size >= 2) compareIds else listOfNotNull(versionId)
        val first = ids.firstOrNull() ?: run { rows = emptyList(); return }
        booksPresent = bible.booksPresent(first)
        if (booksPresent.isNotEmpty() && book !in booksPresent) { book = booksPresent.first(); chapter = 1 }
        chapterCount = bible.chapterCount(first, book).coerceAtLeast(1)
        val perVersion = ids.associateWith { id -> bible.chapter(id, book, chapter).associate { it.verse to it.text } }
        val verseNums = perVersion.values.flatMap { it.keys }.distinct().sorted()
        rows = verseNums.map { n -> VerseRow(n, ids.associateWith { perVersion.getValue(it)[n].orEmpty() }) }
    }

    fun selectVersion(id: String) { versionId = id; highlightVerse = 0; viewModelScope.launch { reload() } }
    fun selectBook(b: Int) { book = b; chapter = 1; highlightVerse = 0; viewModelScope.launch { reload() } }
    fun selectChapter(c: Int) { chapter = c; highlightVerse = 0; viewModelScope.launch { reload() } }

    fun step(delta: Int) {
        val next = chapter + delta
        if (next in 1..chapterCount) selectChapter(next)
        else if (delta > 0) { val i = booksPresent.indexOf(book); if (i in 0 until booksPresent.lastIndex) selectBook(booksPresent[i + 1]) }
        else { val i = booksPresent.indexOf(book); if (i > 0) { book = booksPresent[i - 1]; viewModelScope.launch { chapter = bible.chapterCount(versionId ?: return@launch, book).coerceAtLeast(1); reload() } } }
    }

    fun setCompare(on: Boolean) { compare = on; viewModelScope.launch { reload() } }

    fun toggleCompareVersion(id: String) {
        compareIds = if (id in compareIds) compareIds - id else (compareIds + id).takeLast(3)
        viewModelScope.launch { settings.setCompareVersions(compareIds); reload() }
    }

    fun toggleBookmark(verse: Int) = viewModelScope.launch {
        val existing = db.bookmarks().find(book, chapter, verse)
        if (existing != null) db.bookmarks().delete(existing.id)
        else db.bookmarks().upsert(BookmarkEntity(book = book, chapter = chapter, verse = verse, favorite = false, createdAt = System.currentTimeMillis()))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BibleScreen(onSettings: () -> Unit, vm: BibleViewModel = hiltViewModel()) {
    val bookmarks by vm.bookmarked.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(vm.rows, vm.highlightVerse) {
        val idx = vm.rows.indexOfFirst { it.verse == vm.highlightVerse }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_bible)) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.versions.isEmpty()) {
                Text(stringResource(R.string.no_bible_installed), modifier = Modifier.padding(24.dp))
                return@Column
            }
            // Pickers: version, book, chapter
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(vm.versionId ?: "-", vm.versions.map { it.id }) { vm.selectVersion(it) }
                Picker(BibleBooks.name(vm.book), vm.booksPresent.map { BibleBooks.name(it) }) { name -> BibleBooks.find(name)?.let { vm.selectBook(it.num) } }
                Picker("${vm.chapter}", (1..vm.chapterCount).map { "$it" }) { vm.selectChapter(it.toInt()) }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compare), Modifier.padding(end = 8.dp))
                Switch(checked = vm.compare, onCheckedChange = vm::setCompare, enabled = vm.versions.size > 1)
                if (vm.compare) Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.versions.forEach { v -> FilterChip(selected = v.id in vm.compareIds, onClick = { vm.toggleCompareVersion(v.id) }, label = { Text(v.id) }) }
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.rows, key = { it.verse }) { row ->
                    val marked = "${vm.book}:${vm.chapter}:${row.verse}" in bookmarks
                    val highlighted = row.verse == vm.highlightVerse
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = {},
                            onLongClick = {
                                val t = row.texts.entries.joinToString("\n") { (id, text) -> "${BibleBooks.name(vm.book)} ${vm.chapter}:${row.verse} ($id) — $text" }
                                clipboard.setText(AnnotatedString(t))
                            },
                        ),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                            Text("${row.verse}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp, top = 4.dp))
                            if (row.texts.size == 1) {
                                Text(row.texts.values.first(), Modifier.weight(1f).padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.texts.forEach { (id, text) ->
                                        Column(Modifier.weight(1f)) {
                                            Text(id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            Text(text.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { vm.toggleBookmark(row.verse) }) {
                                Icon(if (marked) Icons.Default.Star else Icons.Default.StarBorder, "Bookmark")
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { vm.step(-1) }) { Icon(Icons.Default.ChevronLeft, null) }
                OutlinedButton(onClick = { vm.step(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
        }
    }
}

@Composable
private fun Picker(label: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
        }
    }
}
