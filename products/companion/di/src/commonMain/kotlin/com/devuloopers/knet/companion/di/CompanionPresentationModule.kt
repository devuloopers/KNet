package com.devuloopers.knet.companion.di

import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Composes lifecycle-aware portable companion presentation dependencies. */
internal fun companionPresentationModule(): Module = module {
    single {
        CompanionViewModelDependencies(
            acceptInvitation = get(),
            pair = get(),
            observeRegistrations = get(),
            observeCertificateEnrollments = get(),
            selectRegistration = get(),
            observeConnection = get(),
            observeNetwork = get(),
            observeDiscovery = get(),
            maintainEndpoint = get(),
            monitorDesktopAvailability = get(),
            startInspection = get(),
            stopInspection = get(),
            observeInspection = get(),
            downloadCertificate = get(),
            verifyCertificateTrust = get(),
            completeCertificateEnrollment = get(),
            observeCertificateStoreChanges = get(),
            refreshCredential = get(),
            forgetDesktop = get(),
        )
    }
    viewModel { CompanionViewModel(dependencies = get()) }
}
