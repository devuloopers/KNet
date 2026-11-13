package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.runtime.Composable
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFailureKind
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFailureUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Typed pairing failures select specific recovery copy without parsing platform exception strings. */
@Composable
internal fun failureTitle(kind: CompanionConnectFailureKind): String = stringResource(
    when (kind) {
        CompanionConnectFailureKind.INVALID_QR -> Res.string.scan_failure_invalid_title
        CompanionConnectFailureKind.EXPIRED_QR -> Res.string.scan_failure_expired_title
        CompanionConnectFailureKind.DESKTOP_UNREACHABLE -> Res.string.scan_failure_unreachable_title
        CompanionConnectFailureKind.SECURITY -> Res.string.scan_failure_security_title
        CompanionConnectFailureKind.PAIRING -> Res.string.scan_failure_pairing_title
        CompanionConnectFailureKind.GENERAL -> Res.string.scan_failure_generic_title
    },
)

/** Preserves safe domain details where no more specific recovery guidance is available. */
@Composable
internal fun failureMessage(failure: CompanionConnectFailureUiState): String = when (failure.kind) {
    CompanionConnectFailureKind.INVALID_QR -> stringResource(Res.string.scan_failure_invalid_message)
    CompanionConnectFailureKind.EXPIRED_QR -> stringResource(Res.string.scan_failure_expired_message)
    CompanionConnectFailureKind.DESKTOP_UNREACHABLE -> stringResource(Res.string.scan_failure_unreachable_message)
    CompanionConnectFailureKind.SECURITY -> stringResource(Res.string.scan_failure_security_message)
    CompanionConnectFailureKind.PAIRING,
    CompanionConnectFailureKind.GENERAL,
    -> failure.failure.message
}
