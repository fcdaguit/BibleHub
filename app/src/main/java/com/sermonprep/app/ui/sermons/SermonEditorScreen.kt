package com.sermonprep.app.ui.sermons

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.R
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser
import com.sermonprep.app.io.DocumentIO
import com.sermonprep.app.ui.components.LinkedText
import com.sermonprep.app.ui.components.LookupViewModel
import com.sermonprep.app.ui.components.ReferenceHighlighter
import com.sermonprep.app.ui.components.VerseLookupHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: SermonRepository,
    private val io: DocumentIO,
) : ViewModel() {
    private val id: Long = handle["id"] ?: 0L

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var loaded by mutableStateOf(false)
        private set
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            repo.get(id)?.let { title = it.title; body = TextFieldValue(it.content) }
            loaded = true
        }
    }

    fun onTitle(t: String) { title = t; scheduleSave() }

    fun onBody(v: TextFieldValue) {
        val changed = v.text != body.text
        body = v
        if (changed) scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(500); repo.save(id, title, body.text) }
    }

    /** Called when leaving the screen or the app goes to the background. */
    fun flush() {
        if (!loaded) return
        saveJob?.cancel()
        viewModelScope.launch { repo.save(id, title, body.text) }
    }

    fun exportPdf(uri: android.net.Uri, done: (Throwable?) -> Unit) = viewModelScope.launch {
        done(runCatching { io.exportPdf(title, body.text, uri) }.exceptionOrNull())
    }

    fun exportDocx(uri: android.net.Uri, done: (Throwable?) -> Unit) = viewModelScope.launch {
        done(runCatching { io.exportDocx(title, body.text, uri) }.exceptionOrNull())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonEditorScreen(
    onBack: () -> Unit,
    onOpenChapter: (BibleRef) -> Unit,
    vm: EditorViewModel = hiltViewModel(),
    lookup: LookupViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val refs = remember(vm.body.text) { ReferenceParser.findAll(vm.body.text) }

    fun leave() { vm.flush(); onBack() }
    BackHandler { leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.flush() }

    val exported = stringResource(R.string.editor_exported)
    val failedFmt = stringResource(R.string.editor_export_failed)
    val result: (Throwable?) -> Unit = { err ->
        Toast.makeText(ctx, if (err == null) exported else failedFmt.format(err.message ?: err.toString()), Toast.LENGTH_LONG).show()
    }
    val fileBase = vm.title.ifBlank { "sermon" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "_")
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        if (it != null) vm.exportPdf(it, result)
    }
    val docxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { if (it != null) vm.exportDocx(it, result) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { reading = !reading }) {
                        Icon(
                            if (reading) Icons.Filled.Edit else Icons.Filled.Visibility,
                            stringResource(if (reading) R.string.editor_edit else R.string.editor_read),
                        )
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_export_pdf)) },
                            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                            onClick = { menu = false; pdfLauncher.launch("$fileBase.pdf") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_export_docx)) },
                            leadingIcon = { Icon(Icons.Filled.Description, null) },
                            onClick = { menu = false; docxLauncher.launch("$fileBase.docx") },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = vm::onTitle,
                placeholder = { Text(stringResource(R.string.editor_title_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            if (reading) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    LinkedText(vm.body.text, onRef = lookup::show, modifier = Modifier.fillMaxWidth())
                }
            } else {
                val linkColor = MaterialTheme.colorScheme.primary
                val transformation = remember(linkColor) { ReferenceHighlighter(linkColor) }
                OutlinedTextField(
                    value = vm.body,
                    onValueChange = { nv ->
                        val textUnchanged = nv.text == vm.body.text
                        vm.onBody(nv)
                        // A tap, arrow-key move or highlight (no typing) on a reference opens its verse.
                        if (textUnchanged) {
                            val sel = nv.selection
                            val hit = if (sel.collapsed) {
                                ReferenceParser.findAll(nv.text).firstOrNull { sel.start in it.range.first..(it.range.last + 1) }
                            } else {
                                val selected = nv.text.substring(sel.min, sel.max)
                                ReferenceParser.findAll(selected).firstOrNull()
                                    ?: ReferenceParser.findAll(nv.text).firstOrNull {
                                        it.range.first < sel.max && it.range.last + 1 > sel.min
                                    }
                            }
                            hit?.let { lookup.show(it.ref) }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.editor_body_hint)) },
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                if (refs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.editor_refs),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        items(refs.map { it.ref }.distinct()) { r ->
                            AssistChip(onClick = { lookup.show(r) }, label = { Text(r.label()) })
                        }
                    }
                }
            }
        }
    }

    VerseLookupHost(lookup, onOpenChapter = { vm.flush(); onOpenChapter(it) })
}
