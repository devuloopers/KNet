package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetBottomPane(
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    tabs: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(themeColors.surface)
    ) {
        HorizontalDivider()
        if (tabs != null || actions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tabs != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        tabs()
                    }
                }
                if (actions != null) {
                    actions()
                }
            }
            HorizontalDivider()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
