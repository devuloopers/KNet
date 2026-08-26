package com.devuloopers.knet.companion.android.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor

/** CameraX analyzer that recognizes QR codes and delivers only the first non-blank payload. */
internal class AndroidQrCodeAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val callbackExecutor: Executor,
    private val onPayloadDetected: (String) -> Unit,
    private val deliveryGate: SingleDeliveryQrGate = SingleDeliveryQrGate(),
) : ImageAnalysis.Analyzer {
    /** Processes one frame without allowing analyzer backlog or duplicate payload delivery. */
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (!deliveryGate.tryBeginAnalysis()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            deliveryGate.finishAnalysis()
            imageProxy.close()
            return
        }
        runCatching {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
        }
            .onSuccess { task ->
                task.addOnSuccessListener(callbackExecutor) { barcodes ->
                    val payload = barcodes
                        .asSequence()
                        .filter { barcode -> barcode.format == Barcode.FORMAT_QR_CODE }
                        .mapNotNull(Barcode::getRawValue)
                        .firstOrNull(String::isNotBlank)
                    if (payload != null && deliveryGate.tryDeliver()) onPayloadDetected(payload)
                }.addOnCompleteListener(callbackExecutor) {
                    deliveryGate.finishAnalysis()
                    imageProxy.close()
                }
            }
            .onFailure {
                deliveryGate.finishAnalysis()
                imageProxy.close()
            }
    }
}
