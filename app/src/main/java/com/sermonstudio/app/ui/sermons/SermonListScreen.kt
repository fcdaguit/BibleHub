package com.sermonstudio.app.ui.sermons

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.db.FolderEntity
import com.sermonstudio.app.data.db.SermonEntity
import com.sermonstudio.app.data.io.ExportFormat
import com.sermonstudio.app.data.io.ExportManager
import com.sermonstudio.app.data.io.ImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SermonListViewModel @Inject constructor(
    private val db: AppDatabase,
    private val exporter: ExportManager,
    private val importer: ImportManager,
) : ViewModel() {

    val query = MutableStateFlow("")
    val folderFilter = MutableStateFlow<Long?>(null)
    val showArchived = MutableStateFlow(false)

    val folders: StateFlow<List<FolderEntity>> =
        db.folders().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sermons: StateFlow<List<SermonEntity>> = combine(query, showArchived) { q, a -> q.trim() to a }
        .flatMapLatest { (q, archived) ->
            if (q.isEmpty()) db.sermons().observe(archived)
            else db.sermons().search(ftsPrefix(q)).map { list -> list.filter { it.archived == archived } }
        }
        .combine(folderFilter) { list, f -> if (f == null) list else list.filter { it.folderId == f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "faith stor" -> "faith*" "stor*" : prefix search on every word, safe against FTS syntax characters. */
    private fun ftsPrefix(q: String) =
        Regex("[\\p{L}\\p{N}]+").findAll(q).joinToString(" ") { "\"${it.value}*\"" }.ifEmpty { "\"\"" }

    suspend fun create(): Long {
        val now = System.currentTimeMillis()
        return db.sermons().insert(
            SermonEntity(title = "", body = "# \n", folderId = folderFilter.value, createdAt = now, updatedAt = now)
        )
    }

    fun duplicate(s: SermonEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.sermons().insert(s.copy(id = 0, title = "${s.title} (copy)", createdAt = now, updatedAt = now, archived = false))
    }

    fun setArchived(s: SermonEntity, v: Boolean) = viewModelScope.launch { db.sermons().update(s.copy(archived = v, updatedAt = System.currentTimeMillis())) }
    fun move(s: SermonEntity, folderId: Long?) = viewModelScope.launch { db.sermons().update(s.copy(folderId = folderId)) }
    fun delete(s: SermonEntity) = viewModelScope.launch { db.sermons().delete(s.id) }
    fun createFolder(name: String) = viewModelScope.launch { if (name.isNotBlank()) db.folders().insert(FolderEntity(name = name.trim())) }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        db.sermons().clearFolder(id); db.folders().delete(id)
        if (folderFilter.value == id) folderFilter.value = null
    }

    suspend fun exportIntent(s: SermonEntity, f: ExportFormat): Intent = exporter.shareIntent(exporter.export(s, f), f)

    /** Imports a TXT/DOCX/PDF as a new sermon and returns its id. */
    suspend fun import(uri: Uri): Long {
        val doc = importer.read(uri)
        val now = System.currentTimeMillis()
        return db.sermons().insert(
            SermonEntity(title = doc.title, body = doc.body, folderId = folderFilter.value, createdAt = now, updatedAt = now)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SermonListScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit, vm: SermonListViewModel = hiltViewModel()) {
    val sermons by vm.sermons.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val folder by vm.folderFilter.collectAsStateWithLifecycle()
    val archived by vm.showArchived.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var newFolderDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.import(uri) }
                .onSuccess { onOpen(it) }
                .onFailure { snackbar.showSnackbar(it.message ?: "Import failed") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sermons)) },
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "*/*")) }) {
                        Icon(Icons.Default.UploadFile, stringResource(R.string.import_file))
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { scope.launch { onOpen(vm.create()) } }) { Icon(Icons.Default.Add, stringResource(R.string.new_sermon)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                label = { Text(stringResource(R.string.search_sermons)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = folder == null && !archived, onClick = { vm.folderFilter.value = null; vm.showArchived.value = false }, label = { Text(stringResource(R.string.all)) })
                folders.forEach { f ->
                    FilterChip(selected = folder == f.id, onClick = { vm.folderFilter.value = if (folder == f.id) null else f.id }, label = { Text(f.name) })
                }
                FilterChip(selected = archived, onClick = { vm.showArchived.value = !archived }, label = { Text(stringResource(R.string.archived)) })
                FilterChip(selected = false, onClick = { newFolderDialog = true }, label = { Text("+ " + stringResource(R.string.new_folder)) })
            }
            folder?.let { id ->
                TextButton(onClick = { vm.deleteFolder(id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.delete) + " " + (folders.firstOrNull { it.id == id }?.name ?: ""))
                }
            }
            if (sermons.isEmpty()) {
                Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sermons, key = { it.id }) { s ->
                    SermonCard(
                        s = s, folders = folders,
                        onClick = { onOpen(s.id) },
                        onDuplicate = { vm.duplicate(s) },
                        onArchive = { vm.setArchived(s, !s.archived) },
                        onMove = { vm.move(s, it) },
                        onDelete = { vm.delete(s) },
                        onExport = { f -> scope.launch { ctx.startActivity(vm.exportIntent(s, f)) } },
                    )
                }
            }
        }
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.createFolder(name); newFolderDialog = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SermonCard(
    s: SermonEntity, folders: List<FolderEntity>,
    onClick: () -> Unit, onDuplicate: () -> Unit, onArchive: () -> Unit,
    onMove: (Long?) -> Unit, onDelete: () -> Unit, onExport: (ExportFormat) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<String?>(null) }   // "export" | "move"
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub2 = listOf(s.mainText, s.category).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub2.isNotBlank()) Text(sub2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            Column {
                IconButton(onClick = { menu = true; sub = null }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    when (sub) {
                        "export" -> ExportFormat.values().forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { menu = false; onExport(f) })
                        }
                        "move" -> {
                            DropdownMenuItem(text = { Text("—") }, onClick = { menu = false; onMove(null) })
                            folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { menu = false; onMove(f.id) }) }
                        }
                        else -> {
                            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; onDuplicate() })
                            DropdownMenuItem(text = { Text(stringResource(if (s.archived) R.string.unarchive else R.string.archive)) }, onClick = { menu = false; onArchive() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { sub = "move" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_as)) }, onClick = { sub = "export" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}
