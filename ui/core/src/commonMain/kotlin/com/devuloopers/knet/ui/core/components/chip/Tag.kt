package com.devuloopers.knet.ui.core.components.chip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Passive compact metadata label without click semantics. */
@Composable
fun KNetTag(
    text: String,
    modifier: Modifier = Modifier
) {
    KNetChip(
        text = text,
        modifier = modifier
    )
}
