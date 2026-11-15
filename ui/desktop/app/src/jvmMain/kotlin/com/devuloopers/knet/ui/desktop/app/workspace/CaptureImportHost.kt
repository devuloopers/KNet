package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.importcapture.CaptureImportActionDraft
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import com.devuloopers.knet.ui.desktop.traffic.importcapture.CaptureImportDialogViewModel
import com.devuloopers.knet.ui.desktop.traffic.importcapture.CaptureImportDropHost
import org.koin.compose.viewmodel.koinViewModel

/** Shell-owned import routing; files prepare editor drafts and never enter the captured Traffic workspace. */
@Composable
fun CaptureImportHost(
    viewModel: CaptureImportDialogViewModel,
    workspace: PolicyWorkspace,
    workspaceName: String,
    onNavigate: (DesktopDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val studio: ApiStudioViewModel = koinViewModel()
    val intercepts: BreakpointManagerViewModel = koinViewModel()
    val conditions: NetworkConditionsViewModel = koinViewModel()
    val protection: ProtectedTrafficViewModel = koinViewModel()
    val route: (CaptureImportActionDraft) -> Unit = remember(studio, intercepts, conditions, protection, onNavigate) {
        { draft ->
            when (draft) {
                is CaptureImportActionDraft.ApiStudio -> { studio.importRequestSpec(draft.request); onNavigate(DesktopDestination.ApiStudio) }
                is CaptureImportActionDraft.Intercept -> { intercepts.openImportedDraft(draft); onNavigate(DesktopDestination.Breakpoints) }
                is CaptureImportActionDraft.NetworkConditions -> { conditions.openImportedDraft(draft); onNavigate(DesktopDestination.NetworkConditions) }
                is CaptureImportActionDraft.ProtectedTraffic -> { protection.openImportedDraft(draft); onNavigate(DesktopDestination.ProtectedTraffic) }
            }
        }
    }
    CaptureImportDropHost(viewModel, workspace, workspaceName, route, modifier, content)
}
