package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

/**
 * Top horizontal request tab bar for switching open API requests in API Studio.
 */
@Composable
fun RequestTabBar(
    tabs: List<RequestTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTabClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(themeColors.surfaceVariant)
            .border(width = 1.dp, color = themeColors.border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KNetTabRow(
            modifier = Modifier.weight(1f)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == activeTabId

                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 180.dp, max = 240.dp)
                        .background(if (isSelected) themeColors.surface else Color.Transparent)
                        .border(width = 1.dp, color = themeColors.border)
                        .clickable { onTabSelected(tab.id) }
                        .handCursor()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KNetBadge(
                        text = tab.method,
                        containerColor = ApiStudioColors.getMethodBackgroundColor(tab.method),
                        contentColor = ApiStudioColors.getMethodTextColor(tab.method)
                    )

                    Text(
                        text = tab.title,
                        style = typography.bodySmall.copy(
                            color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (tab.isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(themeColors.accent, shape = KNetTheme.shapes.pill)
                        )
                    }

                    Icon(
                        imageVector = KNetIcons.Close,
                        contentDescription = "Close Tab",
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onTabClosed(tab.id) }
                            .handCursor(),
                        tint = themeColors.textMuted
                    )
                }
            }
        }

        KNetIconButton(
            onClick = onNewTabClicked,
            icon = KNetIcons.Add,
            contentDescription = "New Tab",
            modifier = Modifier.padding(horizontal = 4.dp),
            tint = themeColors.textSecondary
        )
    }
}
