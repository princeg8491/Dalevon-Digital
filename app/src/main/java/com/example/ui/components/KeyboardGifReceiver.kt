package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.data.cache.OrbitMediaFiles

/**
 * Creates a Jetpack Compose [ReceiveContentListener] that enables the Android keyboard's
 * built-in GIF, sticker, and image insertion (Gboard, Samsung Keyboard, SwiftKey, etc.) on any
 * `BasicTextField(state = ...)` via `Modifier.contentReceiver(...)`.
 *
 * Immediately copies keyboard-supplied temporary `content://` URIs into app cache while the IME's
 * transient read permission is valid so previews animate reliably and publishing never hits a
 * `SecurityException`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberKeyboardMediaReceiver(
    onMediaReceived: (Uri) -> Unit
): ReceiveContentListener {
    val context = LocalContext.current
    val currentHandler by rememberUpdatedState(onMediaReceived)

    return remember(context) {
        ReceiveContentListener { transferableContent ->
            if (!transferableContent.hasMediaType(MediaType.Image)) {
                return@ReceiveContentListener transferableContent
            }
            transferableContent.consume { item ->
                val uri = item.uri
                if (uri != null) {
                    val persistedUri = OrbitMediaFiles.persistKeyboardMediaUriSync(context, uri)
                    if (persistedUri != null) {
                        currentHandler(persistedUri)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
}
