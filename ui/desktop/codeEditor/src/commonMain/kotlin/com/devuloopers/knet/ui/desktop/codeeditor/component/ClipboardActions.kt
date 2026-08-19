package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalClipboard
import com.devuloopers.knet.ui.core.foundation.clipboard.readPlainText
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText
import kotlinx.coroutines.launch

/** Returns an asynchronous clipboard copy action for the code editor. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun rememberClipboardCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return remember(clipboard, coroutineScope) {
        { value ->
            if (value.isNotEmpty()) {
                coroutineScope.launch { clipboard.setPlainText(value) }
            }
        }
    }
}

/** Returns an asynchronous clipboard paste action for the code editor. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun rememberClipboardPasteAction(): ((String) -> Unit) -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return remember(clipboard, coroutineScope) {
        { onPasted ->
            coroutineScope.launch {
                clipboard.readPlainText()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(onPasted)
            }
        }
    }
}
