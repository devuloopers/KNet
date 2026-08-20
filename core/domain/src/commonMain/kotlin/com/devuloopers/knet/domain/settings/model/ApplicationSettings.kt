package com.devuloopers.knet.domain.settings.model

import com.devuloopers.knet.scripting.model.ScriptLanguage
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Validated TCP port used by KNet's local proxy listener.
 *
 * @property value Port number in the inclusive TCP range from 1 through 65535.
 * @throws IllegalArgumentException when [value] is outside the valid TCP port range.
 */
@JvmInline
value class ProxyPort(val value: Int) {
    init {
        require(value in MINIMUM_VALUE..MAXIMUM_VALUE) {
            "Proxy port must be between $MINIMUM_VALUE and $MAXIMUM_VALUE."
        }
    }

    /** Constants defining the supported proxy-port range and default listener port. */
    companion object {
        const val MINIMUM_VALUE: Int = 1
        const val MAXIMUM_VALUE: Int = 65_535

        /** Default KNet listener port. */
        val Default: ProxyPort = ProxyPort(8080)
    }
}

/**
 * Process-level user preferences that affect KNet behavior independently from window layout.
 *
 * Durations use Kotlin time so application and UI consumers do not exchange raw unit-dependent numbers.
 * The constructor validates values at the domain boundary before persistence or runtime synchronization.
 *
 * @property proxyPort Port committed for the local proxy listener.
 * @property autoClearTrafficOnStartup Whether stored traffic is cleared during the next application startup.
 * @property defaultScriptLanguage Default scripting language for newly created API Studio requests.
 * @property apiStudioTimeout Maximum duration of an API Studio request.
 * @property liveInterceptionTimeout Maximum duration a breakpoint may remain suspended.
 * @throws IllegalArgumentException when a timeout is outside its supported range.
 */
data class ApplicationSettings(
    val proxyPort: ProxyPort = ProxyPort.Default,
    val autoClearTrafficOnStartup: Boolean = false,
    val defaultScriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val apiStudioTimeout: Duration = DEFAULT_TIMEOUT,
    val liveInterceptionTimeout: Duration = DEFAULT_TIMEOUT,
) {
    init {
        require(apiStudioTimeout in MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT) {
            "API Studio timeout must be between one second and sixty minutes."
        }
        require(liveInterceptionTimeout in MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT) {
            "Live interception timeout must be between one second and sixty minutes."
        }
    }

    /** Supported timeout bounds shared by persistence, presentation validation, and runtime consumers. */
    companion object {
        val MINIMUM_TIMEOUT: Duration = 1.seconds
        val MAXIMUM_TIMEOUT: Duration = 60.minutes
        val DEFAULT_TIMEOUT: Duration = 60.seconds
    }
}
