package com.devuloopers.knet.ui.core.components.breadcrumbs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun KNetBreadcrumbItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLast: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(color = if (isLast) themeColors.textPrimary else themeColors.textSecondary),
            modifier = Modifier
                .clickable(onClick = onClick)
                .handCursor()
        )
        if (!isLast) {
            Icon(
                imageVector = KNetIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(12.dp)
                    .padding(horizontal = 2.dp),
                tint = themeColors.textMuted
            )
        }
    }
}

@Composable
fun KNetBreadcrumbBar(
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (label, onClick) ->
            KNetBreadcrumbItem(
                label = label,
                onClick = onClick,
                isLast = index == items.lastIndex
            )
        }
    }
}
