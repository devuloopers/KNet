package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Explicit multiplatform route serialization used by Navigation 3 state restoration. */
internal val companionNavigationConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(CompanionRoute.ConnectDesktop::class, CompanionRoute.ConnectDesktop.serializer())
            subclass(CompanionRoute.ConfirmDesktop::class, CompanionRoute.ConfirmDesktop.serializer())
            subclass(CompanionRoute.CertificateSetup::class, CompanionRoute.CertificateSetup.serializer())
            subclass(CompanionRoute.InspectionPermission::class, CompanionRoute.InspectionPermission.serializer())
            subclass(CompanionRoute.Home::class, CompanionRoute.Home.serializer())
        }
    }
}
