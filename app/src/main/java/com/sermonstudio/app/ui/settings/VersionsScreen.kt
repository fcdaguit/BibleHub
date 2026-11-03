package com.sermonstudio.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.prefs.AppSettings
import com.sermonstudio.app.data.prefs.SettingsRepository
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
class VersionsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val versions: StateFlow<List<BibleVersionEntity>> = bible.versions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settingsState: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var progress by mutableStateOf<Float?>(null)

    fun setDefault(id: String) = viewModelScope.launch { settings.setDefaultVersion(id) }
    fun delete(id: String) = viewModelScope.launch { bible.delete(id) }

    fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "bible"

    private fun run(block: suspend () -> Int) = viewModelScope.launch {
        busy = true; progress = null
        status = runCatching { block() }.fold({ "Installed ($it verses)" }, { "Failed: ${it.message}" })
        busy = false; progress = null
    }

    private val onProgress: (String, Float?) -> Unit = { msg, p -> status = msg; progress = p }

    fun installFromUri(meta: BibleVersionEntity, uri: Uri) = run {
        withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "bible-import.tmp")
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                bible.installFromFile(meta, tmp, displayName(uri), onProgress)
            } finally { tmp.delete() }
        }
    }

    fun installFromUrl(meta: BibleVersionEntity, url: String) = run { bible.installFromUrl(meta, url, onProgress) }
}

private data class PendingInstall(val uri: Uri? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(onBack: () -> Unit, vm: VersionsViewModel = hiltViewModel()) {
    val versions by vm.versions.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingInstall?>(null) }
    var toDelete by remember { mutableStateOf<BibleVersionEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pending = PendingInstall(uri) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.manage_versions)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy) { Text(stringResource(R.string.import_bible_file)) }
                OutlinedButton(onClick = { pending = PendingInstall(null) }, enabled = !vm.busy) { Text(stringResource(R.string.download_from_url)) }
            }
            if (vm.busy) {
                vm.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            vm.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.installed), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(versions, key = { it.id }) { v ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${v.id} — ${v.name}", style = MaterialTheme.typography.titleSmall)
                                Text(listOf(v.language, v.license).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            val isDefault = (settings.defaultVersionId ?: versions.firstOrNull()?.id) == v.id
                            IconButton(onClick = { vm.setDefault(v.id) }) { Icon(if (isDefault) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.set_default)) }
                            IconButton(onClick = { toDelete = v }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }

    pending?.let { p ->
        var id by remember { mutableStateOf("") }
        var name by remember { mutableStateOf(p.uri?.let { vm.displayName(it).substringBeforeLast('.') } ?: "") }
        var lang by remember { mutableStateOf("en") }
        var license by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(if (p.uri != null) R.string.import_bible_file else R.string.download_from_url)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(id, { id = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text(stringResource(R.string.version_id)) }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.version_name)) }, singleLine = true)
                    OutlinedTextField(lang, { lang = it }, label = { Text(stringResource(R.string.version_language)) }, singleLine = true)
                    OutlinedTextField(license, { license = it }, label = { Text(stringResource(R.string.version_license)) }, singleLine = true)
                    if (p.uri == null) OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.url)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = id.isNotBlank() && name.isNotBlank() && (p.uri != null || url.startsWith("http")),
                    onClick = {
                        val meta = BibleVersionEntity(id, name.trim(), lang.trim(), license.trim())
                        if (p.uri != null) vm.installFromUri(meta, p.uri) else vm.installFromUrl(meta, url.trim())
                        pending = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    toDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete) + " " + v.id + "?") },
            confirmButton = { TextButton(onClick = { vm.delete(v.id); toDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
