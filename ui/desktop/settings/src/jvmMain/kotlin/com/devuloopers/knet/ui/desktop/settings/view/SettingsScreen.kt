package com.devuloopers.knet.ui.desktop.settings.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dialog.ConfirmDialog
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.responsive.ResponsiveLayout
import com.devuloopers.knet.ui.core.foundation.responsive.WindowSizeClass
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.components.AppearanceTab
import com.devuloopers.knet.ui.desktop.settings.components.NetworkProxyTab
import com.devuloopers.knet.ui.desktop.settings.components.SettingsFooterBar
import com.devuloopers.knet.ui.desktop.settings.components.SettingsSidebar
import com.devuloopers.knet.ui.desktop.settings.components.TrafficStorageTab
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsNoticeTone
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel

/** Connects the Settings ViewModel state and intent boundary to the desktop screen. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreenContent(
        state = state,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

/** Pure Settings renderer used by the product screen and presentation tests. */
@Composable
internal fun SettingsScreenContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors

    ResponsiveLayout(modifier.fillMaxSize()) { windowInfo ->
        val compact = windowInfo.widthSizeClass == WindowSizeClass.Compact
        Column(Modifier.fillMaxSize().background(colors.background)) {
            if (compact) {
                KNetTabRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SettingsTab.entries.forEach { tab ->
                        KNetTab(
                            title = tab.displayName,
                            selected = state.activeTab == tab,
                            onClick = { onIntent(SettingsIntent.SelectTab(tab)) },
                        )
                    }
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!compact) {
                    SettingsSidebar(
                        activeTab = state.activeTab,
                        onTabSelected = { onIntent(SettingsIntent.SelectTab(it)) },
                    )
                }

                key(state.activeTab) {
                    val scrollState = rememberScrollState()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(
                                    horizontal = if (compact) 16.dp else 28.dp,
                                    vertical = 24.dp,
                                ),
                        ) {
                            when (state.activeTab) {
                                SettingsTab.NETWORK_PROXY -> NetworkProxyTab(
                                    state = state,
                                    onIntent = onIntent,
                                    compact = compact,
                                )
                                SettingsTab.TRAFFIC_STORAGE -> TrafficStorageTab(
                                    state = state,
                                    onIntent = onIntent,
                                    compact = compact,
                                )
                                SettingsTab.APPEARANCE -> AppearanceTab(
                                    state = state,
                                    onIntent = onIntent,
                                    compact = compact,
                                )
                            }
                        }
                        KNetVerticalScrollbar(
                            scrollState = scrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }

            SettingsFooterBar(state = state, onIntent = onIntent)
        }
    }

    if (state.isResetConfirmationVisible) {
        ConfirmDialog(
            title = "Reset application settings?",
            message = "This restores the proxy port, startup policy, scripting default, and request timeouts. An active proxy may restart on the default port.",
            confirmText = "Reset Defaults",
            onConfirm = { onIntent(SettingsIntent.ConfirmResetDefaults) },
            onDismissRequest = { onIntent(SettingsIntent.CancelResetDefaults) },
        )
    }

    state.notice?.details?.let { details ->
        AlertDialog(
            title = when (state.notice.tone) {
                SettingsNoticeTone.ERROR -> "Settings Error"
                SettingsNoticeTone.WARNING -> "Action Required"
                SettingsNoticeTone.INFO -> "Settings Information"
                SettingsNoticeTone.SUCCESS -> "Settings Updated"
            },
            message = details,
            onDismissRequest = { onIntent(SettingsIntent.DismissNotice) },
        )
    }
}
