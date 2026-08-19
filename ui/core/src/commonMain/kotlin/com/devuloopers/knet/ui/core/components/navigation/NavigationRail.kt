package com.devuloopers.knet.ui.core.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Selectable destination in the primary navigation rail. */
@Composable
fun NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes

    val backgroundColor = if (selected) themeColors.surfaceVariant else themeColors.surface
    val iconColor = if (selected) themeColors.accent else themeColors.textSecondary

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(shapes.small)
            .background(backgroundColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .handCursor(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
    }
}

/** Vertically spaced group of navigation destinations. */
@Composable
fun NavigationSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/** Footer group anchored to the bottom of a navigation rail. */
@Composable
fun ColumnScope.NavigationFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/** Fixed-width KNet primary navigation container. */
@Composable
fun KNetNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions

    Column(
        modifier = modifier
            .width(dimensions.navigationWidth)
            .fillMaxHeight()
            .selectableGroup()
            .background(themeColors.surface)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
