package ru.na.step4.obidy.ui.life

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import ru.na.step4.obidy.data.life.LifeBoardRu
import ru.na.step4.obidy.ui.theme.Amber
import ru.na.step4.obidy.ui.theme.Forest

private enum class ContactKind { PHONE, EMAIL, TELEGRAM, LINK }

/** Найденный в тексте контакт или ссылка вместе с её местом в строке. */
private data class ContactSpan(
    val start: Int,
    val end: Int,
    val kind: ContactKind,
    val raw: String
)

private val LINK_REGEX = Regex("""(https?://|www\.)[^\s]+""")
private val MAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
private val TELEGRAM_REGEX = Regex("""@[A-Za-z0-9_]{3,}|t\.me/[A-Za-z0-9_]+""")
private val PHONE_REGEX = Regex("""\+?\d[\d\s()\-]{8,}\d""")

/** Ищет в тексте телефон, ссылку, почту и ник телеграма, убирая пересечения. */
private fun findContacts(text: String): List<ContactSpan> {
    val found = mutableListOf<ContactSpan>()
    fun collect(regex: Regex, kind: ContactKind) {
        regex.findAll(text).forEach { match ->
            found.add(
                ContactSpan(
                    start = match.range.first,
                    end = match.range.last + 1,
                    kind = kind,
                    raw = match.value
                )
            )
        }
    }
    collect(LINK_REGEX, ContactKind.LINK)
    collect(MAIL_REGEX, ContactKind.EMAIL)
    collect(TELEGRAM_REGEX, ContactKind.TELEGRAM)
    collect(PHONE_REGEX, ContactKind.PHONE)

    val ordered = found.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
    val result = mutableListOf<ContactSpan>()
    var lastEnd = -1
    ordered.forEach { span ->
        if (span.start >= lastEnd) {
            result.add(span)
            lastEnd = span.end
        }
    }
    return result
}

private fun actionLabel(kind: ContactKind): String = when (kind) {
    ContactKind.PHONE -> LifeBoardRu.call
    ContactKind.EMAIL, ContactKind.TELEGRAM -> LifeBoardRu.write
    ContactKind.LINK -> LifeBoardRu.openLink
}

/**
 * Телефон, ссылка, почта и ник телеграма в тексте выделены и кликабельны:
 * тап предлагает сразу набрать/открыть или скопировать значение.
 */
@Composable
fun RichTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val spans = remember(text) { findContacts(text) }
    val annotated = remember(text, spans) {
        buildAnnotatedString {
            append(text)
            spans.forEach { span ->
                addStyle(
                    SpanStyle(color = Amber, textDecoration = TextDecoration.Underline),
                    span.start,
                    span.end
                )
            }
        }
    }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var picked by remember(text) { mutableStateOf<ContactSpan?>(null) }

    Text(
        text = annotated,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(text) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    picked = spans.firstOrNull { offset in it.start until it.end }
                }
            },
        style = style,
        color = Forest,
        onTextLayout = { layout = it }
    )

    picked?.let { span ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(span.raw, style = MaterialTheme.typography.titleMedium, color = Forest) },
            confirmButton = {
                TextButton(onClick = {
                    openContact(context, span)
                    picked = null
                }) {
                    Text(actionLabel(span.kind), color = Forest)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    copyContact(context, span.raw)
                    picked = null
                }) {
                    Text(LifeBoardRu.copy, color = Forest)
                }
            }
        )
    }
}

private fun openContact(context: Context, span: ContactSpan) {
    val uri = when (span.kind) {
        ContactKind.PHONE -> Uri.parse("tel:" + span.raw.filter { it.isDigit() || it == '+' })
        ContactKind.EMAIL -> Uri.parse("mailto:" + span.raw)
        ContactKind.TELEGRAM -> Uri.parse(
            if (span.raw.startsWith("t.me/")) {
                "https://${span.raw}"
            } else {
                "https://t.me/" + span.raw.removePrefix("@")
            }
        )
        ContactKind.LINK -> Uri.parse(
            if (span.raw.startsWith("http")) span.raw else "https://${span.raw}"
        )
    }
    val intent = when (span.kind) {
        ContactKind.PHONE -> Intent(Intent.ACTION_DIAL, uri)
        else -> Intent(Intent.ACTION_VIEW, uri)
    }
    runCatching { context.startActivity(intent) }
}

private fun copyContact(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(LifeBoardRu.copy, text))
    Toast.makeText(context, LifeBoardRu.copied, Toast.LENGTH_SHORT).show()
}
