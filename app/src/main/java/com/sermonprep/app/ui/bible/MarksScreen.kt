package com.sermonprep.app.ui.bible

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.MARK_BOOKMARK
import com.sermonprep.app.data.repo.MARK_FAVORITE
import com.sermonprep.app.data.repo.MarkRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarkItem(val id: Long, val ref: BibleRef, val preview: String)

@HiltViewModel
class MarksViewModel @Inject constructor(
    private val marks: MarkRepository,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private suspend fun items(type: String, list: List<com.sermonprep.app.data.db.MarkEntity>): List<MarkItem> {
        val versions = bible.versionsOnce()
        val chosen = settings.versionId.first()
        val vid = versions.firstOrNull { it.id == chosen }?.id ?: versions.firstOrNull()?.id
        return list.map { m ->
            val ref = marks.refOf(m)
            val text = vid?.let { bible.passage(it, ref).joinToString(" ") { v -> v.text } }.orEmpty()
            MarkItem(m.id, ref, text)
        }
    }

    private fun flowFor(type: String) =
        combine(marks.byType(type), bible.versions, settings.versionId) { list, _, _ -> list }
            .let { f -> kotlinx.coroutines.flow.flow { f.collect { emit(items(type, it)) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks = flowFor(MARK_BOOKMARK)
    val favorites = flowFor(MARK_FAVORITE)

    fun remove(id: Long) { viewModelScope.launch { marks.remove(id) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksScreen(onBack: () -> Unit, onOpenChapter: (BibleRef) -> Unit, vm: MarksViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val bookmarks by vm.bookmarks.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val list = if (tab == 0) bookmarks else favorites

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.marks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.marks_bookmarks)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.marks_favorites)) })
            }
            if (list.isEmpty()) Text(stringResource(R.string.marks_empty), Modifier.padding(24.dp))
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenChapter(item.ref) }) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.ref.label(), style = MaterialTheme.typography.titleMedium)
                                if (item.preview.isNotBlank()) Text(
                                    item.preview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            IconButton(onClick = { vm.remove(item.id) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}
