package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.haptics.LocalOrbitHaptics

private val URL_REGEX = Regex("""https?://[^\s<>()]+""", RegexOption.IGNORE_CASE)
private val BULLET_PREFIX_REGEX = Regex("""^(\s*)(• |- |\* )""")

data class RichTextActiveState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isBullet: Boolean = false
)

/**
 * Returns true if the text contains any Orbit rich text markers (bold, italics, or bullet points).
 */
fun hasOrbitRichTextFormatting(text: String): Boolean {
    if (text.isBlank()) return false
    if (text.contains("**")) return true
    if (text.lines().any { BULLET_PREFIX_REGEX.containsMatchIn(it) }) return true
    // Detect single-asterisk italics outside URLs
    val strippedUrls = URL_REGEX.replace(text, "")
    val firstStar = strippedUrls.indexOf('*')
    if (firstStar >= 0) {
        val nextStar = strippedUrls.indexOf('*', startIndex = firstStar + 1)
        if (nextStar > firstStar + 1) return true
    }
    return false
}

val OrbitMutedMentionColor = Color(0xFF8E8E93)

/**
 * Parses Orbit rich text (bold **text**, italics *text*, bold+italic ***text***, bullet lines
 * starting with "• " / "- " / "* ", clickable URL highlighting, and grayed-out non-clickable
 * `@username` mentions) into an [AnnotatedString] for clean display across feeds, replies,
 * frames, quoted posts, and Spaces.
 */
fun parseOrbitRichText(
    raw: String,
    linkColor: Color,
    bulletColor: Color = linkColor,
    mentionColor: Color = if (linkColor == Color.Unspecified) Color.Unspecified else OrbitMutedMentionColor
): AnnotatedString {
    if (raw.isEmpty()) return AnnotatedString("")

    val lines = raw.split('\n')
    return buildAnnotatedString {
        lines.forEachIndexed { lineIdx, line ->
            val bulletMatch = BULLET_PREFIX_REGEX.find(line)
            if (bulletMatch != null) {
                val content = line.substring(bulletMatch.range.last + 1)
                append("  ")
                if (bulletColor != Color.Unspecified) {
                    withStyle(
                        SpanStyle(
                            color = bulletColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("•  ")
                    }
                } else {
                    append("• ")
                }
                appendInlineRichText(content, linkColor)
            } else {
                appendInlineRichText(line, linkColor)
            }
            if (lineIdx < lines.lastIndex) {
                append('\n')
            }
        }
        if (mentionColor != Color.Unspecified) {
            val rendered = toAnnotatedString().text
            val urlRanges = URL_REGEX.findAll(rendered).map { it.range }.toList()
            val mentionStyle = SpanStyle(
                color = mentionColor,
                fontWeight = FontWeight.Normal,
                textDecoration = TextDecoration.None
            )
            ORBIT_MENTION_REGEX.findAll(rendered).forEach { match ->
                val insideUrl = urlRanges.any { match.range.first >= it.first && match.range.last <= it.last }
                if (!insideUrl) {
                    addStyle(mentionStyle, match.range.first, match.range.last + 1)
                }
            }
        }
    }
}

private val ORBIT_MENTION_REGEX = Regex("(?<![a-zA-Z0-9_.])@[a-zA-Z0-9_.]{2,30}")

/**
 * Converts Orbit rich text to clean plain text (stripping ** and * markers, normalizing bullets
 * to "• ") for external sharing, notifications, and previews.
 */
fun stripOrbitRichText(raw: String): String {
    if (raw.isBlank()) return raw
    return parseOrbitRichText(raw, linkColor = Color.Unspecified, bulletColor = Color.Unspecified).text
}

private fun AnnotatedString.Builder.appendInlineRichText(
    line: String,
    linkColor: Color
) {
    if (line.isEmpty()) return
    val urlMatches = URL_REGEX.findAll(line).toList()
    if (urlMatches.isEmpty()) {
        appendStyledSegment(line)
        return
    }

    var cursor = 0
    urlMatches.forEach { match ->
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (start > cursor) {
            appendStyledSegment(line.substring(cursor, start))
        }
        val urlText = line.substring(start, endExclusive)
        if (linkColor != Color.Unspecified) {
            withStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(urlText)
            }
        } else {
            append(urlText)
        }
        cursor = endExclusive
    }
    if (cursor < line.length) {
        appendStyledSegment(line.substring(cursor))
    }
}

