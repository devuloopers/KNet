package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetInputField

/**
 * Request URL input field bar composable.
 *
 * @param url Active request URL.
 * @param onUrlChanged Callback when URL changes.
 * @param modifier Layout modifier.
 */
@Composable
public fun UrlBar(
    url: String,
    onUrlChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KNetInputField(
            value = url,
            onValueChange = onUrlChanged,
            placeholder = "https://api.example.com/endpoint",
            modifier = Modifier.weight(1f)
        )
    }
}
