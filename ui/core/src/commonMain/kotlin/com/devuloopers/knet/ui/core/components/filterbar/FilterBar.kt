package com.devuloopers.knet.ui.core.components.filterbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetFilterBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val themeColors = KNetTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(themeColors.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
