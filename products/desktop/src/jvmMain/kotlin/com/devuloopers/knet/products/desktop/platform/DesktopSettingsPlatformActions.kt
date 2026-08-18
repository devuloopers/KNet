package com.devuloopers.knet.products.desktop.platform

import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import java.awt.Desktop
import java.nio.file.Files

/** JVM desktop shell implementation for settings-owned platform actions. */
internal class DesktopSettingsPlatformActions(
    configuration: DesktopConfiguration,
) : SettingsPlatformActions {
    private val directory = configuration.appDirectory.toAbsolutePath().normalize()

    override val dataDirectory: String = directory.toString()

    override suspend fun openDataDirectory(): Boolean = runCatching {
        Files.createDirectories(directory)
        if (!Desktop.isDesktopSupported()) return@runCatching false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) return@runCatching false
        desktop.open(directory.toFile())
        true
    }.getOrDefault(false)
}
