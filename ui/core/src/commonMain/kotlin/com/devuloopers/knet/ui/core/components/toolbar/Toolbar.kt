package com.devuloopers.knet.ui.core.components.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun ToolbarGroup(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
public fun RowScope.ToolbarSpacer(
    modifier: Modifier = Modifier
) {
    Spacer(modifier = modifier.weight(1f))
}

@Composable
public fun ToolbarSeparator(
    modifier: Modifier = Modifier
) {
    VerticalDivider(modifier = modifier.height(16.dp).padding(horizontal = 4.dp))
}

/**
 * Slot-driven Toolbar composable.
 */
@Composable
public fun KNetToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.toolbarHeight)
            .background(themeColors.background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        }
        if (content != null) {
            content()
        } else if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (trailing != null) {
            trailing()
        }
    }
}
