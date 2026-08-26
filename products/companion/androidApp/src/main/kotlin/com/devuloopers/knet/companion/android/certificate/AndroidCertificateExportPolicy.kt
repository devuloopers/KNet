package com.devuloopers.knet.companion.android.certificate

import android.os.Environment

/** Stable Android file-system policy for the public KNet root certificate. */
internal object AndroidCertificateExportPolicy {
    const val FILE_NAME: String = "KNet-Root-CA.crt"
    const val MIME_TYPE: String = "application/x-x509-ca-cert"
    val downloadsDirectory: String = "${Environment.DIRECTORY_DOWNLOADS}/KNet"
}
