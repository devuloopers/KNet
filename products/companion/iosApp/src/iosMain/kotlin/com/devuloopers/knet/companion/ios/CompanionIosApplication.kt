package com.devuloopers.knet.companion.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.devuloopers.knet.companion.ios.generated.resources.Res
import com.devuloopers.knet.companion.ios.generated.resources.bootstrap_failed
import com.devuloopers.knet.companion.ios.generated.resources.bootstrap_loading
import com.devuloopers.knet.companion.ios.generated.resources.certificate_guidance_confirm
import com.devuloopers.knet.companion.ios.generated.resources.certificate_guidance_install
import com.devuloopers.knet.companion.ios.generated.resources.certificate_guidance_open
import com.devuloopers.knet.companion.ios.generated.resources.certificate_guidance_return
import com.devuloopers.knet.companion.ios.bootstrap.IosCompanionBootstrap
import com.devuloopers.knet.companion.ios.di.CompanionIosModules
import com.devuloopers.knet.companion.ios.platform.IosCompanionEffectHandler
import com.devuloopers.knet.companion.ios.platform.scanner.IosCompanionInvitationScanner
import com.devuloopers.knet.companion.ios.runtime.IosCompanionBootstrapState
import com.devuloopers.knet.companion.ios.runtime.IosCompanionRuntime
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.sharedui.KNetCompanionApp
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

/** Swift-facing owner of the iOS companion composition and its native resource lifetime. */
class CompanionIosApplication {
    private val runtime = IosCompanionRuntime()
    private var rootController: UIViewController? = null

    fun rootViewController(): UIViewController = ComposeUIViewController {
        IosCompanionProductRoot(runtime, presenter = { rootController })
    }.also { controller -> rootController = controller }
}

@Composable
private fun IosCompanionProductRoot(runtime: IosCompanionRuntime, presenter: () -> UIViewController?) {
    val bootstrapState by runtime.bootstrap.collectAsState()
    DisposableEffect(runtime) {
        onDispose(runtime::close)
    }

    when (val current = bootstrapState) {
        IosCompanionBootstrapState.Loading -> BootstrapSurface(failed = false)
        IosCompanionBootstrapState.Failed -> BootstrapSurface(failed = true)
        is IosCompanionBootstrapState.Ready -> ReadyCompanionProduct(current.value, presenter)
    }
}

@Composable
private fun ReadyCompanionProduct(bootstrap: IosCompanionBootstrap, presenter: () -> UIViewController?) {
    val configuration = remember(bootstrap) {
        koinConfiguration {
            allowOverride(false)
            modules(CompanionIosModules.create(bootstrap))
        }
    }
    KoinApplication(configuration = configuration) {
        val viewModel: CompanionViewModel = koinViewModel()
        val effectHandler = remember(viewModel) {
            IosCompanionEffectHandler(viewModel::dispatch, presenter)
        }
        val invitationScanner = remember { IosCompanionInvitationScanner() }
        DisposableEffect(invitationScanner) {
            onDispose(invitationScanner::close)
        }
        val state by viewModel.state.collectAsState()
        LaunchedEffect(viewModel, effectHandler) {
            viewModel.effects.collect(effectHandler::handle)
        }
        KNetCompanionApp(
            state = state,
            onAction = viewModel::dispatch,
            onExitRequested = {},
            certificateInstallationGuidance = iosCertificateGuidance(),
            invitationScanner = invitationScanner,
        )
    }
}

@Composable
private fun BootstrapSurface(failed: Boolean) {
    KNetTheme(themeMode = ThemeMode.System) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Text(
                        text = stringResource(Res.string.bootstrap_failed),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(Res.string.bootstrap_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun iosCertificateGuidance(): CertificateInstallationGuidance = CertificateInstallationGuidance(
    steps = listOf(
        stringResource(Res.string.certificate_guidance_open),
        stringResource(Res.string.certificate_guidance_install),
        stringResource(Res.string.certificate_guidance_confirm),
        stringResource(Res.string.certificate_guidance_return),
    )
)
