package com.devuloopers.knet.companion.android.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Decodes QR code payloads from user-selected local image URIs using Google ML Kit. */
internal class AndroidQrImageDecoder(
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
) {
    /**
     * Decodes the first non-blank QR code payload from the given image [uri].
     *
     * @param context Application or activity context used to access the image content resolver.
     * @param uri Local content URI representing the selected image.
     * @return [Result] containing the decoded payload string, or a failure if no QR code was found or decoding failed.
     */
    suspend fun decode(context: Context, uri: Uri): Result<String> = suspendCancellableCoroutine { continuation ->
        val inputImage = runCatching {
            InputImage.fromFilePath(context, uri)
        }.getOrElse { error ->
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val payload = barcodes
                    .asSequence()
                    .filter { barcode -> barcode.format == Barcode.FORMAT_QR_CODE }
                    .mapNotNull(Barcode::getRawValue)
                    .firstOrNull(String::isNotBlank)

                if (payload != null) {
                    continuation.resume(Result.success(payload))
                } else {
                    continuation.resume(
                        Result.failure(NoSuchElementException("No QR code found in selected image.")),
                    )
                }
            }
            .addOnFailureListener { error ->
                continuation.resume(Result.failure(error))
            }
    }
}
