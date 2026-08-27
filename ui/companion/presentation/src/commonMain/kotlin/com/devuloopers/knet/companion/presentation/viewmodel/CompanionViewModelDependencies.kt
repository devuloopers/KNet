package com.devuloopers.knet.companion.presentation.viewmodel

import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateStoreChangesUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionDiscoveryUseCase
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase

/**
 * Presentation-owned dependency bundle that keeps ViewModel construction cohesive without relocating use cases.
 *
 * Every contained operation is implemented by `:application:companion`; this type only describes the subset
 * consumed by [CompanionViewModel].
 *
 * @property acceptInvitation validates one secret-bearing invitation in memory.
 * @property pair completes pairing and commits the resulting registration safely.
 * @property observeRegistrations observes durable registrations and active selection.
 * @property selectRegistration changes the active desktop.
 * @property observeConnection observes transport lifecycle state.
 * @property observeNetwork observes native network reachability.
 * @property observeDiscovery observes native DNS-SD discovery lifecycle without platform handles.
 * @property maintainEndpoint securely follows the active desktop across LAN address changes during inspection.
 * @property startInspection prepares connection, trust, and native inspection.
 * @property stopInspection releases inspection before transport.
 * @property observeInspection observes native inspection lifecycle state.
 * @property downloadCertificate retrieves the authenticated public KNet root.
 * @property verifyCertificateTrust performs the authoritative platform TLS challenge.
 * @property observeCertificateStoreChanges emits hints that trigger another trust check.
 * @property refreshCredential rotates the active paired credential.
 * @property forgetDesktop removes registration, credential, transport, and inspection state.
 */
public data class CompanionViewModelDependencies(
    public val acceptInvitation: AcceptPairingInvitationUseCase,
    public val pair: PairCompanionDeviceUseCase,
    public val observeRegistrations: ObserveCompanionRegistrationsUseCase,
    public val selectRegistration: SelectCompanionRegistrationUseCase,
    public val observeConnection: ObserveCompanionConnectionUseCase,
    public val observeNetwork: ObserveCompanionNetworkUseCase,
    public val observeDiscovery: ObserveCompanionDiscoveryUseCase,
    public val maintainEndpoint: MaintainCompanionEndpointUseCase,
    public val startInspection: StartCompanionInspectionUseCase,
    public val stopInspection: StopCompanionInspectionUseCase,
    public val observeInspection: ObserveCompanionInspectionUseCase,
    public val downloadCertificate: DownloadCompanionRootCertificateUseCase,
    public val verifyCertificateTrust: VerifyCompanionCertificateTrustUseCase,
    public val observeCertificateStoreChanges: ObserveCompanionCertificateStoreChangesUseCase,
    public val refreshCredential: RefreshCompanionCredentialUseCase,
    public val forgetDesktop: ForgetCompanionDesktopUseCase,
)
