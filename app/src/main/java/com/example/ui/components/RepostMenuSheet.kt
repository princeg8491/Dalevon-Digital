package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.haptics.LocalOrbitHaptics

/**
 * Crisp flat-design popup menu when clicking Repost: choose instant Repost or Quote Repost.
 */
@Composable
fun RepostMenuDialog(
    isAlreadyReposted: Boolean,
    onRepostClick: () -> Unit,
    onQuoteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("repost_flat_popup")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performMedium()
                            onDismiss()
                            onRepostClick()
                        }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .testTag("repost_popup_repost_option")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Repeat,
                        contentDescription = null,
                        tint = if (isAlreadyReposted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAlreadyReposted) "Remove repost" else "Repost",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAlreadyReposted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAlreadyReposted) {
                                "Take this thread off your reposts"
                            } else {
                                "Instantly share this thread to your followers"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performTap()
                            onDismiss()
                            onQuoteClick()
                        }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .testTag("repost_popup_quote_option")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quote",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add your own thoughts with this post embedded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Edit Post dialog used across Main Feed, Replies, Frames, Profile, and Spaces.
 */
@Composable
fun EditPostDialog(
    initialText: String,
    title: String = "Edit post",
    maxChars: Int = 500,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var textValue by remember(initialText) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    var isPreviewMode by remember { mutableStateOf(false) }
    val activeFormatState = remember(textValue.text, textValue.selection) {
        detectRichTextActiveState(textValue)
    }
    val hasFormatting = remember(textValue.text) {
        hasOrbitRichTextFormatting(textValue.text)
    }
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val visualTransformation = remember(markerColor, primaryColor) {
        OrbitRichTextVisualTransformation(markerColor = markerColor, accentColor = primaryColor)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RichTextFormattingToolbar(
                    activeState = activeFormatState,
                    hasFormatting = hasFormatting,
                    isPreviewMode = isPreviewMode,
                    onBoldClick = { textValue = applyBoldFormat(textValue, maxChars = maxChars) },
                    onItalicClick = { textValue = applyItalicFormat(textValue, maxChars = maxChars) },
                    onBulletClick = { textValue = toggleBulletListFormat(textValue, maxChars = maxChars) },
                    onTogglePreview = { isPreviewMode = !isPreviewMode }
                )
                if (isPreviewMode) {
                    val previewText = remember(textValue.text, primaryColor) {
                        parseOrbitRichText(textValue.text, primaryColor)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { incoming ->
                            textValue = handleRichTextValueChange(textValue, incoming, maxChars = maxChars)
                        },
                        visualTransformation = visualTransformation,
                        placeholder = { Text("Update your post…") },
                        minLines = 3,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_post_input")
                    )
                }
                Text(
                    text = "${maxChars - textValue.text.length} characters left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performMedium()
                    onSave(textValue.text.trim())
                },
                enabled = textValue.text.isNotBlank() && textValue.text.trim() != initialText.trim(),
                modifier = Modifier.testTag("edit_post_save_btn")
            ) {
                Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptics.performTap()
                    onDismiss()
                },
                modifier = Modifier.testTag("edit_post_cancel_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}
