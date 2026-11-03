package com.sermonprep.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.ai.LocalLlm
import com.sermonprep.app.data.bible.BibleFormat
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.data.repo.TranslationSource
import com.sermonprep.app.io.DocumentIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferJob(val progress: Float, val error: String? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
    private val llm: LocalLlm,
    private val io: DocumentIO,
) : ViewModel() {
    val catalog: List<TranslationSource> = bible.catalog()
    val installed = bible.versions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val theme = settings.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val selected = settings.versionId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Active or failed downloads/imports, keyed by translation id. */
    val jobs = mutableStateMapOf<String, TransferJob>()

    var modelName by mutableStateOf<String?>(null)
        private set
    var modelProgress by mutableStateOf<Float?>(null)
        private set
    var modelError by mutableStateOf<String?>(null)
        private set

    init { refreshModel() }

    private fun refreshModel() { viewModelScope.launch { modelName = llm.modelName() } }

    fun setTheme(v: String) { viewModelScope.launch { settings.setTheme(v) } }

    fun setLanguage(tag: String) {
        viewModelScope.launch { settings.setLanguage(tag) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun select(id: String) { viewModelScope.launch { settings.setVersion(id) } }

    private fun run(id: String, block: suspend ((Float) -> Unit) -> Unit) {
        if (jobs[id]?.error == null && jobs.containsKey(id)) return
        jobs[id] = TransferJob(0f)
        viewModelScope.launch {
            runCatching { block { p -> jobs[id] = TransferJob(p) } }
                .onSuccess {
                    jobs.remove(id)
                    if (settings.versionId.first() == null) settings.setVersion(id)
                }
                .onFailure { jobs[id] = TransferJob(0f, it.message ?: it.toString()) }
        }
    }

    fun download(src: TranslationSource) = run(src.id) { p ->
        bible.download(src.id, src.name, src.language, src.url, BibleFormat.fromKey(src.format), p)
    }

    fun downloadCustom(id: String, name: String, lang: String, url: String, format: BibleFormat) = run(id) { p ->
        bible.download(id, name, lang, url, format, p)
    }

    fun importFile(uri: Uri, id: String, name: String, lang: String, format: BibleFormat) = run(id) { p ->
        bible.importFile(uri, id, name, lang, format, p)
    }

    fun dismissError(id: String) { jobs.remove(id) }

    fun remove(id: String) {
        viewModelScope.launch {
            bible.delete(id)
            if (settings.versionId.first() == id) {
                bible.versionsOnce().firstOrNull()?.let { settings.setVersion(it.id) }
            }
        }
    }

    fun fileName(uri: Uri) = io.displayName(uri)

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            modelError = null; modelProgress = 0f
            runCatching { llm.importModel(uri, io.displayName(uri)) { modelProgress = it } }
                .onFailure { modelError = it.message ?: it.toString() }
            modelProgress = null
            refreshModel()
        }
    }

    fun removeModel() { viewModelScope.launch { llm.removeModel(); refreshModel() } }
}

