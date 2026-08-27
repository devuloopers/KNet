package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Persistent companion setup chrome. The brand and progress indicator remain mounted while only [content] changes
 * and scrolls below them.
 */
@Composable
internal fun CompanionSetupScaffold(
    progress: CompanionSetupProgress,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetTheme.colors.background)
            .safeDrawingPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = KNetTheme.spacing.lg,
                    top = KNetTheme.spacing.xl,
                    end = KNetTheme.spacing.lg,
                    bottom = KNetTheme.spacing.lg,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            ) {
                CompanionBrandHeader()
                CompanionSetupStepper(
                    progress = progress,
                    modifier = Modifier.padding(top = KNetTheme.spacing.xxl),
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = KNetTheme.colors.border,
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            content = content,
        )
    }
}
