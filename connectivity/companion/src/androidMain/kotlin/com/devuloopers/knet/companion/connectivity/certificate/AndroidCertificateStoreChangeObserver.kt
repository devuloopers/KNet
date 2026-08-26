package com.devuloopers.knet.companion.connectivity.certificate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.security.KeyChain
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Android trust-store callback owner that emits verification triggers without claiming trust. */
internal class AndroidCertificateStoreChangeObserver(context: Context) : CompanionCertificateStoreChangeObserver,
    AutoCloseable {
    private val applicationContext: Context = context.applicationContext
    private val changes: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    private var closed: Boolean = false
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeyChain.ACTION_TRUST_STORE_CHANGED) changes.tryEmit(Unit)
        }
    }

    init {
        val filter = IntentFilter(KeyChain.ACTION_TRUST_STORE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            applicationContext.registerReceiver(receiver, filter)
        }
    }

    override fun observeChanges(): Flow<Unit> = changes.asSharedFlow()

    override fun close() {
        if (closed) return
        closed = true
        runCatching { applicationContext.unregisterReceiver(receiver) }
    }
}
