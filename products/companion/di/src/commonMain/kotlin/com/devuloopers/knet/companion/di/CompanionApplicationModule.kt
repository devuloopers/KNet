package com.devuloopers.knet.companion.di

import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.CompanionDesktopAvailabilityMonitor
import com.devuloopers.knet.companion.application.usecase.CompleteCompanionCertificateEnrollmentUseCase
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DisconnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.MonitorCompanionDesktopAvailabilityUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateEnrollmentsUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateStoreChangesUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionDiscoveryUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RecoverCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/** Composes the portable companion workflows from application contracts. */
internal fun companionApplicationModule(): Module = module {
    single { AcceptPairingInvitationUseCase(get(), get(), ::currentEpochMillis) }
    single { PairCompanionDeviceUseCase(get(), get(), get(), get(), get(), ::currentEpochMillis) }
    single { ObserveCompanionRegistrationsUseCase(get()) }
    single { ObserveCompanionCertificateEnrollmentsUseCase(get()) }
    single { SelectCompanionRegistrationUseCase(get()) }
    single { RecoverCompanionEndpointUseCase(get(), get(), get(), get()) }
    single<CompanionEndpointResolver> { get<RecoverCompanionEndpointUseCase>() }
    single<CompanionDesktopAvailabilityMonitor> {
        MonitorCompanionDesktopAvailabilityUseCase(get(), get(), get(), ::currentEpochMillis)
    }
    single { MaintainCompanionEndpointUseCase(get(), get(), get(), get(), get()) }
    single { ConnectCompanionUseCase(get(), get(), get(), get(), ::currentEpochMillis, get()) }
    single { DisconnectCompanionUseCase(get()) }
    single { ObserveCompanionConnectionUseCase(get()) }
    single { ObserveCompanionNetworkUseCase(get()) }
    single { ObserveCompanionDiscoveryUseCase(get()) }
    single { DownloadCompanionRootCertificateUseCase(get(), get(), get(), ::currentEpochMillis) }
    single { VerifyCompanionCertificateTrustUseCase(get(), get(), get(), get(), ::currentEpochMillis) }
    single { CompleteCompanionCertificateEnrollmentUseCase(get(), get(), get(), ::currentEpochMillis) }
    single { ObserveCompanionCertificateStoreChangesUseCase(get()) }
    single { StartCompanionInspectionUseCase(get(), get(), get(), get(), get()) }
    single { StopCompanionInspectionUseCase(get(), get()) }
    single { ObserveCompanionInspectionUseCase(get()) }
    single { RefreshCompanionCredentialUseCase(get(), get(), get(), ::currentEpochMillis) }
    single { ForgetCompanionDesktopUseCase(get(), get(), get(), get(), get()) }
}

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
