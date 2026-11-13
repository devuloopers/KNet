package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Labels sit above aligned nodes; semantic milestones never imply that native capture has started. */
@Composable
internal fun CompanionSetupStepper(progress: CompanionSetupProgress, modifier: Modifier = Modifier) {
    val visual = progress.toVisualState()
    val nodes = listOf(visual.connect, visual.certificate, visual.inspection)
    val labels = listOf(Res.string.step_connect, Res.string.step_certificate, Res.string.step_inspection).map { stringResource(it) }
    val accent = KNetTheme.colors.accent
    val muted = KNetTheme.colors.textSecondary
    val border = KNetTheme.colors.border
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(label, Modifier.weight(1f).semantics { stateDescription = nodes[index].name },
                    style = KNetTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    color = if (nodes[index] == CompanionSetupNodeState.Pending) muted else accent)
            }
        }
        Box(Modifier.fillMaxWidth().height(18.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val first = size.width / 6
                val middle = size.width / 2
                val last = size.width * 5 / 6
                drawLine(border, Offset(first, center.y), Offset(last, center.y), 1.dp.toPx(), StrokeCap.Round)
                if (visual.firstConnectorProgress > 0) drawLine(accent, Offset(first, center.y), Offset(middle, center.y), 2.dp.toPx())
                if (visual.secondConnectorProgress > 0) drawLine(accent, Offset(middle, center.y), Offset(last, center.y), 2.dp.toPx())
                nodes.forEachIndexed { index, state ->
                    drawCircle(if (state == CompanionSetupNodeState.Pending) muted else accent,
                        if (state == CompanionSetupNodeState.Pending) 3.dp.toPx() else 7.dp.toPx(),
                        Offset(size.width * (index * 2 + 1) / 6, center.y))
                }
            }
            Row(Modifier.fillMaxSize()) {
                nodes.forEach { state -> Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (state == CompanionSetupNodeState.Complete) Icon(KNetIcons.Check, null, Modifier.size(12.dp), tint = KNetTheme.colors.textPrimary)
                } }
            }
        }
    }
}

/** Non-interactive setup node states; Active is the next milestone, not a running inspection. */
internal enum class CompanionSetupNodeState { Pending, Active, Complete }

/** Deterministic connectors shared by the visual indicator and milestone regression checks. */
internal data class CompanionSetupVisualState(
    val connect: CompanionSetupNodeState,
    val certificate: CompanionSetupNodeState,
    val inspection: CompanionSetupNodeState,
    val firstConnectorProgress: Float,
    val secondConnectorProgress: Float,
)

/** Exact-root verification completes Trust; pairing alone activates its verification stage. */
internal fun CompanionSetupProgress.toVisualState(): CompanionSetupVisualState = when (this) {
    CompanionSetupProgress.Scanning -> CompanionSetupVisualState(CompanionSetupNodeState.Active,
        CompanionSetupNodeState.Pending, CompanionSetupNodeState.Pending, 0f, 0f)
    CompanionSetupProgress.DesktopConnected, CompanionSetupProgress.CertificateDownloaded -> CompanionSetupVisualState(
        CompanionSetupNodeState.Complete, CompanionSetupNodeState.Active, CompanionSetupNodeState.Pending, 1f, 0f)
    CompanionSetupProgress.CertificateVerified -> CompanionSetupVisualState(CompanionSetupNodeState.Complete,
        CompanionSetupNodeState.Complete, CompanionSetupNodeState.Active, 1f, 1f)
}
