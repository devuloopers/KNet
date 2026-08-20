package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab

/**
 * Left category navigation sidebar for SettingsScreen.
 */
@Composable
fun SettingsSidebar(
    activeTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val sidebarScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .width(KNetTheme.dimensions.sidebarWidth)
            .fillMaxHeight()
            .background(themeColors.surface)
            .border(1.dp, themeColors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .verticalScroll(sidebarScrollState)
                .padding(vertical = 16.dp, horizontal = 12.dp),
        ) {
            Text(
                text = "Settings",
                style = typography.titleSmall.copy(color = themeColors.textPrimary),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )

            SidebarNavItem(
                label = "Network & Proxy",
                icon = Icons.Default.Router,
                isSelected = activeTab == SettingsTab.NETWORK_PROXY,
                onClick = { onTabSelected(SettingsTab.NETWORK_PROXY) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            SidebarNavItem(
                label = "Traffic & Storage",
                icon = Icons.AutoMirrored.Filled.List,
                isSelected = activeTab == SettingsTab.TRAFFIC_STORAGE,
                onClick = { onTabSelected(SettingsTab.TRAFFIC_STORAGE) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            SidebarNavItem(
                label = "Appearance",
                icon = Icons.Default.Palette,
                isSelected = activeTab == SettingsTab.APPEARANCE,
                onClick = { onTabSelected(SettingsTab.APPEARANCE) },
            )
        }
        KNetVerticalScrollbar(
            scrollState = sidebarScrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val background = if (isSelected) themeColors.accent.copy(alpha = 0.12f) else themeColors.surface
    val contentColor = if (isSelected) themeColors.accent else themeColors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(KNetTheme.shapes.small)
            .background(background)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick,
            )
            .handCursor()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = typography.bodySmall.copy(color = contentColor),
            maxLines = 1,
            softWrap = false
        )
    }
}
