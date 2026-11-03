package com.sermonprep.app.ui.sermons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.data.db.SermonEntity
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.io.DocumentIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SermonListViewModel @Inject constructor(
    private val repo: SermonRepository,
    private val io: DocumentIO,
    bible: BibleRepository,
) : ViewModel() {
    val sermons = repo.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasTranslation = bible.versions.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(repo.create()) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun duplicate(id: Long) = viewModelScope.launch { repo.duplicate(id) }

    fun import(uri: android.net.Uri, onDone: (Long) -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { io.import(uri) }
            .onSuccess { onDone(repo.create(it.title, it.content)) }
            .onFailure { onError(it.message ?: it.toString()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonListScreen(onOpen: (Long) -> Unit, vm: SermonListViewModel = hiltViewModel()) {
    val sermons by vm.sermons.collectAsState()
    val hasTranslation by vm.hasTranslation.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<SermonEntity?>(null) }
    val failedFmt = stringResource(R.string.sermons_import_failed)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, onDone = onOpen, onError = { msg ->
            scope.launch { snackbar.showSnackbar(failedFmt.format(msg)) }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_sermons)) },
                actions = {
                    IconButton(onClick = {
                        picker.launch(arrayOf(
                            "text/plain",
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ))
                    }) { Icon(Icons.Filled.UploadFile, stringResource(R.string.sermons_import)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.create(onOpen) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.sermons_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasTranslation) item {
                Card(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sermons_no_translation), Modifier.padding(16.dp))
                }
            }
            if (sermons.isEmpty()) item {
                Text(stringResource(R.string.sermons_empty), Modifier.padding(8.dp))
            }
            items(sermons, key = { it.id }) { s ->
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().clickable { onOpen(s.id) }) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title.ifBlank { stringResource(R.string.sermons_untitled) },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (s.content.isNotBlank()) Text(
                                s.content.replace('\n', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                onClick = { menu = false; vm.duplicate(s.id) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menu = false; toDelete = s },
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.sermons_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(s.id); toDelete = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
