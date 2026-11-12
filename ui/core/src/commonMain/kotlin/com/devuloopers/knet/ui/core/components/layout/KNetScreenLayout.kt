package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.devuloopers.knet.ui.core.foundation.responsive.ResponsiveLayout
import com.devuloopers.knet.ui.core.foundation.responsive.WindowSizeClass
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Consistent bounded manager page with a shared header, page insets and content spacing.
 * Short viewports use the shared compact spacing classification to preserve list and table height.
 *
 * @param title Page name, matching its navigation label.
 * @param subtitle Optional one-line explanation of the page's current purpose or state.
 * @param modifier Bounded viewport provided by the workspace host.
 * @param actions Optional existing page controls, placed by [KNetScreenHeader].
 * @param content Feature-owned controls and content; weighted children may fill the remaining page height.
 */
@Composable
fun KNetScreenLayout(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = KNetTheme.spacing
    ResponsiveLayout(modifier.background(KNetTheme.colors.background)) { windowInfo ->
        val contentGap = if (windowInfo.heightSizeClass == WindowSizeClass.Compact) spacing.sm else spacing.lg
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xxl, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(contentGap),
        ) {
            KNetScreenHeader(title, subtitle, actions = actions)
            content()
        }
    }
}

/**
 * Shared screen heading with stable title/subtitle baselines and bounded trailing actions.
 *
 * Actions move below the heading when their measured width leaves insufficient room for the title block.
 * Individual controls flow within the available width; text stays on one line and ellipsizes. The heading
 * remains top-aligned even when actions are taller, so switching screens preserves its position.
 *
 * @param title Accessible page heading rendered with the KNet display typography.
 * @param subtitle Optional secondary explanation rendered with the KNet small body typography.
 * @param modifier Header viewport; normally a full-width child of [KNetScreenLayout].
 * @param actions Optional feature controls. Callers supply their behavior, not resize rules.
 */
@Composable
fun KNetScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val spacing = KNetTheme.spacing
    val colors = KNetTheme.colors
    val minimumHeight = KNetTheme.dimensions.screenHeaderMinHeight
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = KNetTheme.typography.display,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) Text(
                    text = subtitle,
                    style = KNetTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions != null) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) { actions() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable = measurables.getOrNull(1)?.measure(loose)
        val gap = if (actionPlaceable == null) 0 else spacing.lg.roundToPx()
        val titleWidth = measurables.first().maxIntrinsicWidth(Constraints.Infinity)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth
            else constraints.constrainWidth(titleWidth + (actionPlaceable?.width ?: 0) + gap)
        val availableTitleWidth = (width - (actionPlaceable?.width ?: 0) - gap).coerceAtLeast(0)
        val stacked = actionPlaceable != null && titleWidth > availableTitleWidth
        val headingPlaceable = measurables.first().measure(loose.copy(
            maxWidth = if (stacked) width else availableTitleWidth,
        ))
        val naturalHeight = if (stacked) headingPlaceable.height + gap + actionPlaceable.height
            else maxOf(headingPlaceable.height, actionPlaceable?.height ?: 0)
        val height = constraints.constrainHeight(maxOf(minimumHeight.roundToPx(), naturalHeight))
        layout(width, height) {
            headingPlaceable.placeRelative(0, 0)
            actionPlaceable?.placeRelative(
                x = if (stacked) 0 else width - actionPlaceable.width,
                y = if (stacked) headingPlaceable.height + gap else (height - actionPlaceable.height) / 2,
            )
        }
    }
}
