package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.badge.MethodBadge
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab

/**
 * Multi-tab header switcher bar for active HTTP request tabs in API Studio.
 *
 * @param tabs List of open request tabs.
 * @param activeTabId Currently selected tab ID.
 * @param onTabSelected Callback when a tab is selected.
 * @param onTabClosed Callback when a tab close 'x' is clicked.
 * @param onNewTabClicked Callback when '+' new tab button is clicked.
 * @param modifier Layout modifier.
 */
@Composable
public fun RequestTabBar(
    tabs: List<RequestTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTabClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            Row(
                modifier = Modifier
                    .background(
                        color = if (isActive) KNetColors.SurfaceDark else KNetColors.BackgroundDark,
                        shape = KNetShapes.Small
                    )
                    .clickable { onTabSelected(tab.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MethodBadge(method = tab.method)
                Text(
                    text = tab.title,
                    color = if (isActive) KNetColors.TextPrimary else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "×",
                    color = KNetColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onTabClosed(tab.id) }
                )
            }
        }

        Text(
            text = "+",
            color = KNetColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { onNewTabClicked() }
                .padding(horizontal = 8.dp)
        )
    }
}
