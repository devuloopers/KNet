package com.devuloopers.knet.products.desktop.di.certificate

import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.data.desktop.certificate.DesktopCertificateManagementAdapter
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import java.io.File
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Shared desktop CA runtime, certificate-management port, and certificate presentation. */
internal val certificateBindings: Module = module {
    single {
        CertificateRuntimeRepository(File(System.getProperty("user.home"), ".knet"))
    }
    single<CertificateManager> {
        val certificates: CertificateRuntimeRepository = get()
        certificates.createCertificateManager(File(System.getProperty("user.home"), ".knet/certificates"))
    }
    single<CertificateManagementPort> { DesktopCertificateManagementAdapter(get(), get<CertificateRuntimeRepository>()) }
    viewModel { CertificateViewModel(get<CertificateManagementPort>()) }
}
