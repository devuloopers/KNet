package com.devuloopers.knet.companion.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.navigation.CompanionNavHost
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.UnavailableCompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import com.devuloopers.knet.ui.core.foundation.theme.KNetApplicationTheme

/**
 * Root companion UI rendered by every Compose Multiplatform product.
 *
 * @param state immutable state supplied by the product-owned companion ViewModel.
 * @param onAction dispatches portable user intents to that ViewModel.
 * @param onExitRequested lets the native host finish or dismiss its root surface.
 * @param invitationScanner product-owned camera capability used by the shared scanner stage.
 * @param certificateInstallationGuidance product-owned native Settings instructions rendered without native types.
 * @param modifier outer layout modifier.
 */
@Composable
public fun KNetCompanionApp(
    modifier: Modifier = Modifier,
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
    onExitRequested: () -> Unit,
    certificateInstallationGuidance: CertificateInstallationGuidance,
    invitationScanner: CompanionInvitationScanner = UnavailableCompanionInvitationScanner
) {
    KNetApplicationTheme {
        KNetTheme(colors = KNetTheme.colors, typography = KNetTheme.typography.copy(
                display = KNetTheme.typography.display.copy(fontSize = 21.sp, lineHeight = 26.sp),
                heading = KNetTheme.typography.heading.copy(fontSize = 19.sp, lineHeight = 25.sp),
                titleLarge = KNetTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
                titleMedium = KNetTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                bodyMedium = KNetTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                bodySmall = KNetTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelMedium = KNetTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp)),
            shapes = KNetTheme.shapes.copy(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(22.dp)),
            motion = KNetTheme.motion) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                CompanionNavHost(
                    state = state,
                    onAction = onAction,
                    onExitRequested = onExitRequested,
                    invitationScanner = invitationScanner,
                    certificateInstallationGuidance = certificateInstallationGuidance,
                )
            }
        }
    }
}
