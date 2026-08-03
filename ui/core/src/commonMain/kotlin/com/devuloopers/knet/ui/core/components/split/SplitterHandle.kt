package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun SplitterHandle(
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = true
) {
    val themeColors = KNetTheme.colors

    val handleModifier = if (isHorizontal) {
        modifier.fillMaxHeight().width(4.dp)
    } else {
        modifier.fillMaxWidth().height(4.dp)
    }

    Box(modifier = handleModifier.background(themeColors.border))
}
