package com.devuloopers.knet.companion.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.devuloopers.knet.companion.android.certificate.AndroidCertificateExportLocation
import com.devuloopers.knet.companion.android.certificate.AndroidCertificateExportPolicy
import com.devuloopers.knet.companion.android.certificate.AndroidCertificateExportResult
import com.devuloopers.knet.companion.android.certificate.AndroidDownloadsCertificateExporter
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.effect.CompanionEffect
import kotlinx.coroutines.launch

/** Executes native Android effects while keeping intents and platform handles outside common code. */
internal class AndroidCompanionEffectHandler(
    private val activity: ComponentActivity,
    private val onAction: (CompanionAction) -> Unit,
    private val certificateExporter: AndroidDownloadsCertificateExporter =
        AndroidDownloadsCertificateExporter(activity.contentResolver),
) {
    private val qrDecoder = AndroidQrInvitationDecoder(activity.contentResolver)
    private var pendingDocumentExport: CompanionEffect.ExportCertificate? = null
    private val certificateDocument = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidCertificateExportPolicy.MIME_TYPE),
    ) { destination ->
        val pending = pendingDocumentExport ?: return@registerForActivityResult
        pendingDocumentExport = null
        if (destination == null) {
            onAction(CompanionAction.CertificateExportCancelled(pending.desktopId))
        } else {
            activity.lifecycleScope.launch {
                reportCertificateExport(
                    desktopId = pending.desktopId,
                    result = certificateExporter.exportToDocument(pending.artifact, destination),
                )
            }
        }
    }
    private val certificateSettings = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onAction(CompanionAction.VerifyCertificateTrustRequested)
    }
    private val vpnConsent = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        onAction(CompanionAction.VpnConsentResolved(result.resultCode == Activity.RESULT_OK))
    }
    private val qrImagePicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            activity.lifecycleScope.launch {
                val payload = qrDecoder.decode(uri).orEmpty()
                onAction(CompanionAction.InvitationSubmitted(payload))
            }
        }
    }

    /** Handles one ViewModel effect through a product-owned Android API. */
    fun handle(effect: CompanionEffect) {
        when (effect) {
            CompanionEffect.RequestInvitationImageImport -> qrImagePicker.launch("image/*")
            CompanionEffect.RequestVpnConsent -> requestVpnConsent()
            is CompanionEffect.ExportCertificate -> exportCertificate(effect)
            CompanionEffect.OpenCertificateTrustSettings -> openCertificateSettings()
        }
    }

    private fun exportCertificate(effect: CompanionEffect.ExportCertificate) {
        activity.lifecycleScope.launch {
            when (val result = certificateExporter.export(effect.artifact)) {
                AndroidCertificateExportResult.DestinationRequired -> {
                    if (pendingDocumentExport != null) {
                        onAction(CompanionAction.CertificateExportFailed(effect.desktopId))
                    } else {
                        pendingDocumentExport = effect
                        certificateDocument.launch(AndroidCertificateExportPolicy.FILE_NAME)
                    }
                }
                else -> reportCertificateExport(effect.desktopId, result)
            }
        }
    }

    private fun reportCertificateExport(
        desktopId: CompanionDesktopId,
        result: AndroidCertificateExportResult,
    ) {
        when (result) {
            is AndroidCertificateExportResult.Saved -> {
                val locationDescription = when (result.location) {
                    AndroidCertificateExportLocation.DOWNLOADS_KNET ->
                        activity.getString(R.string.certificate_location_downloads_knet)
                    AndroidCertificateExportLocation.USER_SELECTED ->
                        activity.getString(R.string.certificate_location_selected_folder)
                }
                onAction(
                    CompanionAction.CertificateExportCompleted(
                        desktopId = desktopId,
                        fileName = result.fileName,
                        locationDescription = locationDescription,
                    ),
                )
            }
            AndroidCertificateExportResult.DestinationRequired ->
                onAction(CompanionAction.CertificateExportFailed(desktopId))
            AndroidCertificateExportResult.Failed ->
                onAction(CompanionAction.CertificateExportFailed(desktopId))
        }
    }

    private fun requestVpnConsent() {
        val consentIntent = VpnService.prepare(activity)
        if (consentIntent == null) {
            onAction(CompanionAction.VpnConsentResolved(granted = true))
        } else {
            vpnConsent.launch(consentIntent)
        }
    }

    private fun openCertificateSettings() {
        val securitySettings = Intent(Settings.ACTION_SECURITY_SETTINGS)
        runCatching { certificateSettings.launch(securitySettings) }
            .onFailure { certificateSettings.launch(Intent(Settings.ACTION_SETTINGS)) }
    }
}
