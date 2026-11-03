package com.sermonstudio.app.ui.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.ai.AiRepository
import com.sermonstudio.app.data.ai.GenerationResult
import com.sermonstudio.app.data.ai.OutlineFormatter
import com.sermonstudio.app.data.ai.Recommendation
import com.sermonstudio.app.data.ai.ScriptureRecommender
import com.sermonstudio.app.data.ai.SermonRequest
import com.sermonstudio.app.data.ai.SermonType
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.SermonEntity
import com.sermonstudio.app.ui.common.VersePopup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val ai: AiRepository,
    private val recommender: ScriptureRecommender,
    private val bible: BibleRepository,
    private val db: AppDatabase,
) : ViewModel() {
    var busy by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var result by mutableStateOf<GenerationResult?>(null)
    var recommendation by mutableStateOf<Recommendation?>(null)

    fun generate(topic: String?, passageText: String?, type: SermonType) {
        viewModelScope.launch {
            busy = true; errorMsg = null; result = null
            try {
                val v = bible.defaultVersionId() ?: error("Install a Bible version first (Settings).")
                val passage = passageText?.let { ReferenceParser.parseSingle(it.trim()) }
                if (passageText != null && passage == null) error("Could not read that reference. Try e.g. Romans 8:28.")
                result = ai.generate(SermonRequest(topic = topic?.trim(), passage = passage, type = type, versionId = v))
            } catch (e: Exception) {
                errorMsg = e.message ?: "Something went wrong."
            }
            busy = false
        }
    }

    fun recommend(input: String) { recommendation = recommender.recommend(input) }

    /** Saves the current draft as a normal sermon outline and returns its id. */
    suspend fun saveDraft(): Long? {
        val d = result?.draft ?: return null
        val now = System.currentTimeMillis()
        return db.sermons().insert(
            SermonEntity(title = d.title, body = OutlineFormatter.toMarkup(d), mainText = d.mainText, createdAt = now, updatedAt = now)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onOpenSermon: (Long) -> Unit,
    onOpenChapter: (book: Int, chapter: Int, verse: Int) -> Unit,
    onSettings: () -> Unit,
    vm: AssistantViewModel = hiltViewModel(),
) {
    var tab by remember { mutableStateOf(0) }
    var topic by remember { mutableStateOf("") }
    var passage by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SermonType.TOPICAL) }
    var popup by remember { mutableStateOf<BibleRef?>(null) }
    val scope = rememberCoroutineScope()
    val tabs = listOf(R.string.assistant_topic, R.string.assistant_passage, R.string.assistant_recommend)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_assistant)) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, r -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        OutlinedTextField(topic, { topic = it }, label = { Text(stringResource(R.string.topic_hint)) }, modifier = Modifier.fillMaxWidth())
                        TypeDropdown(type) { type = it }
                        Button(onClick = { vm.generate(topic, null, type) }, enabled = topic.isNotBlank() && !vm.busy) { Text(stringResource(R.string.generate)) }
                    }
                    1 -> {
                        OutlinedTextField(passage, { passage = it }, label = { Text(stringResource(R.string.passage_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TypeDropdown(type) { type = it }
                        Button(onClick = { vm.generate(null, passage, type) }, enabled = passage.isNotBlank() && !vm.busy) { Text(stringResource(R.string.generate)) }
                    }
                    else -> {
                        OutlinedTextField(topic, { topic = it }, label = { Text(stringResource(R.string.topic_hint)) }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { vm.recommend(topic) }, enabled = topic.isNotBlank()) { Text(stringResource(R.string.generate)) }
                    }
                }
                if (vm.busy) CircularProgressIndicator()
                vm.errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (tab < 2) vm.result?.let { r ->
                    r.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    Text(stringResource(R.string.ai_review_notice), style = MaterialTheme.typography.bodySmall)
                    Card(Modifier.fillMaxWidth()) {
                        Text(OutlineFormatter.toMarkup(r.draft), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { scope.launch { vm.saveDraft()?.let(onOpenSermon) } }) { Text(stringResource(R.string.save_as_outline)) }
                }
                if (tab == 2) vm.recommendation?.let { rec -> RecommendationView(rec) { popup = it } }
            }
        }
    }
    popup?.let { VersePopup(ref = it, onDismiss = { popup = null }, onOpenChapter = onOpenChapter) }
}

@Composable
private fun TypeDropdown(selected: SermonType, onSelect: (SermonType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.sermon_type) + ": " + selected.label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SermonType.values().forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { open = false; onSelect(t) }) }
        }
    }
}

@Composable
private fun RecommendationView(rec: Recommendation, onRef: (BibleRef) -> Unit) {
    @Composable
    fun group(title: String, refs: List<String>) {
        if (refs.isEmpty()) return
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            refs.forEach { x -> ReferenceParser.parseSingle(x)?.let { ref -> AssistChip(onClick = { onRef(ref) }, label = { Text(x) }) } }
        }
    }
    if (rec.matchedTopics.isEmpty()) { Text(stringResource(R.string.no_results)); return }
    Text(rec.matchedTopics.joinToString(", "), style = MaterialTheme.typography.titleMedium)
    group("Supporting verses", rec.supporting.map { it.first })
    group("Parallel / cross references", rec.parallels)
    group("Old Testament", rec.oldTestament)
    group("New Testament", rec.newTestament)
    group("Messianic prophecies", rec.messianic)
    if (rec.relatedTopics.isNotEmpty()) Text("Related topics: " + rec.relatedTopics.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
}
