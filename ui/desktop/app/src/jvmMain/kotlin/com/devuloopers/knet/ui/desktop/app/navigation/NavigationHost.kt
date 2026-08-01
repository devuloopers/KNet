package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.apistudio.view.ApiStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.certificate.view.CertificateManagerScreen
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import com.devuloopers.knet.ui.desktop.inspector.view.InspectorPanel
import com.devuloopers.knet.ui.desktop.inspector.viewmodel.InspectorViewModel
import com.devuloopers.knet.ui.desktop.scripting.view.ScriptingScreen
import com.devuloopers.knet.ui.desktop.scripting.viewmodel.ScriptingViewModel
import com.devuloopers.knet.ui.desktop.traffic.view.TrafficScreen
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import com.devuloopers.knet.ui.desktop.workspace.layout.WorkspaceLayout
import com.devuloopers.knet.ui.desktop.workspace.viewmodel.WorkspaceViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Composable host routing navigation selections explicitly to their feature module screens.
 *
 * @param destination Currently active target screen.
 * @param modifier Layout modifier.
 */
@Composable
public fun NavigationHost(
    destination: DesktopDestination,
    modifier: Modifier = Modifier
) {
    when (destination) {
        DesktopDestination.Workspace -> {
            val viewModel: WorkspaceViewModel = koinViewModel()
            WorkspaceLayout(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.Traffic -> {
            val viewModel: TrafficViewModel = koinViewModel()
            TrafficScreen(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.Inspector -> {
            val viewModel: InspectorViewModel = koinViewModel()
            InspectorPanel(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.ApiStudio -> {
            val viewModel: ApiStudioViewModel = koinViewModel()
            ApiStudioScreen(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.Scripting -> {
            val viewModel: ScriptingViewModel = koinViewModel()
            ScriptingScreen(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.Certificate -> {
            val viewModel: CertificateViewModel = koinViewModel()
            CertificateManagerScreen(viewModel = viewModel, modifier = modifier)
        }

        DesktopDestination.Settings -> {
            SettingsPlaceholderScreen()
        }
    }
}
