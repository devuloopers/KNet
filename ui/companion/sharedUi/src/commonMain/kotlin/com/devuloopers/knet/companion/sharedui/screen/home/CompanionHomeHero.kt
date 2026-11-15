package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.CompanionHeading
import com.devuloopers.knet.companion.sharedui.component.CompanionHeadingGap
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** A reserved heading region keeps the circle stable across readiness and native lifecycle updates. */
@Composable
internal fun CompanionHomeHero(state: CompanionHomeUiState) {
    val stopping = state.inspectionControl == CompanionHomeInspectionControl.Stopping
    val title = if (stopping) Res.string.home_stopping_title else when (state.readiness) {
        CompanionHomeReadiness.CHECKING -> Res.string.home_checking_title
        CompanionHomeReadiness.READY -> Res.string.home_ready_title
        CompanionHomeReadiness.PREPARING -> Res.string.home_preparing_title
        CompanionHomeReadiness.ACTIVE -> Res.string.home_active_title
        CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_unavailable_title
        CompanionHomeReadiness.NEEDS_ATTENTION -> Res.string.home_attention_title
    }
    val summary = if (stopping) Res.string.home_stopping_summary else when (state.readiness) {
        CompanionHomeReadiness.CHECKING -> Res.string.home_checking_summary
        CompanionHomeReadiness.READY -> Res.string.home_ready_summary
        CompanionHomeReadiness.PREPARING -> Res.string.home_preparing_summary
        CompanionHomeReadiness.ACTIVE -> Res.string.home_active_summary
        CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_unavailable_summary
        CompanionHomeReadiness.NEEDS_ATTENTION -> Res.string.home_attention_summary
    }
    val variants = listOf(
        Res.string.home_checking_title to Res.string.home_checking_summary,
        Res.string.home_ready_title to Res.string.home_ready_summary,
        Res.string.home_preparing_title to Res.string.home_preparing_summary,
        Res.string.home_active_title to Res.string.home_active_summary,
        Res.string.home_unavailable_title to Res.string.home_unavailable_summary,
        Res.string.home_attention_title to Res.string.home_attention_summary,
        Res.string.home_stopping_title to Res.string.home_stopping_summary,
    ).map { (titleResource, summaryResource) -> stringResource(titleResource) to stringResource(summaryResource) }
    val measurer = rememberTextMeasurer(cacheSize = variants.size * 2)
    val density = LocalDensity.current
    val titleStyle = KNetTheme.typography.hero
    val summaryStyle = KNetTheme.typography.bodyLarge
    val gap = with(density) { CompanionHeadingGap.roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val reservedHeight = remember(measurer, width, variants, titleStyle, summaryStyle, gap) {
            // Measure only text; no invisible alternative headings enter composition or accessibility.
            val textConstraints = Constraints(maxWidth = width)
            variants.maxOf { (titleText, summaryText) ->
                measurer.measure(titleText, style = titleStyle, constraints = textConstraints).size.height + gap +
                    measurer.measure(summaryText, style = summaryStyle, constraints = textConstraints).size.height
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = maxOf(100.dp, with(density) { reservedHeight.toDp() }))) {
            CompanionHeading(stringResource(title), stringResource(summary), centered = true)
        }
    }
}
