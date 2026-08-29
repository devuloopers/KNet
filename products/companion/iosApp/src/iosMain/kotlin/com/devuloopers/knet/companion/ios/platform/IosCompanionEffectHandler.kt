package com.devuloopers.knet.companion.ios.platform

import com.devuloopers.knet.companion.ios.platform.scanner.IosQrImagePicker
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.effect.CompanionEffect
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController

/** Executes iOS effects without allowing UIKit values into shared presentation code. */
internal class IosCompanionEffectHandler(
    private val dispatch: (CompanionAction) -> Unit,
    presenter: () -> UIViewController?,
) {
    private val certificateExporter: IosCertificateArtifactExporter =
        IosCertificateArtifactExporter(dispatch, presenter)
    private val qrImagePicker: IosQrImagePicker =
        IosQrImagePicker(dispatch, presenter)

    fun handle(effect: CompanionEffect) {
        when (effect) {
            CompanionEffect.RequestVpnConsent -> {
                // iOS has no separate VPN-consent intent. Retrying the shared start flow invokes
                // NETunnelProviderManager.saveToPreferences(), which owns the native system prompt.
                dispatch(CompanionAction.VpnConsentResolved(granted = true))
            }
            is CompanionEffect.ExportCertificate -> {
                certificateExporter.export(effect.desktopId, effect.artifact)
            }
            CompanionEffect.OpenCertificateTrustSettings -> openApplicationSettings()
            CompanionEffect.PickInvitationImage -> qrImagePicker.pickImage()
        }
    }

    private fun openApplicationSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(
            url = settingsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