private fun AnnotatedString.Builder.appendStyledSegment(segment: String) {
    var i = 0
    val n = segment.length
    while (i < n) {
        // Bold + Italic: ***text***
        if (i + 2 < n && segment[i] == '*' && segment[i + 1] == '*' && segment[i + 2] == '*') {
            val close = segment.indexOf("***", startIndex = i + 3)
            if (close > i + 3) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(segment.substring(i + 3, close))
                }
                i = close + 3
                continue
            }
        }

        // Bold: **text**
        if (i + 1 < n && segment[i] == '*' && segment[i + 1] == '*') {
            val close = segment.indexOf("**", startIndex = i + 2)
            if (close > i + 2) {
                val inner = segment.substring(i + 2, close)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendItalicOnly(inner)
                }
                i = close + 2
                continue
            }
        }

        // Italic: *text* (not followed by another *)
        if (segment[i] == '*' && (i + 1 >= n || segment[i + 1] != '*')) {
            val close = findSingleAsteriskClose(segment, i + 1)
            if (close > i + 1) {
                val inner = segment.substring(i + 1, close)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendBoldOnly(inner)
                }
                i = close + 1
                continue
            }
        }

        append(segment[i])
        i++
    }
}

private fun AnnotatedString.Builder.appendItalicOnly(segment: String) {
    var i = 0
    val n = segment.length
    while (i < n) {
        if (segment[i] == '*' && (i + 1 >= n || segment[i + 1] != '*')) {
            val close = findSingleAsteriskClose(segment, i + 1)
            if (close > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(segment.substring(i + 1, close))
                }
                i = close + 1
                continue
            }
        }
        append(segment[i])
        i++
    }
}

private fun AnnotatedString.Builder.appendBoldOnly(segment: String) {
    var i = 0
    val n = segment.length
    while (i < n) {
        if (i + 1 < n && segment[i] == '*' && segment[i + 1] == '*') {
            val close = segment.indexOf("**", startIndex = i + 2)
            if (close > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(segment.substring(i + 2, close))
                }
                i = close + 2
                continue
            }
        }
        append(segment[i])
        i++
    }
}

private fun findSingleAsteriskClose(text: String, startIndex: Int): Int {
    var idx = startIndex
    while (idx < text.length) {
        if (text[idx] == '*') {
            val prevIsStar = idx > startIndex && text[idx - 1] == '*'
            val nextIsStar = idx + 1 < text.length && text[idx + 1] == '*'
            if (!prevIsStar && !nextIsStar) {
                return idx
            }
            // Skip over "**" pair inside italic span
            if (nextIsStar) {
                idx += 2
                continue
            }
        }
        idx++
    }
    return -1
}

/**
 * Live 1:1 [VisualTransformation] for the composer input.
 * Keeps character offsets identical ([OffsetMapping.Identity]) for rock-solid IME, cursor, and
 * selection behavior while styling **bold**, *italic*, and bullet dots live as you type and
 * subtly dimming the raw syntax delimiters.
 */
