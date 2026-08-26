package com.devuloopers.knet.companion.android.scanner

import java.util.concurrent.atomic.AtomicBoolean

/** Thread-safe gate that permits one in-flight frame and one delivered payload per scanner composition. */
internal class SingleDeliveryQrGate {
    private val analyzing: AtomicBoolean = AtomicBoolean(false)
    private val delivered: AtomicBoolean = AtomicBoolean(false)

    /** Claims the next frame when no frame is running and no payload has already been delivered. */
    fun tryBeginAnalysis(): Boolean = !delivered.get() && analyzing.compareAndSet(false, true)

    /** Releases the current frame after every success or failure path. */
    fun finishAnalysis() {
        analyzing.set(false)
    }

    /** Claims the only payload delivery allowed for this gate. */
    fun tryDeliver(): Boolean = delivered.compareAndSet(false, true)
}
