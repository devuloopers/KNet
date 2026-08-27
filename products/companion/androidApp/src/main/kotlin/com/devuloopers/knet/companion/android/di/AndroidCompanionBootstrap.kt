package com.devuloopers.knet.companion.android.di

import android.content.Context
import com.devuloopers.knet.companion.android.inspection.AndroidInspectionRuntimeCoordinator
import com.devuloopers.knet.companion.android.inspection.AndroidVpnServiceInspectionBackend
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.platform.PlatformCompanionAdapterFactory
import com.devuloopers.knet.companion.connectivity.transport.AndroidCompanionProxyTransport
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.connectivity.transport.PlatformAndroidTunForwarder
import com.devuloopers.knet.companion.data.android.AndroidCompanionRecordStore
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionSecretStore
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore

/** Fully restored Android dependencies that must exist before synchronous Koin definitions are installed. */
internal data class AndroidCompanionBootstrap(
    val recordStore: CompanionRecordStore,
    val secretStore: CompanionSecretStore,
    val platformAdapters: CompanionPlatformAdapters,
    val transport: AndroidCompanionProxyTransport,
    val tunForwarder: AndroidTunForwarder,
    val inspectionCoordinator: AndroidInspectionRuntimeCoordinator,
) {
    companion object {
        /** Restores disk-backed stores before creating callback-owning platform adapters. */
        suspend fun create(context: Context): AndroidCompanionBootstrap {
            val applicationContext = context.applicationContext
            val recordStore = AndroidCompanionRecordStore.create(applicationContext)
            val secretStore = AndroidKeystoreCompanionSecretStore.create(applicationContext)
            val transport = AndroidCompanionProxyTransport()
            val tunForwarder = PlatformAndroidTunForwarder(applicationContext, transport)
            val inspectionCoordinator = AndroidInspectionRuntimeCoordinator()
            val inspectionBackend = AndroidVpnServiceInspectionBackend(applicationContext, inspectionCoordinator)
            return AndroidCompanionBootstrap(
                recordStore = recordStore,
                secretStore = secretStore,
                platformAdapters = PlatformCompanionAdapterFactory(
                    context = applicationContext,
                    inspectionBackend = inspectionBackend,
                ).create(),
                transport = transport,
                tunForwarder = tunForwarder,
                inspectionCoordinator = inspectionCoordinator,
            )
        }
    }
}
