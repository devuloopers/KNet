package com.devuloopers.knet.companion.ios.di

import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.data.ios.IosCompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.data.ios.IosKeychainCompanionDeviceIdentity
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.di.CompanionSharedModules
import com.devuloopers.knet.companion.ios.bootstrap.IosCompanionBootstrap
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/** iOS-only definitions installed before the portable companion product graph. */
internal object CompanionIosModules {
    /** Composes one iOS prerequisite module with the shared mobile definitions. */
    fun create(bootstrap: IosCompanionBootstrap): List<Module> =
        listOf(iosPlatformModule(bootstrap)) + CompanionSharedModules.create()

    private fun iosPlatformModule(bootstrap: IosCompanionBootstrap): Module = module {
        single<CompanionPlatformAdapters>(createdAtStart = true) { bootstrap.platformAdapters } onClose { adapters ->
            adapters?.close()
        }
        single<CompanionRecordStore> { bootstrap.recordStore }
        single<CompanionSecretStore> { bootstrap.secretStore }
        single { IosKeychainCompanionDeviceIdentity() }
        single<CompanionDeviceIdentityProvider> { get<IosKeychainCompanionDeviceIdentity>() }
        single<CompanionDeviceDisplayNameProvider> { IosCompanionDeviceDisplayNameProvider() }
        single<CompanionDeviceProofSigner> { get<IosKeychainCompanionDeviceIdentity>() }
        single<CompanionTransport> { bootstrap.transport }
    }
}
