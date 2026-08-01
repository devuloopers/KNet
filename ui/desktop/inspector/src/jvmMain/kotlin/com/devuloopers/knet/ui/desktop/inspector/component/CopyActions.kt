package com.devuloopers.knet.ui.desktop.inspector.component

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.CopyActionButton

/**
 * Inspector copy action buttons container.
 */
@Composable
public fun CopyActions(
    textToCopy: String = "",
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CopyActionButton(
            textToCopy = textToCopy,
            onCopy = onCopy
        )
    }
}
