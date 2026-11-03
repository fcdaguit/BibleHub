package com.sermonstudio.app.ui.search

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.search.SearchEngine
import com.sermonstudio.app.data.search.SearchMode
import com.sermonstudio.app.data.search.SearchResult
import com.sermonstudio.app.ui.common.VersePopup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf as state
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: SearchEngine,
    private val bible: BibleRepository,
) : ViewModel() {
    var query by state("")
    var mode by state(SearchMode.ALL)
    var results by state<List<SearchResult>>(emptyList())
    var busy by state(false)
    var searched by state(false)
    private var job: Job? = null

    fun run() {
        job?.cancel()
        job = viewModelScope.launch {
            busy = true
            val v = bible.defaultVersionId()
            results = if (v == null) emptyList() else engine.search(query, v, mode)
            searched = true
            busy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenChapter: (book: Int, chapter: Int, verse: Int) -> Unit,
    onSettings: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    var popup by remember { mutableStateOf<BibleRef?>(null) }
    val modes = listOf(
        SearchMode.ALL to R.string.mode_all, SearchMode.KEYWORD to R.string.mode_keyword, SearchMode.PHRASE to R.string.mode_phrase,
        SearchMode.TOPIC to R.string.mode_topic, SearchMode.SEMANTIC to R.string.mode_semantic, SearchMode.QUESTION to R.string.mode_question,
    )
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_search)) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (m, label) ->
                    FilterChip(selected = vm.mode == m, onClick = { vm.mode = m; if (vm.query.isNotBlank()) vm.run() }, label = { Text(stringResource(label)) })
                }
            }
            if (vm.busy) CircularProgressIndicator(Modifier.padding(16.dp))
            else if (vm.searched && vm.results.isEmpty()) Text(stringResource(R.string.no_results), Modifier.padding(16.dp))
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.results, key = { it.label + it.score }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(r.sources.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodyLarge)
                            else Text(stringResource(R.string.not_in_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            r.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
                            if (r.crossRefs.isNotEmpty()) {
                                Text(stringResource(R.string.cross_references), style = MaterialTheme.typography.labelMedium)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    r.crossRefs.forEach { x ->
                                        ReferenceParser.parseSingle(x)?.let { ref -> AssistChip(onClick = { popup = ref }, label = { Text(x) }) }
                                    }
                                }
                            }
                            Row {
                                TextButton(onClick = { popup = r.ref }) { Text(stringResource(R.string.nav_bible)) }
                                TextButton(onClick = { onOpenChapter(r.ref.book, r.ref.chapter, r.ref.verseStart ?: 0) }) { Text(stringResource(R.string.open_chapter)) }
                            }
                        }
                    }
                }
            }
        }
    }
    popup?.let { VersePopup(ref = it, onDismiss = { popup = null }, onOpenChapter = onOpenChapter) }
}
