package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private val AUTO_SCROLL_FRAME_DURATION = 16.milliseconds

/**
 * Independent interaction controller owning auto-scroll boundary detection,
 * frame-rate independent velocity calculations, and single-job coroutine lifecycle management.
 *
 * Adheres to KNet UI Specification: Selection Auto-Scroll Controller v1.0.
 */
internal class AutoScrollController(
    private val scope: CoroutineScope,
    private val maxVelocityPxPerFrame: Float = 45f
) {
    private var scrollJob: Job? = null
    private var currentVelocity: Float = 0f

    /**
     * Calculates the frame scroll velocity based on the pointer Y coordinate
     * relative to container bounds and threshold zones.
     *
     * @param mouseY Pointer Y position relative to the top of the container in pixels.
     * @param containerHeightPx Total height of the container in pixels.
     * @param thresholdPx Boundary threshold in pixels (e.g. 40.dp in px).
     * @return Positive velocity for scrolling down, negative for scrolling up, or 0.0f if in safe zone.
     */
    fun calculateVelocity(mouseY: Float, containerHeightPx: Float, thresholdPx: Float): Float {
        if (containerHeightPx <= 0f || thresholdPx <= 0f) return 0f

        return when {
            // Mouse is past or inside the bottom activation zone
            mouseY >= containerHeightPx - thresholdPx -> {
                val overflow = (mouseY - (containerHeightPx - thresholdPx)).coerceAtLeast(0f)
                val ratio = (overflow / thresholdPx).coerceIn(0f, 3f)
                (ratio.pow(1.5f) * 12f).coerceIn(2f, maxVelocityPxPerFrame)
            }
            // Mouse is past or inside the top activation zone
            mouseY <= thresholdPx -> {
                val overflow = (thresholdPx - mouseY).coerceAtLeast(0f)
                val ratio = (overflow / thresholdPx).coerceIn(0f, 3f)
                -(ratio.pow(1.5f) * 12f).coerceIn(2f, maxVelocityPxPerFrame)
            }

            else -> 0f
        }
    }

    /**
     * Triggers or updates the active auto-scroll loop for a [LazyListState].
     * Guaranteed to maintain at most one active scroll job at a time.
     */
    fun handleDragPointerLazy(
        mouseY: Float,
        containerHeightPx: Float,
        thresholdPx: Float,
        lazyListState: LazyListState
    ) {
        val velocity = calculateVelocity(mouseY, containerHeightPx, thresholdPx)
        currentVelocity = velocity

        if (velocity == 0f) {
            stop()
            return
        }

        if (scrollJob?.isActive == true) return

        scrollJob = scope.launch(Dispatchers.Main) {
            try {
                while (isActive && currentVelocity != 0f) {
                    lazyListState.scrollBy(currentVelocity)
                    delay(AUTO_SCROLL_FRAME_DURATION)
                }
            } finally {
                scrollJob = null
            }
        }
    }

    /**
     * Immediately stops and cancels any active auto-scrolling coroutine job.
     */
    fun stop() {
        currentVelocity = 0f
        scrollJob?.cancel()
        scrollJob = null
    }
}


/**
 * Creates and remembers a reusable [AutoScrollController] instance tied to the composable's scope.
 */
@Composable
internal fun rememberAutoScrollController(): AutoScrollController {
    val scope = rememberCoroutineScope()
    return remember(scope) { AutoScrollController(scope) }
}
