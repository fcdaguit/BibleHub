package com.sermonstudio.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleBooks
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.BookmarkEntity
import com.sermonstudio.app.data.db.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val db: AppDatabase,
    private val bible: BibleRepository,
) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> =
        db.notes().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        db.bookmarks().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNote(n: NoteEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.notes().upsert(n.copy(tags = n.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.joinToString(","), updatedAt = now))
    }

    fun deleteNote(id: Long) = viewModelScope.launch { db.notes().delete(id) }
    fun deleteBookmark(id: Long) = viewModelScope.launch { db.bookmarks().delete(id) }
    fun toggleFavorite(b: BookmarkEntity) = viewModelScope.launch { db.bookmarks().upsert(b.copy(favorite = !b.favorite)) }

    suspend fun verseText(b: BookmarkEntity): String {
        val v = bible.defaultVersionId() ?: return ""
        return bible.passage(v, BibleRef(b.book, b.chapter, b.verse)).firstOrNull()?.text.orEmpty()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenChapter: (book: Int, chapter: Int, verse: Int) -> Unit,
    onSettings: () -> Unit,
    vm: NotesViewModel = hiltViewModel(),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    val allTags = notes.flatMap { it.tags.split(',') }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = notes.filter { tagFilter == null || tagFilter in it.tags.split(',') }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_notes)) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) } },
            )
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = {
                val now = System.currentTimeMillis()
                editing = NoteEntity(body = "", createdAt = now, updatedAt = now)
            }) { Icon(Icons.Default.Add, stringResource(R.string.add_note)) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.notes)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.bookmarks)) })
            }
            if (tab == 0) {
                if (allTags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { t -> FilterChip(selected = tagFilter == t, onClick = { tagFilter = if (tagFilter == t) null else t }, label = { Text("#$t") }) }
                }
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { n ->
                        Card(Modifier.fillMaxWidth().clickable { editing = n }) {
                            Column(Modifier.padding(14.dp)) {
                                if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleMedium)
                                Text(n.body, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                                val meta = listOfNotNull(n.topic.ifBlank { null }, n.verseRef).joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                if (n.tags.isNotBlank()) Text(n.tags.split(',').joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks.sortedByDescending { it.favorite }, key = { it.id }) { b ->
                        var text by remember(b.id) { mutableStateOf("") }
                        androidx.compose.runtime.LaunchedEffect(b.id) { text = vm.verseText(b) }
                        Card(Modifier.fillMaxWidth().clickable { onOpenChapter(b.book, b.chapter, b.verse) }) {
                            Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${BibleBooks.name(b.book)} ${b.chapter}:${b.verse}", style = MaterialTheme.typography.titleMedium)
                                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                }
                                IconButton(onClick = { vm.toggleFavorite(b) }) { Icon(if (b.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite") }
                                IconButton(onClick = { vm.deleteBookmark(b.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { n ->
        var title by remember(n.id) { mutableStateOf(n.title) }
        var body by remember(n.id) { mutableStateOf(n.body) }
        var topic by remember(n.id) { mutableStateOf(n.topic) }
        var tags by remember(n.id) { mutableStateOf(n.tags) }
        var verse by remember(n.id) { mutableStateOf(n.verseRef.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.add_note)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.title)) }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.note_text)) }, minLines = 3)
                    OutlinedTextField(topic, { topic = it }, label = { Text(stringResource(R.string.topic_label)) }, singleLine = true)
                    OutlinedTextField(verse, { verse = it }, label = { Text(stringResource(R.string.reference_hint)) }, singleLine = true)
                    OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags_hint)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveNote(n.copy(title = title, body = body, topic = topic, tags = tags, verseRef = verse.ifBlank { null }))
                    editing = null
                }, enabled = body.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (n.id != 0L) TextButton(onClick = { vm.deleteNote(n.id); editing = null }) { Text(stringResource(R.string.delete)) }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}
