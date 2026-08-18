package com.devuloopers.knet.ui.core.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode

enum class CatalogTab {
    Foundation,
    Components,
    Layout,
    Theme,
    Responsive
}

/**
 * Single host entry point for live KNet Design System v2.0 catalog.
 */
@Composable
fun DesignSystemCatalog(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(CatalogTab.Foundation) }
    var currentThemeMode by remember { mutableStateOf(ThemeMode.Dark) }

    KNetTheme(themeMode = currentThemeMode) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(KNetTheme.colors.background)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                KNetTabRow(modifier = Modifier.weight(1f)) {
                    CatalogTab.entries.forEach { tab ->
                        KNetTab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            title = tab.name
                        )
                    }
                }
                KNetButton(
                    onClick = {
                        currentThemeMode = if (currentThemeMode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark
                    },
                    variant = ButtonVariant.Secondary
                ) {
                    androidx.compose.material3.Text("Theme: ${currentThemeMode.name}")
                }
            }
            HorizontalDivider()

            // Active Catalog Tab Body
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                when (selectedTab) {
                    CatalogTab.Foundation -> FoundationCatalog()
                    CatalogTab.Components -> ComponentCatalog()
                    CatalogTab.Layout -> LayoutCatalog()
                    CatalogTab.Theme -> ThemeCatalog()
                    CatalogTab.Responsive -> ResponsiveCatalog()
                }
            }
        }
    }
}
