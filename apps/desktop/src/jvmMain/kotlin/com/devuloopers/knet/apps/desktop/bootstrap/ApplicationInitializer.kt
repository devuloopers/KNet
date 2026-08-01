package com.devuloopers.knet.apps.desktop.bootstrap

import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration

/**
 * Interface contract for discrete, single-responsibility desktop startup initializers.
 * Initializers are executed in ascending order based on [priority].
 */
interface ApplicationInitializer {

    /**
     * Priority rank determining execution order. Lower numeric values execute earlier.
     */
    val priority: Int

    /**
     * Executes initialization logic for this startup component.
     */
    fun initialize(configuration: DesktopConfiguration)
}
