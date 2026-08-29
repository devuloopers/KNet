package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.IosCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionProxyProtocol
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionTransportKind
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authenticated iOS LAN carrier. Secrets remain in memory and are passed to the packet-tunnel extension only as
 * ephemeral start options; they are never persisted in Network Extension preferences.
 */
public class IosCompanionProxyTransport(
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CompanionTransport {
    private val lifecycleLock: Mutex = Mutex()
    private val httpClient: KtorCompanionHttpClient = KtorCompanionHttpClient(IosCompanionKtorClientProvider())
    private var activeSession: IosCompanionProxySession? = null
    private val mutableState: MutableStateFlow<CompanionConnectionState> =
        MutableStateFlow(CompanionConnectionState.Disconnected)

    override val state: StateFlow<CompanionConnectionState> = mutableState.asStateFlow()

    override suspend fun connect(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionTransportResult = lifecycleLock.withLock {
        if (!SAFE_CREDENTIAL.matches(credential)) return@withLock reject(invalidCredentialFailure())
        if (activeSession?.matches(registration, credential) == true &&
            mutableState.value is CompanionConnectionState.Connected
        ) {
            return@withLock CompanionTransportResult.Connected
        }

        activeSession = null
        mutableState.value = CompanionConnectionState.Connecting(registration.desktopId, attempt = 1)
        val response = try {
            httpClient.execute(
                CompanionHttpRequest(
                    endpoint = registration.proxyEndpoint,
                    method = CompanionHttpMethod.GET,
                    path = CompanionProxyProtocol.READINESS_PATH,
                    additionalHeaders = mapOf(
                        PROXY_AUTHORIZATION_HEADER to bearerValue(registration, credential),
                    ),
                    maximumResponseBytes = 0,
                    security = CompanionHttpSecurity.PinnedRoot(
                        registration.rootCertificate,
                        registration.rootCertificateSha256,
                        registration.transportIdentitySha256,
                    ),
                ),
            )
        } catch (cancelled: CancellationException) {
            mutableState.value = CompanionConnectionState.Disconnected
            throw cancelled
        } catch (_: CompanionHttpSecurityException) {
            return@withLock reject(identityFailure())
        } catch (_: Throwable) {
            return@withLock reject(unavailableFailure())
        }

        when (response.statusCode) {
            204 -> Unit
            401, 403 -> return@withLock reject(authenticationFailure())
            503 -> return@withLock reject(proxyStoppedFailure())
            else -> return@withLock reject(unavailableFailure())
        }

        activeSession = IosCompanionProxySession(registration, credential)
        mutableState.value = CompanionConnectionState.Connected(
            desktopId = registration.desktopId,
            transport = CompanionTransportKind.DIRECT_LAN,
            connectedAtEpochMillis = nowEpochMillis(),
        )
        CompanionTransportResult.Connected
    }

    override suspend fun disconnect(): Unit = lifecycleLock.withLock {
        activeSession = null
        mutableState.value = CompanionConnectionState.Disconnected
    }

    internal fun sessionSnapshot(): IosCompanionProxySession? = activeSession?.copy()

    private fun reject(failure: CompanionFailure): CompanionTransportResult.Rejected {
        activeSession = null
        mutableState.value = CompanionConnectionState.Failed(failure)
        return CompanionTransportResult.Rejected(failure)
    }

    private companion object {
        private const val PROXY_AUTHORIZATION_HEADER: String = "Proxy-Authorization"
        private val SAFE_CREDENTIAL: Regex = Regex("[A-Za-z0-9._~-]{1,512}")
    }
}

internal class IosCompanionProxySession(
    val registration: CompanionRegistration,
    private val credential: String,
) {
    fun matches(otherRegistration: CompanionRegistration, otherCredential: String): Boolean =
        registration == otherRegistration && credential == otherCredential

    fun authorizationValue(): String = bearerValue(registration, credential)

    fun copy(): IosCompanionProxySession = IosCompanionProxySession(registration, credential)
}

private fun bearerValue(registration: CompanionRegistration, credential: String): String =
    "Bearer ${registration.deviceId.value}:$credential"

private fun invalidCredentialFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.CREDENTIAL_NOT_FOUND,
    "Paired credential is invalid.",
    recoverable = false,
)

private fun authenticationFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.CREDENTIAL_EXPIRED,
    "The paired desktop rejected this companion credential. Refresh or pair the device again.",
    recoverable = true,
)

private fun identityFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
    "The paired desktop TLS identity could not be verified.",
    recoverable = false,
)

private fun unavailableFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Unable to reach the paired desktop securely.",
    recoverable = true,
)

private fun proxyStoppedFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Start the KNet desktop proxy before starting inspection.",
    recoverable = true,
)
