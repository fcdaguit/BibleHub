package com.sermonprep.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sermonprep.app.R
import com.sermonprep.app.domain.BibleRef

fun shareText(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

fun copyText(ctx: android.content.Context, clipboard: androidx.compose.ui.platform.ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
    Toast.makeText(ctx, ctx.getString(R.string.verse_copied), Toast.LENGTH_SHORT).show()
}

/**
 * The floating verse window. Place once per screen; it appears whenever [LookupViewModel.show]
 * is called (tapping a reference, a verse, a search result, ...).
 */
@Composable
fun VerseLookupHost(
    vm: LookupViewModel,
    onOpenChapter: (BibleRef) -> Unit,
    onOpenSermon: ((Long) -> Unit)? = null,
) {
    val state = vm.popup ?: return
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = vm::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.ref.verseStart != null) {
                        IconButton(onClick = { vm.step(-1) }) {
                            Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_previous))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(state.ref.label(), style = MaterialTheme.typography.titleLarge)
                        state.versionName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (state.ref.verseStart != null) {
                        IconButton(onClick = { vm.step(1) }) {
                            Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_next))
                        }
                    }
                    IconButton(onClick = vm::dismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                }
                Spacer(Modifier.padding(4.dp))

                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    when (state.problem) {
                        PopupProblem.NO_TRANSLATION -> Text(stringResource(R.string.verse_no_translation))
                        PopupProblem.NOT_IN_TRANSLATION -> Text(stringResource(R.string.verse_not_found))
                        PopupProblem.NONE -> state.verses.forEach { v ->
                            Text(
                                "${v.verse}  ${v.text}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }

                if (state.problem == PopupProblem.NONE) {
                    Spacer(Modifier.padding(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { copyText(ctx, clipboard, state.plainText()) }) {
                            Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy))
                        }
                        IconButton(onClick = { shareText(ctx, state.plainText()) }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                        }
                        IconButton(onClick = vm::toggleBookmark) {
                            Icon(
                                if (state.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                stringResource(R.string.verse_bookmark),
                            )
                        }
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.verse_favorite),
                            )
                        }
                        IconButton(onClick = vm::openPicker) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.verse_add_to_outline))
                        }
                        IconButton(onClick = { vm.dismiss(); onOpenChapter(state.ref) }) {
                            Icon(Icons.Filled.MenuBook, stringResource(R.string.verse_open_chapter))
                        }
                    }
                }
            }
        }
    }

    if (vm.showPicker) SermonPicker(vm, onOpenSermon)
}

@Composable
private fun SermonPicker(vm: LookupViewModel, onOpenSermon: ((Long) -> Unit)?) {
    val ctx = LocalContext.current
    val sermons by vm.sermonList.collectAsState()
    val done: (Long) -> Unit = {
        Toast.makeText(ctx, ctx.getString(R.string.added_to_outline), Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = vm::closePicker,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.picker_new)) },
                        leadingContent = { Icon(Icons.Filled.Add, null) },
                        modifier = Modifier.clickable { vm.appendToNew(done) },
                    )
                    HorizontalDivider()
                }
                items(sermons, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.title.ifBlank { stringResource(R.string.sermons_untitled) }) },
                        modifier = Modifier.clickable { vm.appendTo(s.id, done) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closePicker) { Text(stringResource(R.string.action_cancel)) } },
    )
}
