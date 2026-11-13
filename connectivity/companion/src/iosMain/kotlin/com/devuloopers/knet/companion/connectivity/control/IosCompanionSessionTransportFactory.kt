package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.CompanionSessionTransport
import com.devuloopers.knet.companion.connectivity.http.IosCompanionKtorClientProvider

/** Creates an extension-owned Darwin session adapter without constructing the foreground app's adapter bundle. */
public fun createIosCompanionSessionTransport(): CompanionSessionTransport =
    KtorCompanionSessionTransport(IosCompanionKtorClientProvider())
