@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/** Rechecks platform trust whenever the app returns from the iOS profile-installation flow. */
internal class IosCertificateStoreChangeObserver : CompanionCertificateStoreChangeObserver, AutoCloseable {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var closed: Boolean = false
    private val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { changes.tryEmit(Unit) }

    override fun observeChanges(): Flow<Unit> = changes.asSharedFlow()

    override fun close() {
        if (closed) return
        closed = true
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}
