package com.devuloopers.knet.ui.core.components.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun StatusItem(
    text: String,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Text(
        text = text,
        style = typography.caption.copy(color = themeColors.textSecondary),
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
public fun RowScope.FlexibleSpacer(
    modifier: Modifier = Modifier
) {
    Spacer(modifier = modifier.weight(1f))
}

@Composable
public fun KNetStatusBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.statusBarHeight)
            .background(themeColors.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
