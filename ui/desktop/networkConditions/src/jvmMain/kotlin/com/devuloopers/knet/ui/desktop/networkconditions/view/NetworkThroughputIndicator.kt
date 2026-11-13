package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltip
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.formatNetworkRate

/**
 * Compact actual KNet throughput, independent of configured conditions and Traffic's row filters.
 * @param history Shared one-second measurements; startup shows zero, later invalid intervals show a dash.
 * @param onOpenOverview Opens the existing complete monitor without adding an inline graph.
 * @param modifier Host-owned footer placement; overflow belongs to the shared statistics bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkThroughputIndicator(history: NetworkThroughputHistory, onOpenOverview: () -> Unit, modifier: Modifier = Modifier,
    scopeDescription: String = "Actual throughput across KNet, independent of Traffic filters. Open Network Conditions Overview.") {
    val download = when {
        history.sampleCount == 0 -> formatNetworkRate(0L)
        history.currentSampleIsValid -> formatNetworkRate(history.currentDownloadBitsPerSecond)
        else -> "—"
    }
    val upload = when {
        history.sampleCount == 0 -> formatNetworkRate(0L)
        history.currentSampleIsValid -> formatNetworkRate(history.currentUploadBitsPerSecond)
        else -> "—"
    }
    val style = KNetTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reservedWidth = remember(style, measurer, density) {
        with(density) { measurer.measure("999.9 Mbps", style = style, maxLines = 1, softWrap = false).size.width.toDp() }
    }
    TooltipArea(tooltip = {
        KNetTooltip(scopeDescription)
    }, modifier = modifier) {
        Row(Modifier.clickable(role = Role.Button, onClickLabel = "Open Network Conditions Overview", onClick = onOpenOverview)
            .handCursor().semantics(mergeDescendants = true) {
                contentDescription = "Actual KNet throughput. Download $download, upload $upload. Open Network Conditions Overview."
            }.padding(vertical = KNetTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
            DirectionReading("Download", download, KNetIcons.Download, KNetTheme.colors.accent, reservedWidth)
            DirectionReading("Upload", upload, KNetIcons.Upload, KNetTheme.colors.semantic.success, reservedWidth)
        }
    }
}

@Composable
private fun DirectionReading(label: String, value: String, icon: ImageVector, color: Color, reservedWidth: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Icon(icon, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall), tint = color)
        Text(label, style = KNetTheme.typography.caption, color = KNetTheme.colors.textSecondary, maxLines = 1, softWrap = false)
        Text(value, Modifier.widthIn(min = reservedWidth), style = KNetTheme.typography.caption.copy(
            fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = KNetTheme.colors.textPrimary,
            textAlign = TextAlign.Start, maxLines = 1, softWrap = false)
    }
}
