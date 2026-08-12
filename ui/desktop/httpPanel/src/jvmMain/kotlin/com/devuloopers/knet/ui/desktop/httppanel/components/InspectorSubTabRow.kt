package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Shared sub-tab navigation bar component rendering formatted sub-tabs with entry counts
 * for both Request and Response inspectors.
 *
 * @param tabs List of supported sub-tabs to display (e.g. [InspectorSubTab.RequestTabs] or [InspectorSubTab.ResponseTabs]).
 * @param activeTab Currently selected sub-tab.
 * @param onTabSelected Event callback when user selects a sub-tab.
 * @param headerCount Header entries count.
 * @param paramCount Query param entries count.
 * @param cookieCount Cookie entries count.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun InspectorSubTabRow(
    tabs: List<InspectorSubTab>,
    activeTab: InspectorSubTab,
    onTabSelected: (InspectorSubTab) -> Unit,
    headerCount: Int = 0,
    paramCount: Int = 0,
    cookieCount: Int = 0,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEach { subTab ->
            val isSelected = subTab == activeTab
            val labelText = when (subTab) {
                InspectorSubTab.BODY -> "Body"
                InspectorSubTab.HEADERS -> "Headers ($headerCount)"
                InspectorSubTab.PARAMS -> "Params ($paramCount)"
                InspectorSubTab.COOKIES -> if (cookieCount > 0) "Cookies ($cookieCount)" else "Cookies"
            }
            KNetTab(
                title = labelText,
                selected = isSelected,
                onClick = { onTabSelected(subTab) }
            )
        }
    }
}
