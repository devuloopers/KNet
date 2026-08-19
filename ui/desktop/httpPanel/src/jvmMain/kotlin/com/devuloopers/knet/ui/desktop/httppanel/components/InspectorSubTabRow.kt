package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Shared sub-tab navigation bar component rendering formatted sub-tabs with entry counts
 * for both Request and Response inspectors.
 *
 * @param tabs List of supported sub-tabs to display.
 * @param activeTab Currently selected sub-tab.
 * @param onTabSelected Event callback when user selects a sub-tab.
 * @param headerCount Header entries count.
 * @param paramCount Query param entries count.
 * @param cookieCount Cookie entries count.
 * @param modifier Composable layout modifier.
 */
@Composable
fun InspectorSubTabRow(
    tabs: List<InspectorSubTab>,
    activeTab: InspectorSubTab,
    onTabSelected: (InspectorSubTab) -> Unit,
    headerCount: Int = 0,
    paramCount: Int = 0,
    cookieCount: Int = 0,
    modifier: Modifier = Modifier
) {
    KNetTabRow(
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEach { subTab ->
            val isSelected = subTab == activeTab
            val labelText = when (subTab) {
                InspectorSubTab.PARAMS -> "Params ($paramCount)"
                InspectorSubTab.AUTH -> "Auth"
                InspectorSubTab.HEADERS -> "Headers ($headerCount)"
                InspectorSubTab.BODY -> "Body"
                InspectorSubTab.COOKIES -> if (cookieCount > 0) "Cookies ($cookieCount)" else "Cookies"
                InspectorSubTab.SCRIPTS -> "Scripts"
            }
            KNetTab(
                title = labelText,
                selected = isSelected,
                onClick = { onTabSelected(subTab) }
            )
        }
    }
}
