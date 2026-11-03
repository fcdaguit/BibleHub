package com.sermonstudio.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.io.BackupManager
import com.sermonstudio.app.data.prefs.AppSettings
import com.sermonstudio.app.data.prefs.LocaleHelper
import com.sermonstudio.app.data.prefs.SettingsRepository
import com.sermonstudio.app.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: SettingsRepository,
    private val bible: BibleRepository,
    private val backups: BackupManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val versions: StateFlow<List<BibleVersionEntity>> =
        bible.versions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setTheme(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }
    fun setDefaultVersion(id: String) = viewModelScope.launch { repo.setDefaultVersion(id) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch { repo.setAutoBackup(v) }
    fun setEngine(v: String) = viewModelScope.launch { repo.setAiEngine(v) }

    fun backupNow() = viewModelScope.launch {
        message = runCatching { backups.writeAutoBackup().name }.fold({ "Saved $it" }, { "Backup failed: ${it.message}" })
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        message = runCatching {
            val json = backups.createJson()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
        }.fold({ "Backup exported" }, { "Export failed: ${it.message}" })
    }

    fun restore(uri: Uri, replace: Boolean) = viewModelScope.launch {
        message = runCatching {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
            backups.restore(text, replace)
        }.fold({ "Restored $it items" }, { "Restore failed: ${it.message}" })
    }

    /** Copies a model file (can be large) into app storage and selects it. */
    fun importModel(uri: Uri) = viewModelScope.launch {
        busy = true
        message = "Copying model…"
        message = runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(ctx.filesDir, "models").apply { mkdirs() }
                val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "model.bin"
                val out = File(dir, name)
                ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                repo.setLlmModelPath(out.absolutePath)
                out.name
            }
        }.fold({ "Model ready: $it" }, { "Import failed: ${it.message}" })
        busy = false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onVersions: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    val modelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importModel) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_settings)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- Appearance ----
            Section(R.string.settings_appearance)
            Text(stringResource(R.string.theme))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, label) ->
                    FilterChip(selected = s.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(stringResource(label)) })
                }
            }
            SwitchRow(stringResource(R.string.dynamic_color), s.dynamicColor, vm::setDynamic)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.language), Modifier.weight(1f))
                var open by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { open = true }) { Text(LocaleHelper.supported.first { it.first == LocaleHelper.get(ctx) }.second) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        LocaleHelper.supported.forEach { (tag, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                open = false
                                LocaleHelper.save(ctx, tag)
                                ctx.findActivity()?.recreate()
                            })
                        }
                    }
                }
            }
            HorizontalDivider()

            // ---- Bible ----
            Section(R.string.settings_bible)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.default_version), Modifier.weight(1f))
                var open by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { open = true }, enabled = versions.isNotEmpty()) { Text(s.defaultVersionId ?: versions.firstOrNull()?.id ?: "-") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        versions.forEach { v -> DropdownMenuItem(text = { Text("${v.id} — ${v.name}") }, onClick = { open = false; vm.setDefaultVersion(v.id) }) }
                    }
                }
            }
            Button(onClick = onVersions) { Text(stringResource(R.string.manage_versions)) }
            HorizontalDivider()

            // ---- AI ----
            Section(R.string.settings_ai)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.aiEngine == "template", onClick = { vm.setEngine("template") }, label = { Text(stringResource(R.string.engine_template)) })
                FilterChip(selected = s.aiEngine == "llm", onClick = { vm.setEngine("llm") }, label = { Text(stringResource(R.string.engine_llm)) })
            }
            OutlinedButton(onClick = { modelLauncher.launch(arrayOf("*/*")) }, enabled = !vm.busy) { Text(stringResource(R.string.import_model)) }
            s.llmModelPath?.let { Text(File(it).name, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()

            // ---- Backup ----
            Section(R.string.settings_backup)
            SwitchRow(stringResource(R.string.auto_backup), s.autoBackup, vm::setAutoBackup)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::backupNow) { Text(stringResource(R.string.backup_now)) }
                OutlinedButton(onClick = { exportLauncher.launch("sermon-studio-backup.json") }) { Text(stringResource(R.string.export_backup)) }
            }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.restore_backup)) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_backup)) },
            text = { Text("Merge keeps what is on this device and adds/overwrites from the file. Replace deletes current sermons, notes and bookmarks first.") },
            confirmButton = { TextButton(onClick = { vm.restore(uri, replace = true); restoreUri = null }) { Text("Replace") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.restore(uri, replace = false); restoreUri = null }) { Text("Merge") }
                    TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun Section(title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
