package com.sermonstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.R
import com.sermonstudio.app.data.bible.BibleRef
import com.sermonstudio.app.data.bible.BibleRepository
import com.sermonstudio.app.data.bible.formatVerses
import com.sermonstudio.app.data.db.BibleVersionEntity
import com.sermonstudio.app.data.db.VerseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PopupViewModel @Inject constructor(private val bible: BibleRepository) : ViewModel() {
    data class State(
        val ref: BibleRef? = null,
        val versions: List<BibleVersionEntity> = emptyList(),
        val selected: String? = null,
        val verses: List<VerseEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun show(ref: BibleRef) {
        viewModelScope.launch {
            val versions = bible.versionsNow()
            val keep = _state.value.selected?.takeIf { s -> versions.any { it.id == s } }
            val sel = keep ?: bible.defaultVersionId()
            _state.value = State(ref, versions, sel, if (sel != null) bible.passage(sel, ref) else emptyList())
        }
    }

    fun select(id: String) {
        val ref = _state.value.ref ?: return
        viewModelScope.launch { _state.update { it.copy(selected = id, verses = bible.passage(id, ref)) } }
    }
}

/**
 * Floating verse window. It is a dialog over the current screen, so the sermon the user is typing
 * stays exactly where it was. Scrollable; can copy, insert into the sermon, or open the full chapter.
 */
@Composable
fun VersePopup(
    ref: BibleRef,
    onDismiss: () -> Unit,
    onOpenChapter: (book: Int, chapter: Int, verse: Int) -> Unit,
    onInsert: ((String) -> Unit)? = null,
    vm: PopupViewModel = hiltViewModel(),
) {
    LaunchedEffect(ref) { vm.show(ref) }
    val st by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val formatted = st.selected?.let { formatVerses(ref, it, st.verses) } ?: ref.display()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 540.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ref.display(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                }
                if (st.versions.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.versions.forEach {
                            FilterChip(selected = it.id == st.selected, onClick = { vm.select(it.id) }, label = { Text(it.id) })
                        }
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    if (st.verses.isEmpty()) {
                        Text(stringResource(R.string.not_in_version), color = MaterialTheme.colorScheme.error)
                    } else st.verses.forEach { v ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append("${v.verse} ") }
                                append(v.text)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(formatted)) }, enabled = st.verses.isNotEmpty()) {
                        Text(stringResource(R.string.copy))
                    }
                    if (onInsert != null) TextButton(onClick = { onInsert(formatted); onDismiss() }, enabled = st.verses.isNotEmpty()) {
                        Text(stringResource(R.string.insert))
                    }
                    TextButton(onClick = { onOpenChapter(ref.book, ref.chapter, ref.verseStart ?: 0); onDismiss() }) {
                        Text(stringResource(R.string.open_chapter))
                    }
                }
            }
        }
    }
}
