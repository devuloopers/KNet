package com.devuloopers.knet.companion.sharedui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Portable states reported by a product-owned invitation camera implementation. */
public enum class CompanionInvitationScannerState {
    /** The current product or device does not provide an invitation camera. */
    UNAVAILABLE,

    /** Camera access has not been requested yet. */
    PERMISSION_REQUIRED,

    /** Camera access was denied but may still be requested again. */
    PERMISSION_DENIED,

    /** Camera access must be enabled from the platform application settings. */
    PERMISSION_PERMANENTLY_DENIED,

    /** The platform is acquiring and binding the camera. */
    STARTING,

    /** Preview and QR analysis are active. */
    ACTIVE,

    /** The camera or QR analyzer could not be started. */
    FAILED,
}

/**
 * Product capability that supplies native camera content to the shared companion scanner screen.
 *
 * Native context, lifecycle, camera, permission, and image types remain behind this interface. Implementations
 * must deliver at most one non-blank payload for each composed [Preview] session and release preview/analyzer
 * resources when that composition leaves the screen.
 */
public interface CompanionInvitationScanner : AutoCloseable {
    /** Current portable permission and camera lifecycle state. */
    public val state: StateFlow<CompanionInvitationScannerState>

    /** Requests native camera permission in response to an explicit user action. */
    public fun requestCameraPermission()

    /** Opens the native application settings when camera permission cannot be requested again. */
    public fun openApplicationSettings()

    /**
     * Renders the native camera preview and begins QR-only analysis for this composition.
     *
     * @param onPayloadDetected receives the first non-blank decoded QR payload.
     * @param modifier layout modifier supplied by the shared scanner screen.
     */
    @Composable
    public fun Preview(
        onPayloadDetected: (String) -> Unit,
        modifier: Modifier,
    )

    /** Permanently releases product-owned camera and lifecycle registrations. */
    public override fun close()
}

/** Fail-closed scanner used by products that have not supplied a native camera implementation. */
public object UnavailableCompanionInvitationScanner : CompanionInvitationScanner {
    override val state: StateFlow<CompanionInvitationScannerState> =
        MutableStateFlow(CompanionInvitationScannerState.UNAVAILABLE)

    override fun requestCameraPermission(): Unit = Unit

    override fun openApplicationSettings(): Unit = Unit

    @Composable
    override fun Preview(onPayloadDetected: (String) -> Unit, modifier: Modifier): Unit = Unit

    override fun close(): Unit = Unit
}
