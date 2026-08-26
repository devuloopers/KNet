package com.devuloopers.knet.companion.android.certificate

/** Result of one Android-owned public certificate export operation. */
internal sealed interface AndroidCertificateExportResult {
    /** The certificate is available to the user at [location]. */
    data class Saved(
        val fileName: String,
        val location: AndroidCertificateExportLocation,
    ) : AndroidCertificateExportResult

    /** Android 8 or 9 requires the user to choose a document destination. */
    data object DestinationRequired : AndroidCertificateExportResult

    /** The certificate could not be written. */
    data object Failed : AndroidCertificateExportResult
}

/** Closed user-visible destinations rendered by Android resources at the product boundary. */
internal enum class AndroidCertificateExportLocation {
    DOWNLOADS_KNET,
    USER_SELECTED,
}
