package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.runtime.Composable
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Operational Home has one primary circle, truthful native capture status and a single inset summary. */
@Composable
internal fun CompanionHomeScreen(state: CompanionHomeUiState, onAction: (CompanionAction) -> Unit) {
    CompanionHeroPage {
        CompanionHomeHero(state)
        CompanionInspectionControl(state, onAction)
        CompanionReadinessSummary(state)
        CompanionFootnote(stringResource(when {
            state.readiness == CompanionHomeReadiness.UNAVAILABLE -> Res.string.home_auto_checking
            state.inspectionControl == CompanionHomeInspectionControl.Stop -> Res.string.home_active_note
            state.inspectionControl == CompanionHomeInspectionControl.Starting ||
                state.inspectionControl == CompanionHomeInspectionControl.Stopping -> Res.string.home_inspection_busy_summary
            else -> Res.string.home_ready_note
        }))
        state.failureNotice?.let { notice ->
            CompanionPanel {
                CompanionInfo(notice.failure.message, KNetIcons.Warning, KNetTheme.colors.semantic.warning)
                if (notice is CompanionHomeFailureNotice.Dismissible) CompanionActionButton(stringResource(Res.string.dismiss),
                    { onAction(CompanionAction.ClearFailure) }, variant = ButtonVariant.Ghost)
            }
        }
    }
}

/** Native lifecycle owns both interactivity and the center glyph; busy circles never dispatch actions. */
@Composable
private fun CompanionInspectionControl(state: CompanionHomeUiState, onAction: (CompanionAction) -> Unit) {
    val control = state.inspectionControl
    val unavailable = state.readiness == CompanionHomeReadiness.UNAVAILABLE && control is CompanionHomeInspectionControl.Start
    val busy = control == CompanionHomeInspectionControl.Starting || control == CompanionHomeInspectionControl.Stopping
    val enabled = when (control) {
        is CompanionHomeInspectionControl.Start -> unavailable || control.enabled
        is CompanionHomeInspectionControl.Retry -> control.enabled
        CompanionHomeInspectionControl.Starting, CompanionHomeInspectionControl.Stopping -> false
        else -> true
    }
    val caption = when {
        unavailable -> Res.string.switch_desktop
        control == CompanionHomeInspectionControl.Stopping -> Res.string.home_stopping_title
        control == CompanionHomeInspectionControl.Stop -> Res.string.stop_inspection
        control == CompanionHomeInspectionControl.Starting -> Res.string.home_preparing_title
        control == CompanionHomeInspectionControl.ContinueVpnSetup -> Res.string.continue_vpn_setup
        control is CompanionHomeInspectionControl.Retry -> Res.string.retry
        else -> Res.string.start_inspection
    }
    val label = when {
        unavailable -> Res.string.control_switch
        control == CompanionHomeInspectionControl.Stopping -> Res.string.control_stopping
        control == CompanionHomeInspectionControl.Stop -> Res.string.control_stop
        control == CompanionHomeInspectionControl.Starting -> Res.string.control_starting
        control == CompanionHomeInspectionControl.ContinueVpnSetup -> Res.string.control_allow
        control is CompanionHomeInspectionControl.Retry -> Res.string.retry
        else -> Res.string.control_start
    }
    val symbol = when {
        unavailable -> CompanionControlSymbol.DESKTOP
        control == CompanionHomeInspectionControl.Stop || control == CompanionHomeInspectionControl.Stopping -> CompanionControlSymbol.STOP
        control == CompanionHomeInspectionControl.ContinueVpnSetup -> CompanionControlSymbol.SHIELD
        control is CompanionHomeInspectionControl.Retry -> CompanionControlSymbol.RETRY
        else -> CompanionControlSymbol.POWER
    }
    CompanionRoundControl(CompanionRoundControlState(symbol, stringResource(label), stringResource(caption),
        tone = when {
            control == CompanionHomeInspectionControl.Stop -> CompanionControlTone.SUCCESS
            unavailable -> CompanionControlTone.ACCENT
            else -> CompanionControlTone.ACCENT
        }, busy = busy, enabled = enabled), onClick = if (busy) null else {
        { onAction(when {
            unavailable -> CompanionAction.OpenDesktopsRequested
            control == CompanionHomeInspectionControl.Stop -> CompanionAction.StopInspectionRequested
            control == CompanionHomeInspectionControl.ContinueVpnSetup -> CompanionAction.VpnConsentRequested
            else -> CompanionAction.StartInspectionRequested
        }) }
    })
}
