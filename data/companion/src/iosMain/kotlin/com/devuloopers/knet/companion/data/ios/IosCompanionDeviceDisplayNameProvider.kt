package com.devuloopers.knet.companion.data.ios

import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.data.device.formatCompanionDeviceDisplayName
import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.identity.RegisteredDeviceId
import platform.UIKit.UIDevice

/** iOS adapter deriving a non-secret device-family label without leaking UIKit into common code. */
public class IosCompanionDeviceDisplayNameProvider : CompanionDeviceDisplayNameProvider {
    override suspend fun resolve(deviceId: RegisteredDeviceId): CompanionDeviceDisplayName =
        formatCompanionDeviceDisplayName(
            platformLabel = UIDevice.currentDevice.model,
            fallbackLabel = IOS_FALLBACK_LABEL,
            deviceId = deviceId,
        )

    private companion object {
        const val IOS_FALLBACK_LABEL: String = "iOS device"
    }
}
