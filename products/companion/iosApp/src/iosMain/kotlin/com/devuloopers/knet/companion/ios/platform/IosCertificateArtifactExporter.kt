package com.devuloopers.knet.companion.ios.platform

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*

/** Presents the system share sheet for one authenticated public certificate profile. */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal class IosCertificateArtifactExporter(
    private val dispatch: (CompanionAction) -> Unit,
    private val presenter: () -> UIViewController?,
) {
    private var activeDesktopId: CompanionDesktopId? = null

    fun export(desktopId: CompanionDesktopId, artifact: CompanionCertificateArtifact) {
        if (activeDesktopId != null) {
            dispatch(CompanionAction.CertificateExportFailed(desktopId))
            return
        }
        val fileName = artifact.suggestedFileName.safeProfileFileName()
        val fileUrl = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val bytes = artifact.copyBytes()
        val written = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                .writeToURL(fileUrl, atomically = true)
        }
        val presentingController = presenter()
        if (!written || presentingController == null) {
            if (written) NSFileManager.defaultManager.removeItemAtURL(fileUrl, error = null)
            dispatch(CompanionAction.CertificateExportFailed(desktopId))
            return
        }

        activeDesktopId = desktopId
        val controller = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null,
        )
        controller.popoverPresentationController?.apply {
            sourceView = presentingController.view
            sourceRect = presentingController.view.bounds
        }
        controller.completionWithItemsHandler = { _, completed, _, _ ->
            if (activeDesktopId == desktopId) activeDesktopId = null
            NSFileManager.defaultManager.removeItemAtURL(fileUrl, error = null)
            if (completed) {
                dispatch(
                    CompanionAction.CertificateExportCompleted(
                        desktopId = desktopId,
                        fileName = fileName,
                        locationDescription = "the selected location",
                    ),
                )
            } else {
                dispatch(CompanionAction.CertificateExportCancelled(desktopId))
            }
        }
        presentingController.presentViewController(controller, animated = true, completion = null)
    }
}

private fun String.safeProfileFileName(): String {
    val safe = filter { character -> character.isLetterOrDigit() || character in "._-" }
        .take(MAXIMUM_FILE_NAME_LENGTH)
    return safe.takeIf { it.endsWith(".mobileconfig", ignoreCase = true) }
        ?: DEFAULT_PROFILE_FILE_NAME
}

private const val DEFAULT_PROFILE_FILE_NAME: String = "knet-root-ca.mobileconfig"
private const val MAXIMUM_FILE_NAME_LENGTH: Int = 96
