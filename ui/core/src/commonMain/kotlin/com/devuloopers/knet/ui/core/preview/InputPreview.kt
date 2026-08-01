package com.devuloopers.knet.ui.core.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.CopyActionButton
import com.devuloopers.knet.ui.core.input.KNetInputField
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetTheme

/**
 * Preview composable showcasing KNetInputField and CopyActionButton controls.
 */
@Composable
public fun InputPreview() {
    var text by remember { mutableStateOf("https://api.knet.dev/v1/traffic") }

    KNetTheme {
        Column(
            modifier = Modifier
                .background(KNetColors.SurfaceDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KNetInputField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Enter URL"
            )
            CopyActionButton(textToCopy = text, label = "Copy URL")
        }
    }
}
