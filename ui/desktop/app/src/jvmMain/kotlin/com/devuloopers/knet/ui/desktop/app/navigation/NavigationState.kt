package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth interaction state coordinator for the KNet Desktop Navigation Framework.
 *
 * Owns expansion state, overlay presentation animations, and keyboard shortcut event routing.
 */
public class NavigationState {
    /**
     * Single source of truth expansion state.
     * True when the mouse pointer is anywhere over the 64dp rail or 240dp overlay surface.
     */
    public var isExpanded: Boolean by mutableStateOf(false)

    /**
     * Triggers pointer entry into the navigation hover region.
     */
    public fun onPointerEnter() {
        isExpanded = true
    }

    /**
     * Triggers pointer exit from the entire navigation hover region.
     */
    public fun onPointerExit() {
        isExpanded = false
    }

    /**
     * Handles global navigation key events (Escape collapse, Ctrl+1..Ctrl+5, Ctrl+,).
     *
     * @param keyEvent Key press event.
     * @param onDestinationSelected Callback to navigate to a target destination.
     * @return True if key event was consumed.
     */
    public fun handleKeyEvent(
        keyEvent: KeyEvent,
        onDestinationSelected: (DesktopDestination) -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        val isModifierPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
        return when {
            keyEvent.key == Key.Escape && isExpanded -> {
                isExpanded = false
                true
            }
            isModifierPressed && keyEvent.key == Key.One -> {
                onDestinationSelected(DesktopDestination.Traffic)
                true
            }
            isModifierPressed && keyEvent.key == Key.Two -> {
                onDestinationSelected(DesktopDestination.ApiStudio)
                true
            }
            isModifierPressed && keyEvent.key == Key.Three -> {
                onDestinationSelected(DesktopDestination.Certificate)
                true
            }
            isModifierPressed && keyEvent.key == Key.Four -> {
                onDestinationSelected(DesktopDestination.Scripting)
                true
            }
            isModifierPressed && keyEvent.key == Key.Comma -> {
                onDestinationSelected(DesktopDestination.Settings)
                true
            }
            else -> false
        }
    }
}

/**
 * Creates and remembers a [NavigationState] instance.
 */
@Composable
public fun rememberNavigationState(): NavigationState {
    return remember { NavigationState() }
}

/**
 * Helper data holder storing animated presentation values for the navigation overlay.
 */
public data class NavigationPresentation(
    val overlayWidth: Dp,
    val labelAlpha: Float,
    val labelOffset: Dp
)

/**
 * Remembers and computes smooth presentation animation values driven by [NavigationState.isExpanded].
 */
@Composable
public fun rememberNavigationPresentation(state: NavigationState): NavigationPresentation {
    val animationSpec: AnimationSpec<Dp> = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    val floatSpec: AnimationSpec<Float> = tween(durationMillis = 180, easing = FastOutSlowInEasing)

    val overlayWidth by animateDpAsState(
        targetValue = if (state.isExpanded) 240.dp else 64.dp,
        animationSpec = animationSpec,
        label = "NavigationOverlayWidth"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (state.isExpanded) 1f else 0f,
        animationSpec = floatSpec,
        label = "NavigationLabelAlpha"
    )

    val labelOffset by animateDpAsState(
        targetValue = if (state.isExpanded) 0.dp else (-6).dp,
        animationSpec = animationSpec,
        label = "NavigationLabelOffset"
    )

    return NavigationPresentation(overlayWidth, labelAlpha, labelOffset)
}
