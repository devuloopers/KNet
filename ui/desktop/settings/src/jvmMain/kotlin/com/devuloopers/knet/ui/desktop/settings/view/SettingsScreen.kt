package com.devuloopers.knet.ui.desktop.settings.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.components.*
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch

/**
 * Modern Application Settings Screen composable for KNet Desktop.
 *
 * Assembles header, category sidebar, tab panel content, and footer.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreenContent(
        state = state,
        onIntent = viewModel::processIntent,
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsScreenContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        val isNarrow = maxWidth < 550.dp
        val horizontalPadding = if (maxWidth < 800.dp) 16.dp else 28.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Body (Sidebar + Content Panel)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!isNarrow) {
                    SettingsSidebar(
                        activeTab = state.activeTab,
                        onTabSelected = { onIntent(SettingsIntent.SelectTab(it)) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(contentScrollState)
                        .padding(horizontal = horizontalPadding, vertical = 24.dp)
                ) {
                    when (state.activeTab) {
                        SettingsTab.NETWORK_PROXY -> NetworkProxyTab(
                            state = state,
                            onIntent = onIntent,
                            onCopyPath = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipEntry(StringSelection(state.dataDirectory)))
                                }
                            }
                        )

                        SettingsTab.TRAFFIC_STORAGE -> TrafficStorageTab(
                            state = state,
                            onIntent = onIntent
                        )

                        SettingsTab.APPEARANCE -> AppearanceTab(
                            state = state,
                            onIntent = onIntent
                        )
                    }
                }
            }

            // Footer
            SettingsFooterBar(
                message = state.message,
                onResetDefaults = { onIntent(SettingsIntent.ResetDefaults) }
            )
        }
    }
}
