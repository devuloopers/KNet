package com.devuloopers.knet.companion.android

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded Android image decoder for user-selected KNet invitation QR images. */
internal class AndroidQrInvitationDecoder(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Returns decoded QR text or null when the selected image is unreadable or contains no QR code. */
    suspend fun decode(uri: Uri): String? {
        val image = withContext(ioDispatcher) { readImage(uri) } ?: return null
        return withContext(computationDispatcher) { decodePixels(image) }
    }

    private fun readImage(uri: Uri): QrPixelImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            QrPixelImage(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    private fun decodePixels(image: QrPixelImage): String? = runCatching {
        val source = RGBLuminanceSource(image.width, image.height, image.pixels)
        MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
        ).text
    }.getOrNull()

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAXIMUM_IMAGE_DIMENSION || height / sampleSize > MAXIMUM_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val MAXIMUM_IMAGE_DIMENSION: Int = 1_600
    }

    private data class QrPixelImage(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )
}
