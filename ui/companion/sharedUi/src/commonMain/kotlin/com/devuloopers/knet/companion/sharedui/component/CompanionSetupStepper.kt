package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.step_certificate
import com.devuloopers.knet.companion.sharedui.generated.resources.step_connect
import com.devuloopers.knet.companion.sharedui.generated.resources.step_inspection
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Three-step, non-interactive onboarding indicator animated from semantic setup milestones. */
@Composable
internal fun CompanionSetupStepper(
    progress: CompanionSetupProgress,
    modifier: Modifier = Modifier,
) {
    val visualState = progress.toVisualState()
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    val firstConnectorProgress = animateFloatAsState(
        targetValue = visualState.firstConnectorProgress,
        animationSpec = tween(durationMillis = duration),
        label = "ConnectToCertificateProgress",
    ).value
    val secondConnectorProgress = animateFloatAsState(
        targetValue = visualState.secondConnectorProgress,
        animationSpec = tween(durationMillis = duration),
        label = "CertificateToInspectionProgress",
    ).value
    val steps = listOf(
        Res.string.step_connect to visualState.connect,
        Res.string.step_certificate to visualState.certificate,
        Res.string.step_inspection to visualState.inspection,
    )
    Box(modifier = modifier.fillMaxWidth()) {
        CompanionSetupConnectors(
            firstProgress = firstConnectorProgress,
            secondProgress = secondConnectorProgress,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            steps.forEachIndexed { index, (label, nodeState) ->
                CompanionSetupStepItem(
                    number = index + 1,
                    label = label,
                    state = nodeState,
                )
            }
        }
    }
}

@Composable
private fun CompanionSetupConnectors(
    firstProgress: Float,
    secondProgress: Float,
) {
    val pendingColor = KNetTheme.colors.border
    val completedColor = KNetTheme.colors.accent
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val centerY = size.height / 2f
        val firstCenter = size.width / 6f
        val secondCenter = size.width / 2f
        val thirdCenter = size.width * 5f / 6f
        val strokeWidth = 2.dp.toPx()

        drawLine(
            color = pendingColor,
            start = Offset(firstCenter, centerY),
            end = Offset(secondCenter, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = pendingColor,
            start = Offset(secondCenter, centerY),
            end = Offset(thirdCenter, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = completedColor,
            start = Offset(firstCenter, centerY),
            end = Offset(
                firstCenter + ((secondCenter - firstCenter) * firstProgress),
                centerY,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = completedColor,
            start = Offset(secondCenter, centerY),
            end = Offset(
                secondCenter + ((thirdCenter - secondCenter) * secondProgress),
                centerY,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RowScope.CompanionSetupStepItem(
    number: Int,
    label: StringResource,
    state: CompanionSetupNodeState,
) {
    val emphasized = state != CompanionSetupNodeState.Pending
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    val nodeColor = animateColorAsState(
        targetValue = if (emphasized) KNetTheme.colors.accent else KNetTheme.colors.surfaceVariant,
        animationSpec = tween(durationMillis = duration),
        label = "SetupNodeColor",
    ).value
    val borderColor = animateColorAsState(
        targetValue = if (emphasized) KNetTheme.colors.accent else KNetTheme.colors.border,
        animationSpec = tween(durationMillis = duration),
        label = "SetupNodeBorderColor",
    ).value
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
    ) {
        KNetSurface(
            modifier = Modifier.size(48.dp),
            color = nodeColor,
            shape = CircleShape,
            border = BorderStroke(width = 1.dp, color = borderColor),
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state == CompanionSetupNodeState.Complete) {
                    Icon(
                        imageVector = KNetIcons.Check,
                        contentDescription = null,
                        tint = KNetTheme.colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = KNetTheme.typography.heading,
                        color = if (emphasized) {
                            KNetTheme.colors.textPrimary
                        } else {
                            KNetTheme.colors.textSecondary
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(label),
            style = KNetTheme.typography.bodyMedium,
            color = if (emphasized) KNetTheme.colors.accent else KNetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

internal enum class CompanionSetupNodeState {
    Pending,
    Active,
    Complete,
}

internal data class CompanionSetupVisualState(
    val connect: CompanionSetupNodeState,
    val certificate: CompanionSetupNodeState,
    val inspection: CompanionSetupNodeState,
    val firstConnectorProgress: Float,
    val secondConnectorProgress: Float,
)

internal fun CompanionSetupProgress.toVisualState(): CompanionSetupVisualState = when (this) {
    CompanionSetupProgress.Scanning -> CompanionSetupVisualState(
        connect = CompanionSetupNodeState.Active,
        certificate = CompanionSetupNodeState.Pending,
        inspection = CompanionSetupNodeState.Pending,
        firstConnectorProgress = 0f,
        secondConnectorProgress = 0f,
    )

    CompanionSetupProgress.DesktopConnected -> CompanionSetupVisualState(
        connect = CompanionSetupNodeState.Complete,
        certificate = CompanionSetupNodeState.Pending,
        inspection = CompanionSetupNodeState.Pending,
        firstConnectorProgress = 0.5f,
        secondConnectorProgress = 0f,
    )

    CompanionSetupProgress.CertificateDownloaded -> CompanionSetupVisualState(
        connect = CompanionSetupNodeState.Complete,
        certificate = CompanionSetupNodeState.Active,
        inspection = CompanionSetupNodeState.Pending,
        firstConnectorProgress = 1f,
        secondConnectorProgress = 0f,
    )

    CompanionSetupProgress.CertificateVerified -> CompanionSetupVisualState(
        connect = CompanionSetupNodeState.Complete,
        certificate = CompanionSetupNodeState.Complete,
        inspection = CompanionSetupNodeState.Active,
        firstConnectorProgress = 1f,
        secondConnectorProgress = 1f,
    )
}
