package com.devuloopers.knet.companion.android.di

import com.devuloopers.knet.companion.android.inspection.AndroidInspectionRuntimeCoordinator
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.data.android.AndroidCompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceProofSigner
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.di.CompanionSharedModules
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Android-only definitions installed before the portable companion product graph. */
internal object CompanionAndroidModules {
    /** Composes one Android prerequisite module with the shared mobile definitions. */
    fun create(bootstrap: AndroidCompanionBootstrap): List<Module> =
        listOf(androidPlatformModule(bootstrap)) + CompanionSharedModules.create()

    private fun androidPlatformModule(bootstrap: AndroidCompanionBootstrap): Module = module {
        single<CompanionPlatformAdapters>(createdAtStart = true) { bootstrap.platformAdapters } onClose { adapters ->
            adapters?.close()
        }
        single<CompanionRecordStore> { bootstrap.recordStore }
        single<CompanionSecretStore> { bootstrap.secretStore }
        single { AndroidKeystoreCompanionDeviceIdentityProvider() } bind CompanionDeviceIdentityProvider::class
        single { AndroidCompanionDeviceDisplayNameProvider() } bind CompanionDeviceDisplayNameProvider::class
        single { AndroidKeystoreCompanionDeviceProofSigner() } bind CompanionDeviceProofSigner::class
        single<CompanionTransport> { bootstrap.transport }
        single<AndroidTunForwarder> { bootstrap.tunForwarder }
        single<AndroidInspectionRuntimeCoordinator> { bootstrap.inspectionCoordinator }
    }
}
