package com.devuloopers.knet.domain.util

/**
 * Strongly-typed enumeration representing the operating system platform running KNet.
 *
 * Used for platform-specific capabilities such as trust store installation, path separators,
 * and native tool integrations.
 */
enum class HostPlatform(
    val displayName: String,
    val trustStoreName: String
) {
    /**
     * Apple macOS (Darwin) platform using the macOS Keychain Services.
     */
    MACOS(
        displayName = "macOS",
        trustStoreName = "macOS Keychain (login.keychain-db)"
    ),

    /**
     * Microsoft Windows platform using the Windows Certificate Store API (`certutil`).
     */
    WINDOWS(
        displayName = "Windows",
        trustStoreName = "Windows Certificate Store (Root)"
    ),

    /**
     * Linux platform using distribution-specific CA bundles (e.g. `/etc/ssl/certs`).
     */
    LINUX(
        displayName = "Linux",
        trustStoreName = "System CA Store (/etc/ssl/certs)"
    ),

    /**
     * Fallback for unknown or unsupported operating systems.
     */
    UNKNOWN(
        displayName = "Unknown OS",
        trustStoreName = "Generic Trust Store"
    );

    companion object {
        /**
         * Resolves the current runtime host platform by inspecting the `os.name` system property.
         *
         * @return The matching [HostPlatform] value.
         */
        fun current(): HostPlatform {
            val os = try {
                System.getProperty("os.name")?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }

            return when {
                os.contains("mac") || os.contains("darwin") -> MACOS
                os.contains("win") -> WINDOWS
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}
