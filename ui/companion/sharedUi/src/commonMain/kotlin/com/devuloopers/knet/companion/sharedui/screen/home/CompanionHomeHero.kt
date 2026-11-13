package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.CompanionHeading
import com.devuloopers.knet.companion.sharedui.generated.resources.*
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
    Box(Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
        CompanionHeading(stringResource(title), stringResource(summary), centered = true)
    }
}
