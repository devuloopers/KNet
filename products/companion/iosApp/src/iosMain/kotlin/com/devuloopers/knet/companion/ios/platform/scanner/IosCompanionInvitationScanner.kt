@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.ios.platform.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import kotlinx.cinterop.readValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/** AVFoundation implementation rendered directly inside the shared Compose scanner panel. */
internal class IosCompanionInvitationScanner : CompanionInvitationScanner {
    private val mutableState = MutableStateFlow(resolveState())
    private var activeCapture: IosQrCapture? = null
    private var closed: Boolean = false
    private val applicationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) {
        if (!closed && activeCapture == null) mutableState.value = resolveState()
    }

    override val state: StateFlow<CompanionInvitationScannerState> = mutableState.asStateFlow()

    override fun requestCameraPermission() {
        if (closed) return
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> mutableState.value = CompanionInvitationScannerState.STARTING
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(
                AVMediaTypeVideo,
            ) { granted: Boolean ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (!closed) {
                        mutableState.value = if (granted) {
                            CompanionInvitationScannerState.STARTING
                        } else {
                            CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
                        }
                    }
                }
            }

            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted,
                -> mutableState.value = CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED

            else -> mutableState.value = CompanionInvitationScannerState.FAILED
        }
    }

    override fun openApplicationSettings() {
        if (closed) return
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    @Composable
    override fun Preview(onPayloadDetected: (String) -> Unit, modifier: Modifier) {
        val currentPayloadCallback = rememberUpdatedState(onPayloadDetected)
        val capture = remember(this) {
            IosQrCapture(
                onStarted = {
                    if (!closed) mutableState.value = CompanionInvitationScannerState.ACTIVE
                },
                onFailure = {
                    if (!closed) mutableState.value = CompanionInvitationScannerState.FAILED
                },
                onPayload = { payload -> currentPayloadCallback.value(payload) },
            )
        }
        UIKitView(
            factory = { capture.preview },
            modifier = modifier,
            properties = UIKitInteropProperties(
                isInteractive = false,
                isNativeAccessibilityEnabled = false,
            ),
        )
        DisposableEffect(capture) {
            activeCapture?.close()
            activeCapture = capture
            mutableState.value = CompanionInvitationScannerState.STARTING
            capture.start()
            onDispose {
                if (activeCapture === capture) activeCapture = null
                capture.close()
                if (!closed && mutableState.value == CompanionInvitationScannerState.ACTIVE) {
                    mutableState.value = CompanionInvitationScannerState.STARTING
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        activeCapture?.close()
        activeCapture = null
        NSNotificationCenter.defaultCenter.removeObserver(applicationObserver)
    }

    private fun resolveState(): CompanionInvitationScannerState = when {
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) == null ->
            CompanionInvitationScannerState.UNAVAILABLE

        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized ->
            CompanionInvitationScannerState.STARTING

        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusNotDetermined ->
            CompanionInvitationScannerState.PERMISSION_REQUIRED

        else -> CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
    }
}

private class IosQrCapture(
    private val onStarted: () -> Unit,
    private val onFailure: () -> Unit,
    onPayload: (String) -> Unit,
) : AutoCloseable {
    private val session = AVCaptureSession()
    private val delegate = IosQrMetadataDelegate(onPayload)
    private val metadataQueue = dispatch_queue_create(METADATA_QUEUE_LABEL, null)
    val preview = IosCapturePreview(session)
    private var configured: Boolean = false
    private var closed: Boolean = false

    fun start() {
        if (closed) return
        val success = runCatching(::configure).getOrDefault(false)
        if (!success) {
            onFailure()
            return
        }
        dispatch_async(metadataQueue) {
            if (!closed && !session.running) session.startRunning()
            dispatch_async(dispatch_get_main_queue()) {
                if (!closed && session.running) onStarted() else if (!closed) onFailure()
            }
        }
    }

    private fun configure(): Boolean {
        if (configured) return true
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        val output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        return try {
            if (!session.canAddInput(input) || !session.canAddOutput(output)) return false
            session.addInput(input)
            session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, metadataQueue)
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            configured = true
            true
        } finally {
            session.commitConfiguration()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        delegate.close()
        dispatch_async(metadataQueue) {
            if (session.running) session.stopRunning()
        }
    }

    private companion object {
        const val METADATA_QUEUE_LABEL: String = "com.devuloopers.knet.companion.qr-metadata"
    }
}

private class IosQrMetadataDelegate(
    private val onPayload: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var delivered: Boolean = false

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (delivered) return
        val payload = didOutputMetadataObjects.asSequence()
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { metadata -> metadata.stringValue?.takeIf(String::isNotBlank) }
            ?: return
        delivered = true
        dispatch_async(dispatch_get_main_queue()) { onPayload(payload) }
    }

    fun close() {
        delivered = true
    }
}

private class IosCapturePreview(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}
