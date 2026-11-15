package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.CompanionFootnote
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Reserves actual wrapped runtime copy height so centered Home controls do not move between states. */
@Composable
internal fun CompanionHomeFootnote(state: CompanionHomeUiState) {
    val resource = when {
        state.readiness == CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_auto_checking
        state.inspectionControl == CompanionHomeInspectionControl.Stop -> Res.string.home_active_note
        state.inspectionControl == CompanionHomeInspectionControl.Starting ||
            state.inspectionControl == CompanionHomeInspectionControl.Stopping -> Res.string.home_inspection_busy_summary
        else -> Res.string.home_ready_note
    }
    val variants = listOf(Res.string.home_auto_checking, Res.string.home_active_note,
        Res.string.home_inspection_busy_summary, Res.string.home_ready_note).map { stringResource(it) }
    val measurer = rememberTextMeasurer(cacheSize = variants.size)
    val density = LocalDensity.current
    val style = KNetTheme.typography.bodySmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val reservedHeight = remember(measurer, width, variants, style) {
            val textConstraints = Constraints(maxWidth = width)
            variants.maxOf { measurer.measure(it, style = style, constraints = textConstraints).size.height }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = with(density) { reservedHeight.toDp() })) {
            CompanionFootnote(stringResource(resource))
        }
    }
}
