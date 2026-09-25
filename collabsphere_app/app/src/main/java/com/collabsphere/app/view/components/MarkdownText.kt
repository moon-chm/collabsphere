package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.collabsphere.app.dto.message.LinkPreview
import com.collabsphere.app.model.LinkPreviewRepo
import org.koin.compose.koinInject

enum class MdStyle { BOLD, ITALIC, STRIKE, CODE, CODE_BLOCK, LINK }

data class MdSpan(val text: String, val styles: Set<MdStyle> = emptySet(), val url: String? = null)

private val urlPattern = Regex("""https?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"]""")
private val inlinePattern = Regex(
    """```([\s\S]+?)```|`([^`\n]+)`|\*\*([^*\n]+)\*\*|~~([^~\n]+)~~|(?<![\w*])\*([^*\n]+)\*(?![\w*])|(?<![\w_])_([^_\n]+)_(?![\w_])|""" +
        urlPattern.pattern
)

fun extractFirstUrl(text: String): String? = urlPattern.find(text)?.value

fun parseMarkdown(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var cursor = 0
    for (match in inlinePattern.findAll(text)) {
        if (match.range.first > cursor) spans += MdSpan(text.substring(cursor, match.range.first))
        val groups = match.groups
        spans += when {
            groups[1] != null -> MdSpan(groups[1]!!.value.trim('\n'), setOf(MdStyle.CODE_BLOCK))
            groups[2] != null -> MdSpan(groups[2]!!.value, setOf(MdStyle.CODE))
            groups[3] != null -> MdSpan(groups[3]!!.value, setOf(MdStyle.BOLD))
            groups[4] != null -> MdSpan(groups[4]!!.value, setOf(MdStyle.STRIKE))
            groups[5] != null -> MdSpan(groups[5]!!.value, setOf(MdStyle.ITALIC))
            groups[6] != null -> MdSpan(groups[6]!!.value, setOf(MdStyle.ITALIC))
            else -> MdSpan(match.value, setOf(MdStyle.LINK), url = match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) spans += MdSpan(text.substring(cursor))
    return spans
}

fun markdownToPlainText(text: String): String = parseMarkdown(text).joinToString("") { it.text }

private fun buildMarkdown(spans: List<MdSpan>, linkColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (MdStyle.BOLD in span.styles) FontWeight.Bold else null,
                fontStyle = if (MdStyle.ITALIC in span.styles) FontStyle.Italic else null,
                textDecoration = if (MdStyle.STRIKE in span.styles) TextDecoration.LineThrough else null,
                fontFamily = if (MdStyle.CODE in span.styles || MdStyle.CODE_BLOCK in span.styles) FontFamily.Monospace else null,
                background = if (MdStyle.CODE in span.styles || MdStyle.CODE_BLOCK in span.styles) codeBackground else Color.Unspecified
            )
            val url = span.url
            if (url != null) {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) { append(span.text) }
            } else {
                withStyle(style) {
                    if (MdStyle.CODE_BLOCK in span.styles) append("\n${span.text}\n") else append(span.text)
                }
            }
        }
    }

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    style: TextStyle,
    linkColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val codeBackground = color.copy(alpha = 0.12f)
    val annotated = remember(text, linkColor, codeBackground) {
        buildMarkdown(parseMarkdown(text), linkColor, codeBackground)
    }
    Text(
        text = annotated,
        color = color,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun LinkPreviewCard(text: String, onDarkBubble: Boolean, modifier: Modifier = Modifier) {
    val url = remember(text) { extractFirstUrl(text) } ?: return
    val repo = koinInject<LinkPreviewRepo>()
    val preview by produceState<LinkPreview?>(initialValue = null, url) {
        value = repo.preview(url)
    }
    val current = preview ?: return
    val uriHandler = LocalUriHandler.current
    val background = if (onDarkBubble) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.05f)
    val titleColor = if (onDarkBubble) Color.White else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (onDarkBubble) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .padding(top = 6.dp)
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { runCatching { uriHandler.openUri(current.url) } }
            .padding(8.dp)
    ) {
        current.imageUrl?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(56.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Column(modifier = Modifier.padding(start = if (current.imageUrl != null) 8.dp else 0.dp).fillMaxWidth()) {
            current.siteName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = bodyColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            current.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            current.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = bodyColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
