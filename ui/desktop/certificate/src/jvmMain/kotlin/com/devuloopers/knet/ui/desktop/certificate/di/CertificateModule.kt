package com.devuloopers.knet.ui.desktop.certificate.di

import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:certificate`.
 *
 * The [CertificateManager] singleton is deliberately NOT registered here. It is registered in
 * `DesktopDataModule.runtime` (`:data:desktop`) where it can be wired to the persisted
 * [CertificateRuntimeRepository] CA keypair. This ensures the Root CA used for HTTPS interception
 * and the one presented in the Certificate Manager UI are always the same instance, so the user
 * installs the Root CA certificate into the OS trust store exactly once.
 */
public val certificateUiModule = module {
    viewModel { CertificateViewModel(get<CertificateManager>()) }
}