class OrbitRichTextVisualTransformation(
    private val markerColor: Color,
    private val accentColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            // Style bullet prefixes at line starts
            var lineStart = 0
            while (lineStart <= raw.length) {
                val lineEnd = raw.indexOf('\n', lineStart).let { if (it == -1) raw.length else it }
                val line = raw.substring(lineStart, lineEnd)
                val bulletMatch = BULLET_PREFIX_REGEX.find(line)
                if (bulletMatch != null) {
                    val symStart = lineStart + bulletMatch.groups[1]?.value.orEmpty().length
                    val symEnd = lineStart + bulletMatch.range.last + 1
                    addStyle(
                        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                        symStart,
                        symEnd
                    )
                }
                if (lineEnd == raw.length) break
                lineStart = lineEnd + 1
            }

            // Style inline bold, italic, and bold+italic spans
            val markerStyle = SpanStyle(color = markerColor, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal)
            var i = 0
            val n = raw.length
            while (i < n) {
                if (raw[i] == '\n') {
                    i++
                    continue
                }
                // ***bold+italic***
                if (i + 2 < n && raw[i] == '*' && raw[i + 1] == '*' && raw[i + 2] == '*') {
                    val close = raw.indexOf("***", startIndex = i + 3)
                    val newlineBetween = if (close > 0) raw.indexOf('\n', i + 3).let { it in (i + 3) until close } else true
                    if (close >= i + 3 && !newlineBetween) {
                        addStyle(markerStyle, i, i + 3)
                        if (close > i + 3) {
                            addStyle(
                                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                                i + 3,
                                close
                            )
                        }
                        addStyle(markerStyle, close, close + 3)
                        i = close + 3
                        continue
                    }
                }
                // **bold**
                if (i + 1 < n && raw[i] == '*' && raw[i + 1] == '*') {
                    val close = raw.indexOf("**", startIndex = i + 2)
                    val newlineBetween = if (close > 0) raw.indexOf('\n', i + 2).let { it in (i + 2) until close } else true
                    if (close >= i + 2 && !newlineBetween) {
                        addStyle(markerStyle, i, i + 2)
                        if (close > i + 2) {
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), i + 2, close)
                        }
                        addStyle(markerStyle, close, close + 2)
                        i = close + 2
                        continue
                    }
                }
                // *italic*
                if (raw[i] == '*' && (i + 1 >= n || raw[i + 1] != '*')) {
                    val close = findSingleAsteriskClose(raw, i + 1)
                    val newlineBetween = if (close > 0) raw.indexOf('\n', i + 1).let { it in (i + 1) until close } else true
                    if (close >= i + 1 && !newlineBetween) {
                        addStyle(markerStyle, i, i + 1)
                        if (close > i + 1) {
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), i + 1, close)
                        }
                        addStyle(markerStyle, close, close + 1)
                        i = close + 1
                        continue
                    }
                }
                i++
            }

            // Render @username mentions in muted gray (non-clickable) as the user types
            val urlRanges = URL_REGEX.findAll(raw).map { it.range }.toList()
            val mentionStyle = SpanStyle(
                color = OrbitMutedMentionColor,
                fontWeight = FontWeight.Normal,
                textDecoration = TextDecoration.None
            )
            ORBIT_MENTION_REGEX.findAll(raw).forEach { match ->
                val insideUrl = urlRanges.any { match.range.first >= it.first && match.range.last <= it.last }
                if (!insideUrl) {
                    addStyle(mentionStyle, match.range.first, match.range.last + 1)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Inspects the current cursor or selection to determine which rich text formats are active.
 */
fun detectRichTextActiveState(value: TextFieldValue): RichTextActiveState {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)

    // Current line bullet check
    val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1 || start == 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', start).let { if (it == -1) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)
    val isBullet = BULLET_PREFIX_REGEX.containsMatchIn(currentLine)

    // Inline bold check: either selection itself is wrapped in **...** or cursor is inside **...** on this line
    val isBold = isWrappedByDelimiter(text, start, end, "**", lineStart, lineEnd)
    val isItalic = isWrappedBySingleItalic(text, start, end, lineStart, lineEnd)

    return RichTextActiveState(
        isBold = isBold,
        isItalic = isItalic,
        isBullet = isBullet
    )
}

private fun isWrappedByDelimiter(
    text: String,
    start: Int,
    end: Int,
    delimiter: String,
    lineStart: Int,
    lineEnd: Int
): Boolean {
    val dLen = delimiter.length
    if (start >= dLen && end + dLen <= text.length &&
        text.substring(start - dLen, start) == delimiter &&
        text.substring(end, end + dLen) == delimiter
    ) {
        return true
    }
    if (end - start >= dLen * 2 &&
        text.substring(start, start + dLen) == delimiter &&
        text.substring(end - dLen, end) == delimiter
    ) {
        return true
    }
    // Check if cursor sits between an open and close delimiter on the same line
    val before = text.substring(lineStart, start)
    val after = text.substring(end, lineEnd)
    val openCountBefore = Regex(Regex.escape(delimiter)).findAll(before).count()
    val closeExistsAfter = after.contains(delimiter)
    return openCountBefore % 2 == 1 && closeExistsAfter
}

private fun isWrappedBySingleItalic(
    text: String,
    start: Int,
    end: Int,
    lineStart: Int,
    lineEnd: Int
): Boolean {
    // Check immediate single * around selection (excluding ** without ***)
    val leftSingle = start >= 1 && text[start - 1] == '*' &&
        !(start >= 2 && text[start - 2] == '*' && !(start >= 3 && text[start - 3] == '*'))
    val rightSingle = end < text.length && text[end] == '*' &&
        !(end + 1 < text.length && text[end + 1] == '*' && !(end + 2 < text.length && text[end + 2] == '*'))
    if (leftSingle && rightSingle) return true

    // Strip ** pairs on the current line to count single * markers cleanly
    val before = text.substring(lineStart, start).replace("**", "")
    val after = text.substring(end, lineEnd).replace("**", "")
    val openCountBefore = before.count { it == '*' }
    return openCountBefore % 2 == 1 && after.contains('*')
}

/**
 * Toggles **bold** formatting around the current selection or inserts bold markers at the cursor.
 */
fun applyBoldFormat(value: TextFieldValue, maxChars: Int = 500): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)

    // Case 1: Cursor/selection is immediately inside **...** -> unwrap
    if (start >= 2 && end + 2 <= text.length &&
        text.substring(start - 2, start) == "**" &&
        text.substring(end, end + 2) == "**"
    ) {
        val updated = text.substring(0, start - 2) +
            text.substring(start, end) +
            text.substring(end + 2)
        return TextFieldValue(
            text = updated,
            selection = TextRange(start - 2, end - 2)
        )
    }

    // Case 2: Selection itself starts and ends with ** -> unwrap
    if (end - start >= 4 &&
        text.substring(start, start + 2) == "**" &&
        text.substring(end - 2, end) == "**"
    ) {
        val inner = text.substring(start + 2, end - 2)
        val updated = text.substring(0, start) + inner + text.substring(end)
        return TextFieldValue(
            text = updated,
            selection = TextRange(start, start + inner.length)
        )
    }

    // Case 3: Wrap selection (or insert empty **** with cursor inside)
    if (text.length + 4 > maxChars) return value
    val selected = text.substring(start, end)
    val updated = text.substring(0, start) + "**$selected**" + text.substring(end)
    val newSelection = if (selected.isEmpty()) {
        TextRange(start + 2)
    } else {
        TextRange(start + 2, end + 2)
    }
    return TextFieldValue(text = updated, selection = newSelection)
}

