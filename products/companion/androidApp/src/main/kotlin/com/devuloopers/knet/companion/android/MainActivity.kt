package com.devuloopers.knet.companion.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.android.scanner.AndroidCompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.KNetCompanionApp
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Android lifecycle host for the shared Compose Multiplatform companion UI. */
class MainActivity : ComponentActivity() {
    private var companionViewModel: CompanionViewModel? = null
    private var invitationScanner: CompanionInvitationScanner? = null

    /** Creates the UI host and product-owned native effect boundary. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val effectHandler = AndroidCompanionEffectHandler(
            activity = this,
            onAction = { action -> companionViewModel?.dispatch(action) },
        )
        val scanner = AndroidCompanionInvitationScanner(this)
        val certificateInstallationGuidance = CertificateInstallationGuidance(
            title = getString(R.string.certificate_guidance_title),
            steps = listOf(
                getString(R.string.certificate_guidance_step_downloads),
                getString(R.string.certificate_guidance_step_install),
                getString(R.string.certificate_guidance_step_select),
                getString(R.string.certificate_guidance_step_confirm),
            ),
        )
        invitationScanner = scanner

        lifecycleScope.launch {
            (application as KNetCompanionApplication).awaitDependencyInjection()

            setContent {
                val viewModel: CompanionViewModel = koinViewModel()
                SideEffect { companionViewModel = viewModel }
                LaunchedEffect(viewModel, effectHandler) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.effects.collect(effectHandler::handle)
                    }
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                KNetCompanionApp(
                    state = state,
                    onAction = viewModel::dispatch,
                    onExitRequested = ::finish,
                    invitationScanner = scanner,
                    certificateInstallationGuidance = certificateInstallationGuidance,
                )
            }
        }
    }

    /** Clears the Activity-local reference after the lifecycle-owned ViewModelStore is destroyed. */
    override fun onDestroy() {
        invitationScanner?.close()
        invitationScanner = null
        companionViewModel = null
        super.onDestroy()
    }
}
