package com.devuloopers.knet.companion.android.certificate

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes only the public KNet root certificate to Android user-visible storage. */
internal class AndroidDownloadsCertificateExporter(
    private val storage: AndroidCertificateStorage,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    constructor(contentResolver: ContentResolver) : this(MediaStoreAndroidCertificateStorage(contentResolver))

    /** Saves to Downloads on Android 10+, or requests a user-owned destination on Android 8–9. */
    suspend fun export(artifact: CompanionCertificateArtifact): AndroidCertificateExportResult =
        withContext(Dispatchers.IO) {
            if (sdkInt < Build.VERSION_CODES.Q) {
                AndroidCertificateExportResult.DestinationRequired
            } else {
                if (storage.writeToDownloads(artifact.copyBytes())) {
                    AndroidCertificateExportResult.Saved(
                        fileName = AndroidCertificateExportPolicy.FILE_NAME,
                        location = AndroidCertificateExportLocation.DOWNLOADS_KNET,
                    )
                } else {
                    AndroidCertificateExportResult.Failed
                }
            }
        }

    /** Writes to a URI returned by Android's document picker. */
    suspend fun exportToDocument(
        artifact: CompanionCertificateArtifact,
        destination: Uri,
    ): AndroidCertificateExportResult = withContext(Dispatchers.IO) {
        if (storage.writeToDocument(artifact.copyBytes(), destination)) {
            AndroidCertificateExportResult.Saved(
                fileName = AndroidCertificateExportPolicy.FILE_NAME,
                location = AndroidCertificateExportLocation.USER_SELECTED,
            )
        } else {
            AndroidCertificateExportResult.Failed
        }
    }
}