/** What the "add translation" dialog is doing. */
private sealed interface AddDialog {
    data class FromFile(val uri: Uri, val suggestedName: String) : AddDialog
    data object FromUrl : AddDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val installed by vm.installed.collectAsState()
    val theme by vm.theme.collectAsState()
    val language by vm.language.collectAsState()
    val selected by vm.selected.collectAsState()
    var dialog by remember { mutableStateOf<AddDialog?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = AddDialog.FromFile(uri, vm.fileName(uri))
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    val effectiveSelected = installed.firstOrNull { it.id == selected }?.id ?: installed.firstOrNull()?.id

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Appearance
            item { SectionTitle(R.string.settings_appearance) }
            item {
                Column {
                    Radio(R.string.theme_system, theme == "system") { vm.setTheme("system") }
                    Radio(R.string.theme_light, theme == "light") { vm.setTheme("light") }
                    Radio(R.string.theme_dark, theme == "dark") { vm.setTheme("dark") }
                }
            }

            // Language
            item { SectionTitle(R.string.settings_language) }
            item {
                Column {
                    Radio(R.string.lang_system, language == "") { vm.setLanguage("") }
                    Radio(R.string.lang_en, language == "en") { vm.setLanguage("en") }
                    Radio(R.string.lang_fil, language == "fil") { vm.setLanguage("fil") }
                    Radio(R.string.lang_ceb, language == "ceb") { vm.setLanguage("ceb") }
                }
            }

            // Preferred translation
            if (installed.isNotEmpty()) {
                item { SectionTitle(R.string.settings_preferred) }
                item {
                    Column {
                        installed.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.select(v.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = v.id == effectiveSelected, onClick = { vm.select(v.id) })
                                Text(v.name)
                            }
                        }
                    }
                }
            }

            // Translations catalog
            item { SectionTitle(R.string.settings_versions) }
            items(vm.catalog.size) { i ->
                val src = vm.catalog[i]
                val isInstalled = installed.firstOrNull { it.id == src.id }
                val job = vm.jobs[src.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(src.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.versions_license, src.license), style = MaterialTheme.typography.labelSmall)
                        if (src.note.isNotBlank()) Text(src.note, style = MaterialTheme.typography.labelSmall)
                        if (isInstalled != null) Text(
                            stringResource(R.string.versions_installed) + " · " +
                                stringResource(R.string.versions_verses, isInstalled.verseCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (job != null && job.error == null) LinearProgressIndicator(
                            progress = { job.progress }, modifier = Modifier.fillMaxWidth(),
                        )
                        job?.error?.let {
                            Text(stringResource(R.string.versions_error, it), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { vm.dismissError(src.id) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (src.url.isNotBlank() && job?.error == null && job == null) {
                                Button(onClick = { vm.download(src) }) {
                                    Text(stringResource(R.string.versions_download))
                                }
                            } else if (src.url.isNotBlank() && job?.error != null) {
                                Button(onClick = { vm.dismissError(src.id); vm.download(src) }) {
                                    Text(stringResource(R.string.versions_download))
                                }
                            }
                            if (isInstalled != null) OutlinedButton(onClick = { vm.remove(src.id) }) {
                                Text(stringResource(R.string.versions_delete))
                            }
                        }
                    }
                }
            }
            // Custom / imported translations that are not in the catalog
            items(installed.filter { v -> vm.catalog.none { it.id == v.id } }.size) { i ->
                val v = installed.filter { x -> vm.catalog.none { it.id == x.id } }[i]
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.versions_verses, v.verseCount), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { vm.remove(v.id) }) { Text(stringResource(R.string.versions_delete)) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.versions_import)) }
                    OutlinedButton(onClick = { dialog = AddDialog.FromUrl }) { Text(stringResource(R.string.versions_custom)) }
                }
            }
            // Per-job errors for custom/imported ids that are not in the catalog
            vm.jobs.filter { (id, _) -> vm.catalog.none { it.id == id } }.forEach { (id, j) ->
                item(key = "job_$id") {
                    if (j.error == null) {
                        Column { Text(id); LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth()) }
                    } else {
                        Text(stringResource(R.string.versions_error, j.error), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { vm.dismissError(id) })
                    }
                }
            }

            // Offline AI model
            item { SectionTitle(R.string.settings_ai) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(vm.modelName?.let { stringResource(R.string.ai_status_installed, it) }
                        ?: stringResource(R.string.ai_status_none))
                    vm.modelProgress?.let {
                        Text(stringResource(R.string.ai_importing), style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    }
                    vm.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = vm.modelProgress == null) {
                            Text(stringResource(R.string.ai_import))
                        }
                        if (vm.modelName != null) OutlinedButton(onClick = vm::removeModel) {
                            Text(stringResource(R.string.ai_remove))
                        }
                    }
                    Text(stringResource(R.string.ai_note), style = MaterialTheme.typography.labelSmall)
                }
            }

            item { SectionTitle(R.string.settings_about) }
            item { Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall) }
        }
    }

    when (val d = dialog) {
        is AddDialog.FromFile -> AddTranslationDialog(
            showUrl = false,
            initialName = d.suggestedName.substringBeforeLast('.'),
            initialFormat = BibleFormat.guess(d.suggestedName),
            onDismiss = { dialog = null },
            onConfirm = { id, name, lang, _, fmt -> vm.importFile(d.uri, id, name, lang, fmt); dialog = null },
        )
        AddDialog.FromUrl -> AddTranslationDialog(
            showUrl = true,
            initialName = "",
            initialFormat = BibleFormat.CSV,
            onDismiss = { dialog = null },
            onConfirm = { id, name, lang, url, fmt -> vm.downloadCustom(id, name, lang, url, fmt); dialog = null },
        )
        null -> {}
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Radio(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(stringResource(label))
    }
}

@Composable
private fun AddTranslationDialog(
    showUrl: Boolean,
    initialName: String,
    initialFormat: BibleFormat,
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, lang: String, url: String, fmt: BibleFormat) -> Unit,
) {
    var id by remember { mutableStateOf(initialName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24)) }
    var name by remember { mutableStateOf(initialName) }
    var lang by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var fmt by remember { mutableStateOf(initialFormat) }
    val valid = id.isNotBlank() && name.isNotBlank() && (!showUrl || url.startsWith("https://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (showUrl) R.string.versions_custom else R.string.versions_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(id, { id = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                    label = { Text(stringResource(R.string.dialog_id)) }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.dialog_name)) }, singleLine = true)
                OutlinedTextField(lang, { lang = it }, label = { Text(stringResource(R.string.dialog_language)) }, singleLine = true)
                if (showUrl) OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.dialog_url)) }, singleLine = true)
                Text(stringResource(R.string.dialog_format), style = MaterialTheme.typography.labelMedium)
                BibleFormat.entries.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { fmt = f }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fmt == f, onClick = { fmt = f })
                        Text(f.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(id, name, lang, url, fmt) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