/**
 * Toggles *italic* formatting around the current selection or inserts italic markers at the cursor.
 */
fun applyItalicFormat(value: TextFieldValue, maxChars: Int = 500): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)

    val wrappedDirectly = start >= 1 && end + 1 <= text.length &&
        text[start - 1] == '*' && text[end] == '*' &&
        !(start >= 2 && text[start - 2] == '*' && !(start >= 3 && text[start - 3] == '*')) &&
        !(end + 1 < text.length && text[end + 1] == '*' && !(end + 2 < text.length && text[end + 2] == '*'))

    if (wrappedDirectly) {
        val updated = text.substring(0, start - 1) +
            text.substring(start, end) +
            text.substring(end + 1)
        return TextFieldValue(
            text = updated,
            selection = TextRange(start - 1, end - 1)
        )
    }

    if (end - start >= 2 &&
        text[start] == '*' && text[end - 1] == '*' &&
        !(end - start >= 4 && text.substring(start, start + 2) == "**" && text.substring(end - 2, end) == "**")
    ) {
        val inner = text.substring(start + 1, end - 1)
        val updated = text.substring(0, start) + inner + text.substring(end)
        return TextFieldValue(
            text = updated,
            selection = TextRange(start, start + inner.length)
        )
    }

    if (text.length + 2 > maxChars) return value
    val selected = text.substring(start, end)
    val updated = text.substring(0, start) + "*$selected*" + text.substring(end)
    val newSelection = if (selected.isEmpty()) {
        TextRange(start + 1)
    } else {
        TextRange(start + 1, end + 1)
    }
    return TextFieldValue(text = updated, selection = newSelection)
}

/**
 * Toggles bullet points ("• ") on the current line or all lines covered by the selection.
 */
fun toggleBulletListFormat(value: TextFieldValue, maxChars: Int = 500): TextFieldValue {
    val text = value.text
    val selStart = value.selection.min.coerceIn(0, text.length)
    val selEnd = value.selection.max.coerceIn(0, text.length)

    val blockStart = if (selStart == 0) {
        0
    } else {
        text.lastIndexOf('\n', selStart - 1).let { if (it == -1) 0 else it + 1 }
    }
    val blockEnd = text.indexOf('\n', selEnd).let { if (it == -1) text.length else it }

    val block = text.substring(blockStart, blockEnd)
    val lines = block.split('\n')
    val allBulleted = lines.all { BULLET_PREFIX_REGEX.containsMatchIn(it) }

    val transformedLines = if (allBulleted) {
        lines.map { line ->
            val match = BULLET_PREFIX_REGEX.find(line)
            if (match != null) {
                match.groups[1]?.value.orEmpty() + line.substring(match.range.last + 1)
            } else {
                line
            }
        }
    } else {
        lines.map { line ->
            if (BULLET_PREFIX_REGEX.containsMatchIn(line)) line else "• $line"
        }
    }

    val newBlock = transformedLines.joinToString("\n")
    val updated = text.substring(0, blockStart) + newBlock + text.substring(blockEnd)
    if (updated.length > maxChars) return value

    val delta = newBlock.length - block.length
    val newCursor = if (selStart == selEnd) {
        (selEnd + delta).coerceIn(blockStart, blockStart + newBlock.length)
    } else {
        blockStart + newBlock.length
    }
    val newSelection = if (selStart == selEnd) {
        TextRange(newCursor)
    } else {
        TextRange(blockStart, blockStart + newBlock.length)
    }
    return TextFieldValue(text = updated, selection = newSelection)
}

