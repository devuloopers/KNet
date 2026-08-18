package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Desktop application workspace scaffold composable.
 * Provides canonical layout hierarchy: Toolbar -> (Navigation + Main Workspace) -> Status Bar.
 */
@Composable
fun KNetWorkspaceScaffold(
    modifier: Modifier = Modifier,
    toolbar: (@Composable () -> Unit)? = null,
    navigationRail: (@Composable () -> Unit)? = null,
    sidebar: (@Composable () -> Unit)? = null,
    detailPane: (@Composable () -> Unit)? = null,
    bottomPane: (@Composable () -> Unit)? = null,
    statusBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        if (toolbar != null) {
            toolbar()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (navigationRail != null) {
                navigationRail()
            }
            if (sidebar != null) {
                sidebar()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (detailPane != null) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    content()
                                }
                                detailPane()
                            }
                        } else {
                            content()
                        }
                    }
                    if (bottomPane != null) {
                        bottomPane()
                    }
                }
            }
        }

        if (statusBar != null) {
            statusBar()
        }
    }
}
