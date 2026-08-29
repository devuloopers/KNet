package com.devuloopers.knet.companion.ios.bootstrap

import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.platform.PlatformCompanionAdapterFactory
import com.devuloopers.knet.companion.connectivity.transport.IosCompanionProxyTransport
import com.devuloopers.knet.companion.data.ios.IosCompanionDataStoreFactory
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import kotlinx.coroutines.CoroutineScope

/** Restored iOS dependencies required before the product installs its synchronous Koin graph. */
internal data class IosCompanionBootstrap(
    val recordStore: CompanionRecordStore,
    val secretStore: CompanionSecretStore,
    val platformAdapters: CompanionPlatformAdapters,
    val transport: IosCompanionProxyTransport,
) {
    companion object {
        suspend fun create(persistenceScope: CoroutineScope): IosCompanionBootstrap {
            val persistence = IosCompanionDataStoreFactory.create(persistenceScope)
            val transport = IosCompanionProxyTransport()
            return IosCompanionBootstrap(
                recordStore = persistence.records,
                secretStore = persistence.secrets,
                platformAdapters = PlatformCompanionAdapterFactory(transport).create(),
                transport = transport,
            )
        }
    }
}
