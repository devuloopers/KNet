package com.devuloopers.knet.ui.core.components.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
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
fun ToolbarGroup(
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
fun RowScope.ToolbarSpacer(
    modifier: Modifier = Modifier
) {
    Spacer(modifier = modifier.weight(1f))
}

@Composable
fun ToolbarSeparator(
    modifier: Modifier = Modifier
) {
    VerticalDivider(modifier = modifier.height(16.dp).padding(horizontal = 4.dp))
}

/**
 * Slot-driven Toolbar composable. Opt-in leading overflow keeps trailing controls fixed and gives
 * narrower windows a theme-owned horizontal scrollbar instead of overlapping or dropping actions.
 * The scrollbar occupies a separate row below the full-height controls; overflow never reduces their height.
 * @param scrollLeading When true, leading children use intrinsic width inside the scrollable area; omit child weights.
 */
@Composable
fun KNetToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable (RowScope.() -> Unit)? = null,
    scrollLeading: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val leadingScroll = if (leading != null && scrollLeading) rememberScrollState() else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (leadingScroll != null) Modifier.heightIn(min = dimensions.toolbarHeight)
                else Modifier.height(dimensions.toolbarHeight))
            .background(themeColors.background)
            .padding(horizontal = 8.dp),
        verticalAlignment = if (leadingScroll != null) Alignment.Top else Alignment.CenterVertically
    ) {
        if (leading != null && leadingScroll != null) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().height(dimensions.toolbarHeight).horizontalScroll(leadingScroll),
                    verticalAlignment = Alignment.CenterVertically) { leading() }
                KNetHorizontalScrollbar(leadingScroll, Modifier.fillMaxWidth())
            }
        } else if (leading != null) {
            leading()
        }
        if (content != null) {
            content()
        } else if (trailing != null && !scrollLeading) {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (trailing != null) {
            if (leadingScroll != null) {
                Row(Modifier.height(dimensions.toolbarHeight), verticalAlignment = Alignment.CenterVertically,
                    content = trailing)
            } else trailing()
        }
    }
}
