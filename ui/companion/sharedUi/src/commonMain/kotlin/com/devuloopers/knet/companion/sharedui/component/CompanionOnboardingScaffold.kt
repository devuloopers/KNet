package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.app_title
import com.devuloopers.knet.companion.sharedui.generated.resources.dismiss
import com.devuloopers.knet.companion.sharedui.generated.resources.step_certificate
import com.devuloopers.knet.companion.sharedui.generated.resources.step_connect
import com.devuloopers.knet.companion.sharedui.generated.resources.step_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.step_ready
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Consistent responsive shell for every companion setup stage. */
@Composable
internal fun CompanionOnboardingScaffold(
    title: String,
    summary: String,
    currentStep: Int,
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KNetTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KNetTheme.spacing.xl, vertical = KNetTheme.spacing.xxl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.app_title),
                style = KNetTheme.typography.labelMedium,
                color = KNetTheme.colors.accent,
            )
            CompanionProgress(currentStep = currentStep)
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                Text(text = title, style = KNetTheme.typography.display, color = KNetTheme.colors.textPrimary)
                Text(text = summary, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
            }
            state.failure?.let { failure ->
                KNetCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = KNetIcons.Warning,
                                contentDescription = null,
                                tint = KNetTheme.colors.semantic.error,
                            )
                            Text(
                                text = failure.message,
                                style = KNetTheme.typography.bodyMedium,
                                color = KNetTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (failure.recoverable) {
                            KNetButton(
                                onClick = { onAction(CompanionAction.ClearFailure) },
                                variant = ButtonVariant.Ghost,
                            ) {
                                Text(stringResource(Res.string.dismiss))
                            }
                        }
                    }
                }
            }
            content()
            Spacer(modifier = Modifier.height(KNetTheme.spacing.xl))
        }
    }
}

@Composable
private fun CompanionProgress(currentStep: Int) {
    val steps = listOf(
        Res.string.step_connect,
        Res.string.step_certificate,
        Res.string.step_inspection,
        Res.string.step_ready,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
    ) {
        steps.forEachIndexed { index, label ->
            CompanionProgressStep(label = label, selected = index <= currentStep)
        }
    }
}

@Composable
private fun RowScope.CompanionProgressStep(label: StringResource, selected: Boolean) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = if (selected) KNetTheme.colors.accent else KNetTheme.colors.border,
                    shape = CircleShape,
                ),
        )
        Text(
            text = stringResource(label),
            style = KNetTheme.typography.caption,
            color = if (selected) KNetTheme.colors.textPrimary else KNetTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

/** Compact semantic status row used by onboarding and the ready screen. */
@Composable
internal fun CompanionStatusRow(
    label: String,
    value: String,
    icon: ImageVector,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(KNetTheme.colors.surfaceVariant, KNetTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (positive) KNetTheme.colors.semantic.success else KNetTheme.colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = KNetTheme.typography.caption, color = KNetTheme.colors.textMuted)
            Text(text = value, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textPrimary)
        }
    }
}
