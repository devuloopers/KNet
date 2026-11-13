package com.devuloopers.knet.companion.android.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android CameraX and bundled ML Kit implementation of the portable invitation scanner capability. */
internal class AndroidCompanionInvitationScanner(
    private val activity: ComponentActivity,
) : CompanionInvitationScanner {
    // This installation-local marker distinguishes a fresh request from an unresolved denial after recreation.
    // A grant clears it, so a later one-time permission expiry is eligible for a fresh native request.
    private val permissionHistory = activity.getSharedPreferences("knet_camera_permission", Context.MODE_PRIVATE)
    private var permissionRequested: Boolean = permissionHistory.getBoolean("unresolved_request", false)
    private val mutableState: MutableStateFlow<CompanionInvitationScannerState> = MutableStateFlow(
        resolveCameraPermissionState(
            granted = hasCameraPermission(),
            permissionRequested = permissionRequested,
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
        ),
    )
    private var permissionRequestInProgress: Boolean = false
    private var closed: Boolean = false
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestInProgress = false
        if (closed) return@registerForActivityResult
        rememberPermissionRequest(unresolved = !granted)
        mutableState.value = resolveCameraPermissionState(
            granted = granted,
            permissionRequested = true,
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
        )
    }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME && !closed && !permissionRequestInProgress) refreshPermissionState()
    }

    override val state: StateFlow<CompanionInvitationScannerState> = mutableState.asStateFlow()

    init {
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    override fun requestCameraPermission() {
        if (closed || permissionRequestInProgress) return
        if (hasCameraPermission()) {
            rememberPermissionRequest(unresolved = false)
            mutableState.value = CompanionInvitationScannerState.STARTING
        } else {
            val previousUnresolvedRequest = permissionRequested
            rememberPermissionRequest(unresolved = true)
            permissionRequestInProgress = true
            mutableState.value = CompanionInvitationScannerState.PERMISSION_REQUESTING
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (_: IllegalStateException) {
                permissionRequestInProgress = false
                rememberPermissionRequest(unresolved = previousUnresolvedRequest)
                mutableState.value = CompanionInvitationScannerState.FAILED
            }
        }
    }

    override fun openApplicationSettings() {
        if (closed) return
        activity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null),
            ),
        )
    }

    @Composable
    override fun Preview(onPayloadDetected: (String) -> Unit, modifier: Modifier) {
        val currentPayloadCallback = rememberUpdatedState(onPayloadDetected)
        val cameraController = remember(activity) {
            LifecycleCameraController(activity).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            }
        }
        val barcodeScanner = remember {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        }
        val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
        val callbackExecutor: Executor = remember(activity) {
            Executor { command -> activity.runOnUiThread(command) }
        }
        val analyzer = remember(barcodeScanner, callbackExecutor) {
            AndroidQrCodeAnalyzer(
                barcodeScanner = barcodeScanner,
                callbackExecutor = callbackExecutor,
                onPayloadDetected = { payload -> currentPayloadCallback.value(payload) },
            )
        }

        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            update = { previewView -> previewView.controller = cameraController },
            modifier = modifier,
        )

        DisposableEffect(cameraController, analyzer, analysisExecutor, barcodeScanner) {
            val started = runCatching {
                mutableState.value = CompanionInvitationScannerState.STARTING
                cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
                cameraController.bindToLifecycle(activity)
                mutableState.value = CompanionInvitationScannerState.ACTIVE
            }.isSuccess
            if (!started) mutableState.value = CompanionInvitationScannerState.FAILED

            onDispose {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                barcodeScanner.close()
                analysisExecutor.shutdownNow()
                if (!closed && mutableState.value == CompanionInvitationScannerState.ACTIVE) {
                    mutableState.value = CompanionInvitationScannerState.STARTING
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        activity.lifecycle.removeObserver(lifecycleObserver)
        permissionLauncher.unregister()
    }

    private fun rememberPermissionRequest(unresolved: Boolean) {
        permissionRequested = unresolved
        permissionHistory.edit().putBoolean("unresolved_request", unresolved).apply()
    }

    private fun refreshPermissionState() {
        val granted = hasCameraPermission()
        if (granted && permissionRequested) rememberPermissionRequest(unresolved = false)
        mutableState.value = resolveCameraPermissionState(
            granted = granted,
            permissionRequested = permissionRequested,
            shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
            requestInProgress = permissionRequestInProgress,
        )
    }

    private fun hasCameraPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
