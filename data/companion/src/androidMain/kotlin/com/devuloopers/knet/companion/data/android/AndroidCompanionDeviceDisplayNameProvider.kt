package com.devuloopers.knet.companion.data.android

import android.os.Build
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.data.device.formatCompanionDeviceDisplayName
import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.identity.RegisteredDeviceId

/** Android adapter deriving a non-secret model label without requiring an Activity or Context. */
public class AndroidCompanionDeviceDisplayNameProvider : CompanionDeviceDisplayNameProvider {
    override suspend fun resolve(deviceId: RegisteredDeviceId): CompanionDeviceDisplayName {
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        val platformLabel = when {
            model.isBlank() -> manufacturer
            manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return formatCompanionDeviceDisplayName(
            platformLabel = platformLabel,
            fallbackLabel = ANDROID_FALLBACK_LABEL,
            deviceId = deviceId,
        )
    }

    private companion object {
        const val ANDROID_FALLBACK_LABEL: String = "Android device"
    }
}
