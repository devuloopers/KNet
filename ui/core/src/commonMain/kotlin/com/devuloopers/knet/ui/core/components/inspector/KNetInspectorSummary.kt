package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.OverflowTextPopupHost
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Closed semantic palette for captured inspector values; colors always come from the KNet theme. */
enum class KNetInspectorTone { NEUTRAL, SUCCESS, INFO, WARNING, ERROR }

/** One small, payload-free summary metric. */
@Immutable
data class KNetInspectorMetric(val label: String, val value: String, val tone: KNetInspectorTone = KNetInspectorTone.NEUTRAL)

/** Persistent endpoint summary shared by application-protocol and opaque-transport inspectors. */
@Immutable
data class KNetInspectorSummary(
    val badge: String,
    val title: String,
    val subtitle: String,
    val copyValue: String,
    val metrics: List<KNetInspectorMetric>,
    val badgeTone: KNetInspectorTone = KNetInspectorTone.INFO,
)

/**
 * Renders the same endpoint identity and metrics above every inspector workspace.
 *
 * @param summary Display-only captured facts; callers own protocol and lifecycle interpretation.
 * @param modifier Hosting pane constraints. The shared minimum readable width and scrollbar protect narrow panes.
 */
@Composable
fun KNetInspectorSummaryHeader(summary: KNetInspectorSummary, modifier: Modifier = Modifier) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val horizontal = rememberInspectorScrollState(summary.copyValue)
    BoxWithConstraints(modifier.background(colors.panelHeader)) {
        val readableWidth = maxOf(maxWidth, KNetTheme.dimensions.inspectorMinWidth)
        Column {
            Column(Modifier.horizontalScroll(horizontal).width(readableWidth).padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    KNetInspectorText(summary.badge, color = summary.badgeTone.color(), bold = true)
                    KNetInspectorText(summary.title, Modifier.weight(1f), bold = true)
                    KNetCopyButton(textToCopy = summary.copyValue)
                }
                if (summary.subtitle.isNotBlank()) KNetInspectorText(summary.subtitle, Modifier.fillMaxWidth(), code = true)
                HorizontalDivider()
                KNetInspectorMetrics(summary.metrics)
            }
            KNetHorizontalScrollbar(horizontal)
        }
    }
}

/** Shared compact metric row, with stable single-line values and complete overflow previews. */
@Composable
fun KNetInspectorMetrics(metrics: List<KNetInspectorMetric>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
        metrics.forEach { metric ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                KNetInspectorText(metric.label, color = KNetTheme.colors.textSecondary, caption = true)
                KNetInspectorText(metric.value, Modifier.fillMaxWidth(), color = metric.tone.color(), bold = true)
            }
        }
    }
}

/** Resolves only semantic display intent, never hardcoded feature colors. */
@Composable
fun KNetInspectorTone.color() = when (this) {
    KNetInspectorTone.NEUTRAL -> KNetTheme.colors.textPrimary
    KNetInspectorTone.SUCCESS -> KNetTheme.colors.semantic.success
    KNetInspectorTone.INFO -> KNetTheme.colors.accent
    KNetInspectorTone.WARNING -> KNetTheme.colors.semantic.warning
    KNetInspectorTone.ERROR -> KNetTheme.colors.semantic.error
}

/** Single-line inspector text with an accessible, non-focusable preview when the value is clipped. */
@Composable
fun KNetInspectorText(text: String, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = KNetTheme.colors.textPrimary, bold: Boolean = false,
    code: Boolean = false, caption: Boolean = false) {
    val style = when { caption -> KNetTheme.typography.caption; code -> KNetTheme.typography.codeSmall; else -> KNetTheme.typography.bodySmall }
        .copy(color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    OverflowTextPopupHost(text, style, enabled = text.isNotBlank(), modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
