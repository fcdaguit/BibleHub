package com.sermonprep.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.ai.LocalLlm
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.HitKind
import com.sermonprep.app.data.repo.SearchEngine
import com.sermonprep.app.data.repo.SearchHit
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.ui.components.LookupViewModel
import com.sermonprep.app.ui.components.VerseLookupHost
import com.sermonprep.app.ui.components.copyText
import com.sermonprep.app.ui.components.shareText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: SearchEngine,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
    private val llm: LocalLlm,
) : ViewModel() {
    var query by mutableStateOf("")
    var allVersions by mutableStateOf(false)
    var useAi by mutableStateOf(false)
    var aiAvailable by mutableStateOf(false)
        private set
    var hasTranslation by mutableStateOf(true)
        private set
    var loading by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            hasTranslation = bible.versionsOnce().isNotEmpty()
            aiAvailable = llm.hasModel()
        }
    }

    fun run() {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            val versions = bible.versionsOnce()
            hasTranslation = versions.isNotEmpty()
            aiAvailable = llm.hasModel()
            val chosen = settings.versionId.first()
            val primary = versions.firstOrNull { it.id == chosen }?.id ?: versions.firstOrNull()?.id
            results = if (primary == null) emptyList() else runCatching {
                engine.search(
                    query, primary,
                    if (allVersions) versions.map { it.id } else listOf(primary),
                    useAi && aiAvailable,
                )
            }.getOrDefault(emptyList())
            searched = true
            loading = false
        }
    }
}

@Composable
fun SearchScreen(
    onOpenChapter: (BibleRef) -> Unit,
    onOpenSermon: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
    lookup: LookupViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.run() }),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Switch(checked = vm.allVersions, onCheckedChange = { vm.allVersions = it })
            Text(stringResource(R.string.search_all_versions), Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (vm.aiAvailable) Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.useAi, onCheckedChange = { vm.useAi = it })
            Text(stringResource(R.string.search_use_ai), Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium)
        }

        when {
            !vm.hasTranslation -> Text(stringResource(R.string.search_need_translation), Modifier.padding(16.dp))
            vm.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            vm.searched && vm.results.isEmpty() -> Text(stringResource(R.string.search_no_results), Modifier.padding(16.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(vm.results) { _, hit ->
                val plain = hit.ref.label() + " (${hit.versionName})\n" +
                    hit.verses.joinToString("\n") { "${it.verse} ${it.text}" }
                Card(Modifier.fillMaxWidth().clickable { lookup.show(hit.ref) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when (hit.kind) {
                                HitKind.REFERENCE -> stringResource(R.string.search_kind_reference)
                                HitKind.TOPIC -> stringResource(R.string.search_kind_topic, hit.label.orEmpty())
                                HitKind.KEYWORD -> stringResource(R.string.search_kind_keyword)
                                HitKind.AI -> stringResource(R.string.search_kind_ai)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text("${hit.ref.label()} · ${hit.versionName}", style = MaterialTheme.typography.titleMedium)
                        hit.verses.take(6).forEach {
                            Text("${it.verse}  ${it.text}", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        if (hit.verses.size > 6) Text("…", style = MaterialTheme.typography.bodyMedium)

                        if (hit.related.isNotEmpty()) {
                            Text(stringResource(R.string.search_related), style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(hit.related) { r ->
                                    AssistChip(onClick = { lookup.show(r) }, label = { Text(r.label()) })
                                }
                            }
                        }
                        Row {
                            IconButton(onClick = { copyText(ctx, clipboard, plain) }) {
                                Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy))
                            }
                            IconButton(onClick = { shareText(ctx, plain) }) {
                                Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                            }
                            IconButton(onClick = { lookup.addToOutline(hit.ref) }) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.verse_add_to_outline))
                            }
                        }
                    }
                }
            }
        }
    }

    VerseLookupHost(lookup, onOpenChapter = onOpenChapter, onOpenSermon = onOpenSermon)
}
