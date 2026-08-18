package com.devuloopers.knet.ui.desktop.certificate.model

/** Desktop operating-system presentation categories used by certificate trust UI. */
enum class HostPlatform(
    val displayName: String,
    val trustStoreName: String,
) {
    /** Apple macOS using the login Keychain. */
    MACOS("macOS", "macOS Keychain (login.keychain-db)"),

    /** Microsoft Windows using its root certificate store. */
    WINDOWS("Windows", "Windows Certificate Store (Root)"),

    /** Linux using distribution-specific certificate bundles. */
    LINUX("Linux", "System CA Store (/etc/ssl/certs)"),

    /** Unknown or unsupported desktop operating system. */
    UNKNOWN("Unknown OS", "Generic Trust Store");

    companion object {
        /** Detects the current desktop operating system from the JVM host property. */
        fun current(): HostPlatform {
            val operatingSystem = runCatching { System.getProperty("os.name").orEmpty().lowercase() }
                .getOrDefault("")
            return when {
                "mac" in operatingSystem || "darwin" in operatingSystem -> MACOS
                "win" in operatingSystem -> WINDOWS
                "nix" in operatingSystem || "nux" in operatingSystem || "aix" in operatingSystem -> LINUX
                else -> UNKNOWN
            }
        }
    }
}
