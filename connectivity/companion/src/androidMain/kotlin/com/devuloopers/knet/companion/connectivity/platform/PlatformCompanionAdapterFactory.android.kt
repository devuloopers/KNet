package com.devuloopers.knet.companion.connectivity.platform

import android.content.Context
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.connectivity.bootstrap.DefaultCompanionInvitationResolver
import com.devuloopers.knet.companion.connectivity.bootstrap.KtorCompanionBootstrapClient
import com.devuloopers.knet.companion.connectivity.certificate.AndroidCertificateStoreChangeObserver
import com.devuloopers.knet.companion.connectivity.certificate.AndroidCompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.connectivity.certificate.AndroidCompanionRootCertificateSource
import com.devuloopers.knet.companion.connectivity.certificate.PlatformAndroidCertificateTlsClient
import com.devuloopers.knet.companion.connectivity.certificate.PlatformAndroidTrustedCertificateStore
import com.devuloopers.knet.companion.connectivity.control.KtorCompanionControlTransport
import com.devuloopers.knet.companion.connectivity.fallback.UnavailableCompanionInspectionController
import com.devuloopers.knet.companion.connectivity.http.AndroidCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.inspection.AndroidCompanionInspectionController
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackend
import com.devuloopers.knet.companion.connectivity.inspection.PlatformAndroidVpnConsent
import com.devuloopers.knet.companion.connectivity.network.AndroidCompanionNetworkObserver

/**
 * Android actual factory whose constructor accepts native dependencies only in `androidMain`.
 *
 * @param context Android owner used only through its process-scoped application context.
 * @param inspectionBackend optional qualified VPN packet backend; absence fails closed.
 * @param nowEpochMillis clock used by certificate and inspection state transitions.
 */
public actual class PlatformCompanionAdapterFactory(
    context: Context,
    private val inspectionBackend: AndroidInspectionBackend? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : CompanionPlatformAdapterFactory {
    private val applicationContext: Context = context.applicationContext

    /** Creates an independently owned Android adapter bundle. */
    actual override fun create(): CompanionPlatformAdapters {
        val networkObserver = AndroidCompanionNetworkObserver(applicationContext)
        val httpClient = KtorCompanionHttpClient(AndroidCompanionKtorClientProvider())
        val certificateClient = PlatformAndroidCertificateTlsClient(httpClient)
        val certificateStoreChanges = AndroidCertificateStoreChangeObserver(applicationContext)
        val inspectionController: CompanionInspectionController = inspectionBackend?.let { backend ->
            AndroidCompanionInspectionController(
                consent = PlatformAndroidVpnConsent(applicationContext),
                backend = backend,
                nowEpochMillis = nowEpochMillis,
            )
        } ?: UnavailableCompanionInspectionController(ANDROID_PLATFORM_NAME)
        return DefaultCompanionPlatformAdapters(
            networkObserver = networkObserver,
            invitationResolver = DefaultCompanionInvitationResolver(KtorCompanionBootstrapClient(httpClient)),
            controlTransport = KtorCompanionControlTransport(httpClient),
            rootCertificateSource = AndroidCompanionRootCertificateSource(certificateClient),
            trustVerifier = AndroidCompanionCertificateTrustVerifier(
                client = certificateClient,
                trustedCertificates = PlatformAndroidTrustedCertificateStore(),
                nowEpochMillis = nowEpochMillis,
            ),
            certificateStoreChanges = certificateStoreChanges,
            inspectionController = inspectionController,
            closePlatform = {
                certificateStoreChanges.close()
                networkObserver.close()
            },
        )
    }

    private companion object {
        private const val ANDROID_PLATFORM_NAME: String = "Android"
    }
}
