package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Three compact, consistently aligned rows mirror authoritative trust, transport and network state. */
@Composable
internal fun CompanionReadinessSummary(state: CompanionHomeUiState) {
    CompanionPanel {
        CompanionStatusRow(stringResource(Res.string.home_certificate_label), stringResource(when (state.certificateStatus) {
            CompanionHomeCertificateStatus.VERIFIED -> Res.string.home_certificate_verified
            CompanionHomeCertificateStatus.PREVIOUSLY_VERIFIED -> Res.string.home_certificate_previously_verified
            CompanionHomeCertificateStatus.CHECKING -> Res.string.home_certificate_checking
            CompanionHomeCertificateStatus.VERIFICATION_PENDING -> Res.string.home_certificate_pending
            CompanionHomeCertificateStatus.NEEDS_ATTENTION -> Res.string.home_certificate_attention
        }), if (state.certificateStatus == CompanionHomeCertificateStatus.VERIFIED)
            KNetTheme.colors.semantic.success else KNetTheme.colors.textSecondary)
        HorizontalDivider(color = KNetTheme.colors.border)
        CompanionStatusRow(stringResource(Res.string.home_inspection_label), stringResource(when (state.inspectionControl) {
            CompanionHomeInspectionControl.Stop -> Res.string.home_tunnel_active
            CompanionHomeInspectionControl.Starting -> Res.string.control_starting
            CompanionHomeInspectionControl.Stopping -> Res.string.control_stopping
            CompanionHomeInspectionControl.ContinueVpnSetup -> Res.string.home_permission_pending
            is CompanionHomeInspectionControl.Retry -> Res.string.home_tunnel_failed
            is CompanionHomeInspectionControl.Start -> Res.string.home_inspection_not_running
        }), if (state.inspectionControl == CompanionHomeInspectionControl.Stop) KNetTheme.colors.semantic.success else KNetTheme.colors.textSecondary)
        HorizontalDivider(color = KNetTheme.colors.border)
        CompanionStatusRow(stringResource(Res.string.home_connection_label), stringResource(when {
            state.inspectionControl == CompanionHomeInspectionControl.Starting -> Res.string.home_tunnel_connecting
            state.inspectionControl == CompanionHomeInspectionControl.Stopping -> Res.string.home_connection_disconnecting
            state.networkPath == CompanionHomeNetworkPath.DIRECT_LAN -> Res.string.home_network_direct_lan
            state.networkPath == CompanionHomeNetworkPath.RELAY -> Res.string.home_network_relay
            else -> Res.string.home_network_waiting

        }))
    }
}
