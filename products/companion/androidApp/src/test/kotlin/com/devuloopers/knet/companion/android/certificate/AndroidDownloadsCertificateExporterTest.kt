package com.devuloopers.knet.companion.android.certificate

import android.net.Uri
import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AndroidDownloadsCertificateExporterTest {
    @Test
    fun androidTenAndLaterWritesThePublicRootToStableDownloadsLocation() = runTest {
        val storage = FakeCertificateStorage(downloadResult = true)
        val exporter = AndroidDownloadsCertificateExporter(storage = storage, sdkInt = 29)

        val result = exporter.export(CompanionCertificateArtifact(byteArrayOf(1, 2, 3), "remote-name.crt"))

        val saved = assertIs<AndroidCertificateExportResult.Saved>(result)
        assertEquals(AndroidCertificateExportPolicy.FILE_NAME, saved.fileName)
        assertEquals(AndroidCertificateExportLocation.DOWNLOADS_KNET, saved.location)
        assertContentEquals(byteArrayOf(1, 2, 3), storage.downloadedBytes)
        assertEquals(1, storage.downloadWrites)
    }

    @Test
    fun androidEightAndNineRequireAUserSelectedDocumentWithoutWritingEarly() = runTest {
        val storage = FakeCertificateStorage(downloadResult = true)
        val exporter = AndroidDownloadsCertificateExporter(storage = storage, sdkInt = 28)

        val result = exporter.export(CompanionCertificateArtifact(byteArrayOf(1), "knet.crt"))

        assertIs<AndroidCertificateExportResult.DestinationRequired>(result)
        assertEquals(0, storage.downloadWrites)
    }

    @Test
    fun failedDownloadsWriteReturnsATypedFailure() = runTest {
        val exporter = AndroidDownloadsCertificateExporter(
            storage = FakeCertificateStorage(downloadResult = false),
            sdkInt = 29,
        )

        val result = exporter.export(CompanionCertificateArtifact(byteArrayOf(1), "knet.crt"))

        assertIs<AndroidCertificateExportResult.Failed>(result)
    }

    private class FakeCertificateStorage(
        private val downloadResult: Boolean,
    ) : AndroidCertificateStorage {
        var downloadWrites: Int = 0
        var downloadedBytes: ByteArray = ByteArray(0)

        override fun writeToDownloads(bytes: ByteArray): Boolean {
            downloadWrites += 1
            downloadedBytes = bytes.copyOf()
            return downloadResult
        }

        override fun writeToDocument(bytes: ByteArray, destination: Uri): Boolean = false
    }
}
