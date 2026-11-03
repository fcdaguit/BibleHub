package com.sermonprep.app.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.ai.AssistantResult
import com.sermonprep.app.ai.SermonAssistant
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.ui.components.LinkedText
import com.sermonprep.app.ui.components.LookupViewModel
import com.sermonprep.app.ui.components.VerseLookupHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistant: SermonAssistant,
    private val sermons: SermonRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    var topic by mutableStateOf("")
    var running by mutableStateOf(false)
        private set
    var result by mutableStateOf<AssistantResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun generate() {
        if (topic.isBlank() || running) return
        viewModelScope.launch {
            running = true; error = null
            val lang = when (settings.language.first()) {
                "fil" -> "Tagalog"
                "ceb" -> "Cebuano (Bisaya)"
                else -> "English"
            }
            runCatching { assistant.generate(topic.trim(), lang) }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: it.toString() }
            running = false
        }
    }

    fun saveAsSermon(onDone: (Long) -> Unit) {
        val r = result ?: return
        viewModelScope.launch { onDone(sermons.create(topic.trim(), r.text)) }
    }
}

@Composable
fun AssistantScreen(
    onOpenChapter: (BibleRef) -> Unit,
    onOpenSermon: (Long) -> Unit,
    vm: AssistantViewModel = hiltViewModel(),
    lookup: LookupViewModel = hiltViewModel(),
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = vm.topic,
            onValueChange = { vm.topic = it },
            placeholder = { Text(stringResource(R.string.assistant_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = vm::generate, enabled = !vm.running && vm.topic.isNotBlank()) {
            Text(stringResource(if (vm.running) R.string.assistant_working else R.string.assistant_generate))
        }
        if (vm.running) CircularProgressIndicator()
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (r.usedModel) R.string.assistant_model_note else R.string.assistant_template_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (r.unverified.isNotEmpty()) Text(
                        stringResource(R.string.assistant_unverified, r.unverified.joinToString { it.label() }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    LinkedText(r.text, onRef = lookup::show, modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedButton(onClick = { vm.saveAsSermon(onOpenSermon) }) {
                Text(stringResource(R.string.assistant_save))
            }
        }
    }
    VerseLookupHost(lookup, onOpenChapter = onOpenChapter, onOpenSermon = onOpenSermon)
}
