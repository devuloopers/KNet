package com.devuloopers.knet.companion.ios.platform.scanner

import com.devuloopers.knet.companion.presentation.action.CompanionAction
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Presents the system photo picker and decodes QR codes from the selected image on iOS. */
@OptIn(ExperimentalForeignApi::class)
internal class IosQrImagePicker(
    private val dispatch: (CompanionAction) -> Unit,
    private val presenter: () -> UIViewController?,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    /** Presents the out-of-process PHPickerViewController to select an image from the photo library. */
    fun pickImage() {
        val presentingController = presenter() ?: run {
            dispatch(CompanionAction.InvitationImageDecodeFailed("Unable to present image picker."))
            return
        }
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter()
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = configuration).apply {
            delegate = this@IosQrImagePicker
        }
        presentingController.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val selectedResult = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        val itemProvider = selectedResult.itemProvider

        itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { nsData: NSData?, nsError: NSError? ->
            dispatch_async(dispatch_get_main_queue()) {
                if (nsData == null || nsError != null) {
                    dispatch(CompanionAction.InvitationImageDecodeFailed("Could not load selected image."))
                    return@dispatch_async
                }
                val payload = decodeQr(nsData)
                if (payload != null) {
                    dispatch(CompanionAction.InvitationScanned(payload))
                } else {
                    dispatch(CompanionAction.InvitationImageDecodeFailed("No QR code found in selected image."))
                }
            }
        }
    }

    private fun decodeQr(data: NSData): String? {
        val ciImage = CIImage.imageWithData(data) ?: return null
        val detector = CIDetector.detectorOfType(
            type = CIDetectorTypeQRCode,
            context = null,
            options = mapOf<Any?, Any>(CIDetectorAccuracy to CIDetectorAccuracyHigh),
        ) ?: return null
        val features = detector.featuresInImage(ciImage)
        return features.asSequence()
            .filterIsInstance<CIQRCodeFeature>()
            .mapNotNull { it.messageString }
            .firstOrNull { it.isNotBlank() }
    }
}
