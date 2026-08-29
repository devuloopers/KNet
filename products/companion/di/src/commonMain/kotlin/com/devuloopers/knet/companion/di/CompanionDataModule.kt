package com.devuloopers.knet.companion.di

import com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionStateRepository
import com.devuloopers.knet.companion.data.control.DefaultCompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.data.control.DefaultCompanionPairingClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** Composes portable persistence facades and authenticated control clients. */
internal fun companionDataModule(): Module = module {
    single { VersionedCompanionStateRepository(get()) }
    single<CompanionRegistrationRepository> { get<VersionedCompanionStateRepository>() }
    single<CompanionCertificateEnrollmentRepository> { get<VersionedCompanionStateRepository>() }
    single<CompanionCredentialStore> { ProtectedCompanionCredentialStore(get()) }
    single<CompanionInvitationCodec> { VersionedCompanionInvitationCodec() }
    single<CompanionPairingClient> { DefaultCompanionPairingClient(get(), get()) }
    single<CompanionEndpointReconciliationClient> { DefaultCompanionEndpointReconciliationClient(get()) }
}
