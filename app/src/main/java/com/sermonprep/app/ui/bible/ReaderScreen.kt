package com.sermonprep.app.ui.bible

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleBooks
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.ui.components.LookupViewModel
import com.sermonprep.app.ui.components.VerseLookupHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    var book by mutableStateOf(handle["book"] ?: 43)
        private set
    var chapter by mutableStateOf(handle["chapter"] ?: 1)
        private set
    /** Verse to highlight/scroll to (0 = none). */
    var focusVerse by mutableStateOf(handle["verse"] ?: 0)
        private set
    var verses by mutableStateOf<List<VerseEntity>>(emptyList())
        private set
    var versionName by mutableStateOf<String?>(null)
        private set
    var hasTranslation by mutableStateOf(true)
        private set

    private var versionId: String? = null

    init {
        viewModelScope.launch {
            combine(settings.versionId, bible.versions) { chosen, all ->
                all.firstOrNull { it.id == chosen } ?: all.firstOrNull()
            }.collect { v ->
                versionId = v?.id
                versionName = v?.name
                hasTranslation = v != null
                reload()
            }
        }
    }

    private suspend fun reload() {
        val id = versionId ?: run { verses = emptyList(); return }
        verses = bible.chapter(id, book, chapter)
    }

    fun go(book: Int, chapter: Int) {
        this.book = book; this.chapter = chapter; focusVerse = 0
        viewModelScope.launch { reload() }
    }

    fun next() {
        val b = BibleBooks.get(book) ?: return
        when {
            chapter < b.chapters -> go(book, chapter + 1)
            book < 66 -> go(book + 1, 1)
        }
    }

    fun previous() {
        when {
            chapter > 1 -> go(book, chapter - 1)
            book > 1 -> go(book - 1, BibleBooks.get(book - 1)?.chapters ?: 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onOpenMarks: () -> Unit,
    onOpenSermon: (Long) -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
    lookup: LookupViewModel = hiltViewModel(),
) {
    var bookMenu by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val book = BibleBooks.get(vm.book)

    LaunchedEffect(vm.verses, vm.focusVerse) {
        if (vm.focusVerse > 0) {
            val idx = vm.verses.indexOfFirst { it.verse == vm.focusVerse }
            if (idx >= 0) listState.scrollToItem(idx) else Unit
        } else if (vm.verses.isNotEmpty()) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { bookMenu = true }) { Text(book?.name ?: "?") }
                            DropdownMenu(expanded = bookMenu, onDismissRequest = { bookMenu = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                                BibleBooks.all.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.name) },
                                        onClick = { bookMenu = false; vm.go(b.id, 1) },
                                    )
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { chapterMenu = true }) { Text("${vm.chapter}") }
                            DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                (1..(book?.chapters ?: 1)).forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("$c") },
                                        onClick = { chapterMenu = false; vm.go(vm.book, c) },
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMarks) { Icon(Icons.Filled.Bookmarks, stringResource(R.string.marks_title)) }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = vm::previous) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_previous)) }
                Text(
                    vm.versionName.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = vm::next) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_next)) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!vm.hasTranslation) {
                Text(stringResource(R.string.reader_no_translation), Modifier.padding(24.dp))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(vm.verses, key = { _, v -> v.id }) { _, v ->
                        val highlighted = v.verse == vm.focusVerse
                        Text(
                            "${v.verse}  ${v.text}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { lookup.show(BibleRef(vm.book, vm.chapter, v.verse)) }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    VerseLookupHost(
        lookup,
        onOpenChapter = { r -> vm.go(r.book, r.chapter) },
        onOpenSermon = onOpenSermon,
    )
}
