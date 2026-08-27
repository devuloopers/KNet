package com.devuloopers.knet.companion.android.certificate

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi

/** Narrow Android storage boundary used by the certificate export coordinator. */
internal interface AndroidCertificateStorage {
    /** Writes [bytes] to the stable KNet file in Downloads. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun writeToDownloads(bytes: ByteArray): Boolean

    /** Writes [bytes] to one user-selected document destination. */
    fun writeToDocument(bytes: ByteArray, destination: Uri): Boolean
}
