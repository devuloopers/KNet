package com.devuloopers.knet.companion.connectivity.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/** Continuously monitors resolved DNS-SD service data on Android 14 and newer. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class Android34CompanionServiceMonitor(
    private val nsdManager: NsdManager,
    private val callbackExecutor: Executor,
    private val onServiceUpdated: (generation: Long, serviceInfo: NsdServiceInfo) -> Unit,
    private val onServiceUnavailable: (generation: Long, serviceName: String) -> Unit,
) {
    private val lock = Any()
    private val registrations = linkedMapOf<String, Registration>()

    /** Starts monitoring one discovered service; duplicate discovery callbacks remain idempotent. */
    fun observe(serviceInfo: NsdServiceInfo, generation: Long) {
        val key = serviceInfo.registrationKey()
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(updatedServiceInfo: NsdServiceInfo) {
                if (isCurrent(key, this)) onServiceUpdated(generation, updatedServiceInfo)
            }

            override fun onServiceLost() {
                if (isCurrent(key, this)) onServiceUnavailable(generation, serviceInfo.serviceName)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                if (removeIfCurrent(key, this)) onServiceUnavailable(generation, serviceInfo.serviceName)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        val shouldRegister = synchronized(lock) {
            if (key in registrations) {
                false
            } else {
                registrations[key] = Registration(callback)
                true
            }
        }
        if (!shouldRegister) return
        try {
            nsdManager.registerServiceInfoCallback(serviceInfo, callbackExecutor, callback)
        } catch (_: RuntimeException) {
            if (removeIfCurrent(key, callback)) onServiceUnavailable(generation, serviceInfo.serviceName)
        }
    }

    /** Stops monitoring a service that the discovery listener reported as lost. */
    fun forget(serviceInfo: NsdServiceInfo) {
        val callback = synchronized(lock) {
            registrations.remove(serviceInfo.registrationKey())?.callback
        } ?: return
        runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
    }

    /** Releases every active service-info callback before discovery restarts or stops. */
    fun clear() {
        val callbacks = synchronized(lock) {
            registrations.values.map(Registration::callback).also { registrations.clear() }
        }
        callbacks.forEach { callback ->
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
    }

    private fun isCurrent(key: String, callback: NsdManager.ServiceInfoCallback): Boolean =
        synchronized(lock) { registrations[key]?.callback === callback }

    private fun removeIfCurrent(key: String, callback: NsdManager.ServiceInfoCallback): Boolean =
        synchronized(lock) {
            if (registrations[key]?.callback !== callback) return@synchronized false
            registrations.remove(key)
            true
        }

    private data class Registration(val callback: NsdManager.ServiceInfoCallback)
}

private fun NsdServiceInfo.registrationKey(): String = "$serviceName\u0000$serviceType"
