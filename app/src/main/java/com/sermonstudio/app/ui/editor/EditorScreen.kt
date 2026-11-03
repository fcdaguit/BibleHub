package com.sermonstudio.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.ReferenceParser
import com.sermonstudio.app.data.io.ExportFormat
import com.sermonstudio.app.ui.common.VersePopup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenChapter: (book: Int, chapter: Int, verse: Int) -> Unit,
    vm: EditorViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val folders by vm.folders.collectAsStateWithLifecycle()
    var popupRef by remember { mutableStateOf<BibleRef?>(null) }
    var menu by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf("main") }
    var insertDialog by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }

    // Save when leaving, including via the system back gesture.
    DisposableEffect(Unit) { onDispose { vm.saveNow() } }
    BackHandler { vm.saveNow(); onBack() }

    val markup = remember(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.outline) {
        MarkupTransformation(linkColor = Color(0xFF1E88E5), dim = Color(0x99808080))
    }
    val textColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.saveNow(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                title = {
                    BasicTextField(
                        value = vm.title, onValueChange = vm::onTitleChange, singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, color = textColor),
                        cursorBrush = SolidColor(textColor),
                        decorationBox = { inner ->
                            if (vm.title.isEmpty()) Text(stringResource(R.string.untitled), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
                            inner()
                        },
                    )
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo)) }
                    IconButton(onClick = { menu = true; menuPage = "main" }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        when (menuPage) {
                            "export" -> ExportFormat.values().forEach { f ->
                                DropdownMenuItem(text = { Text(f.name) }, onClick = { menu = false; scope.launch { ctx.startActivity(vm.exportIntent(f)) } })
                            }
                            "move" -> {
                                DropdownMenuItem(text = { Text("—") }, onClick = { menu = false; vm.moveTo(null) })
                                folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { menu = false; vm.moveTo(f.id) }) }
                            }
                            else -> {
                                DropdownMenuItem(text = { Text(stringResource(R.string.export_as)) }, onClick = { menuPage = "export" })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { menuPage = "move" })
                                DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; vm.duplicate() })
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (vm.archived) R.string.unarchive else R.string.archive)) },
                                    onClick = { menu = false; vm.setArchived(!vm.archived) },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    TextButton(onClick = { vm.prefixLines("# ") }) { Text("H1") }
                    TextButton(onClick = { vm.prefixLines("## ") }) { Text("H2") }
                    IconButton(onClick = { vm.prefixLines("- ") }) { Icon(Icons.Default.FormatListBulleted, "Bullets") }
                    IconButton(onClick = { vm.prefixLines("", numbered = true) }) { Icon(Icons.Default.FormatListNumbered, "Numbered") }
                    IconButton(onClick = { vm.wrap("**") }) { Icon(Icons.Default.FormatBold, "Bold") }
                    IconButton(onClick = { vm.wrap("_") }) { Icon(Icons.Default.FormatItalic, "Italic") }
                    IconButton(onClick = { vm.wrap("==") }) { Icon(Icons.Default.FormatColorFill, "Highlight") }
                    IconButton(onClick = { colorMenu = true }) { Icon(Icons.Default.FormatColorText, "Text colour") }
                    DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                        listOf("red", "blue", "green", "orange", "purple").forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { colorMenu = false; vm.wrap("{$c}", "{/}") })
                        }
                    }
                    IconButton(onClick = { vm.prefixLines("> ") }) { Icon(Icons.Default.StickyNote2, "Note") }
                    IconButton(onClick = { insertDialog = true }) { Icon(Icons.Default.MenuBook, stringResource(R.string.insert_scripture)) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Quick access: every reference detected in the sermon, one tap to open its popup.
            val refs = vm.references()
            if (refs.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    refs.forEach { r -> AssistChip(onClick = { popupRef = r }, label = { Text(r.display()) }) }
                }
                HorizontalDivider()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                BasicTextField(
                    value = vm.body,
                    onValueChange = { new ->
                        val textChanged = new.text != vm.body.text
                        vm.onBodyChange(new)
                        // A tap/selection inside a reference (no text change) opens the verse popup.
                        if (!textChanged) {
                            val sel = new.selection
                            ReferenceParser.findAll(new.text).firstOrNull { m ->
                                if (sel.collapsed) sel.start in m.range.first..(m.range.last + 1)
                                else sel.min <= m.range.last && sel.max > m.range.first
                            }?.let { popupRef = it.ref }
                        }
                    },
                    textStyle = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, color = textColor),
                    cursorBrush = SolidColor(textColor),
                    visualTransformation = markup,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp),
                )
            }
        }
    }

    popupRef?.let { ref ->
        VersePopup(
            ref = ref,
            onDismiss = { popupRef = null },
            onOpenChapter = { b, c, v -> vm.saveNow(); onOpenChapter(b, c, v) },
            onInsert = { vm.insertAtCursor("\n> $it\n") },
        )
    }

    if (insertDialog) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { insertDialog = false },
            title = { Text(stringResource(R.string.insert_scripture)) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false }, singleLine = true, isError = error,
                    placeholder = { Text(stringResource(R.string.reference_hint)) },
                    supportingText = if (error) ({ Text(stringResource(R.string.not_in_version)) }) else null,
                )
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { if (vm.insertScripture(text)) insertDialog = false else error = true } }) {
                    Text(stringResource(R.string.insert))
                }
            },
            dismissButton = { TextButton(onClick = { insertDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
