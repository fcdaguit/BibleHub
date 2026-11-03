package com.sermonprep.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.AnnotatedString
import com.sermonprep.app.domain.BibleRef
import com.sermonprep.app.domain.ReferenceParser

/** Read-only text in which every Bible reference is a tappable link. */
@Composable
fun LinkedText(
    text: String,
    onRef: (BibleRef) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val callback by rememberUpdatedState(onRef)
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            for (f in ReferenceParser.findAll(text)) {
                append(text.substring(last, f.range.first))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = f.text,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { callback(f.ref) },
                    )
                ) { append(f.text) }
                last = f.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(annotated, modifier = modifier, style = style)
}

/** Underlines references while the text is being edited. */
class ReferenceHighlighter(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val refs = ReferenceParser.findAll(text.text)
        val styled = buildAnnotatedString {
            append(text)
            for (f in refs) {
                addStyle(
                    SpanStyle(color = color, textDecoration = TextDecoration.Underline),
                    f.range.first, f.range.last + 1,
                )
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
