package com.devuloopers.knet.ui.core.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density IDE Table Header Row.
 */
@Composable
fun KNetTableHeader(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.tableRowHeight)
            .background(themeColors.panelHeader)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * High-density IDE Table Row.
 */
@Composable
fun KNetRow(
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val themeColors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val bg = if (selected) themeColors.interaction.selectedOverlay else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.tableRowHeight)
            .background(bg)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * High-density Table Cell.
 */
@Composable
fun KNetCell(
    text: String,
    modifier: Modifier = Modifier,
    isHeader: Boolean = false,
    color: Color = if (isHeader) KNetTheme.colors.textSecondary else KNetTheme.colors.textPrimary
) {
    val typography = KNetTheme.typography

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = if (isHeader) typography.labelSmall.copy(color = color) else typography.codeSmall.copy(color = color),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
