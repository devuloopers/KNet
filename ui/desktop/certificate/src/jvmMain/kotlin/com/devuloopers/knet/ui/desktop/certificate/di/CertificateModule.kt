package com.devuloopers.knet.ui.desktop.certificate.di

import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.CertificateManagerImpl
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:certificate`.
 */
public val certificateUiModule = module {
    single<CertificateManager> { CertificateManagerImpl() }
    viewModel { CertificateViewModel(get()) }
}
