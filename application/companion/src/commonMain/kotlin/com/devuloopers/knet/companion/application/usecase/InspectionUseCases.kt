package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** Determines whether capture may start and whether HTTPS bodies can be inspected. */
public class PrepareCompanionInspectionUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val verifyCertificateTrust: VerifyCompanionCertificateTrustUseCase,
    private val inspection: CompanionInspectionController,
) {
    public suspend fun execute(): PrepareCompanionInspectionResult {
        val registration = registrations.activeRegistration.value
            ?: return PrepareCompanionInspectionResult.Rejected(registrationMissing())
        val certificateState = verifyCertificateTrust.execute(registration.desktopId)
        return when (val preparation = inspection.prepare()) {
            CompanionInspectionPreparationResult.Ready -> PrepareCompanionInspectionResult.Ready(
                fullHttpsInspection = certificateState is CompanionCertificateState.Trusted,
            )
            CompanionInspectionPreparationResult.ConsentRequired -> PrepareCompanionInspectionResult.VpnConsentRequired(
                fullHttpsInspection = certificateState is CompanionCertificateState.Trusted,
            )
            is CompanionInspectionPreparationResult.Failed -> PrepareCompanionInspectionResult.Rejected(preparation.failure)
        }
    }
}

/** Preparation result deliberately allows limited HTTP/metadata inspection without CA trust. */
public sealed interface PrepareCompanionInspectionResult {
    public data class Ready(public val fullHttpsInspection: Boolean) : PrepareCompanionInspectionResult
    public data class VpnConsentRequired(public val fullHttpsInspection: Boolean) : PrepareCompanionInspectionResult
    public data class Rejected(public val failure: CompanionFailure) : PrepareCompanionInspectionResult
}

/** Starts capture only after registration, network, transport, and native permission are ready. */
public class StartCompanionInspectionUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val connect: ConnectCompanionUseCase,
    private val verifyCertificateTrust: VerifyCompanionCertificateTrustUseCase,
    private val inspection: CompanionInspectionController,
    private val transport: CompanionTransport,
) {
    public suspend fun execute(
        mode: CompanionInspectionMode = CompanionInspectionMode.DEVICE_VPN,
        unsupportedTrafficPolicy: UnsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
    ): StartCompanionInspectionResult {
        val registration = registrations.activeRegistration.value
            ?: return StartCompanionInspectionResult.Rejected(registrationMissing())
        return when (val preparation = inspection.prepare()) {
            CompanionInspectionPreparationResult.ConsentRequired -> StartCompanionInspectionResult.VpnConsentRequired
            is CompanionInspectionPreparationResult.Failed -> StartCompanionInspectionResult.Rejected(preparation.failure)
            CompanionInspectionPreparationResult.Ready -> {
                when (val connection = connect.execute()) {
                    ConnectCompanionResult.Connected -> Unit
                    is ConnectCompanionResult.Rejected -> {
                        cleanupFailedStart()
                        return StartCompanionInspectionResult.Rejected(connection.failure)
                    }
                }
                val certificateState = try {
                    verifyCertificateTrust.execute(registration.desktopId)
                } catch (cancelled: CancellationException) {
                    cleanupFailedStart()
                    throw cancelled
                } catch (_: Throwable) {
                    cleanupFailedStart()
                    return StartCompanionInspectionResult.Rejected(
                        CompanionFailure(
                            CompanionFailureCode.CERTIFICATE_UNAVAILABLE,
                            "Unable to verify the KNet certificate trust state.",
                            true,
                        ),
                    )
                }
                val configuration = CompanionInspectionConfiguration(
                    registration = registration,
                    mode = mode,
                    unsupportedTrafficPolicy = unsupportedTrafficPolicy,
                    fullHttpsInspection = certificateState is CompanionCertificateState.Trusted,
                )
                val started = try {
                    inspection.start(configuration)
                } catch (cancelled: CancellationException) {
                    cleanupFailedStart()
                    throw cancelled
                } catch (_: Throwable) {
                    cleanupFailedStart()
                    return StartCompanionInspectionResult.Rejected(
                        CompanionFailure(
                            CompanionFailureCode.VPN_START_FAILED,
                            "Unable to start device inspection.",
                            true,
                        ),
                    )
                }
                when (started) {
                    CompanionInspectionStartResult.Started -> StartCompanionInspectionResult.Started(
                        fullHttpsInspection = configuration.fullHttpsInspection,
                    )
                    is CompanionInspectionStartResult.Failed -> {
                        cleanupFailedStart()
                        StartCompanionInspectionResult.Rejected(started.failure)
                    }
                }
            }
        }
    }

    private suspend fun cleanupFailedStart() {
        try {
            inspection.stop()
        } finally {
            transport.disconnect()
        }
    }
}

/** Capture start outcome. */
public sealed interface StartCompanionInspectionResult {
    public data class Started(public val fullHttpsInspection: Boolean) : StartCompanionInspectionResult
    public data object VpnConsentRequired : StartCompanionInspectionResult
    public data class Rejected(public val failure: CompanionFailure) : StartCompanionInspectionResult
}

/** Stops capture first, then disconnects transport; pairing remains durable. */
public class StopCompanionInspectionUseCase(
    private val inspection: CompanionInspectionController,
    private val transport: CompanionTransport,
) {
    public suspend fun execute() {
        try {
            inspection.stop()
        } finally {
            transport.disconnect()
        }
    }
}

/** Provides capture state without exposing VpnService or Network Extension. */
public class ObserveCompanionInspectionUseCase(
    inspection: CompanionInspectionController,
) {
    public val state: StateFlow<CompanionInspectionState> = inspection.state
}