/**
 * Handles text updates with smart bullet continuation on Enter:
 * - Pressing Enter after a non-empty bullet line automatically inserts "• " on the next line.
 * - Pressing Enter on an empty bullet line ("• ") cleanly exits the bullet list.
 */
fun handleRichTextValueChange(
    previous: TextFieldValue,
    incoming: TextFieldValue,
    maxChars: Int = 500
): TextFieldValue {
    if (incoming.text.length > maxChars) return previous

    val prevText = previous.text
    val nextText = incoming.text
    val cursor = incoming.selection.start

    // Detect single newline insertion
    if (nextText.length == prevText.length + 1 &&
        incoming.selection.collapsed &&
        cursor > 0 &&
        nextText[cursor - 1] == '\n'
    ) {
        val prevLineStart = nextText.lastIndexOf('\n', cursor - 2).let { if (it == -1) 0 else it + 1 }
        val prevLine = nextText.substring(prevLineStart, cursor - 1)
        val bulletMatch = BULLET_PREFIX_REGEX.find(prevLine)
        if (bulletMatch != null) {
            val contentAfterBullet = prevLine.substring(bulletMatch.range.last + 1)
            if (contentAfterBullet.isBlank()) {
                // Exit bullet list cleanly: remove the empty bullet and the newline
                val cleaned = nextText.substring(0, prevLineStart) + nextText.substring(cursor)
                return TextFieldValue(text = cleaned, selection = TextRange(prevLineStart))
            } else if (nextText.length + 2 <= maxChars) {
                // Continue bullet list automatically
                val continued = nextText.substring(0, cursor) + "• " + nextText.substring(cursor)
                return TextFieldValue(text = continued, selection = TextRange(cursor + 2))
            }
        }
    }

    return incoming
}

/**
 * Material 3 Rich Text Formatting Toolbar for the Orbit post composer and editor.
 * Provides Bold, Italic, and Bullet List controls with active-state highlights, haptic feedback,
 * and an optional live feed Preview toggle when formatting is applied.
 */
@Composable
fun RichTextFormattingToolbar(
    activeState: RichTextActiveState,
    hasFormatting: Boolean,
    isPreviewMode: Boolean,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onBulletClick: () -> Unit,
    onTogglePreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compose_rich_text_toolbar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            RichFormatButton(
                icon = Icons.Outlined.FormatBold,
                contentDescription = stringResource(R.string.format_bold),
                selected = activeState.isBold && !isPreviewMode,
                enabled = !isPreviewMode,
                testTag = "compose_format_bold_button",
                onClick = {
                    haptics.performTap()
                    onBoldClick()
                }
            )
            RichFormatButton(
                icon = Icons.Outlined.FormatItalic,
                contentDescription = stringResource(R.string.format_italic),
                selected = activeState.isItalic && !isPreviewMode,
                enabled = !isPreviewMode,
                testTag = "compose_format_italic_button",
                onClick = {
                    haptics.performTap()
                    onItalicClick()
                }
            )
            RichFormatButton(
                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                contentDescription = stringResource(R.string.format_bullet_list),
                selected = activeState.isBullet && !isPreviewMode,
                enabled = !isPreviewMode,
                testTag = "compose_format_bullet_button",
                onClick = {
                    haptics.performTap()
                    onBulletClick()
                }
            )

            Spacer(Modifier.weight(1f))

            if (onTogglePreview != null && (hasFormatting || isPreviewMode)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPreviewMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptics.performTap()
                            onTogglePreview()
                        }
                        .testTag("compose_format_preview_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = if (isPreviewMode) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isPreviewMode) {
                                stringResource(R.string.format_edit)
                            } else {
                                stringResource(R.string.format_preview)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPreviewMode) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichFormatButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor = when {
        !enabled -> Color.Transparent
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics { this.selected = selected }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
