package com.devuloopers.knet.core.logger

import co.touchlab.kermit.Logger

/**
 * Factory object creating tag-scoped Kermit logger instances for KNet modules.
 */
object LoggerFactory {

    private var configuration = LoggerConfiguration()

    /**
     * Updates the global logger configuration.
     */
    fun configure(config: LoggerConfiguration) {
        this.configuration = config
    }

    /**
     * Obtains a Kermit logger instance configured with the specified tag.
     *
     * @param tag Log tag identifying module or context (e.g. [LogTags.PROXY]).
     * @return Kermit [Logger] instance.
     */
    fun get(tag: String = LogTags.KNET): Logger {
        return Logger.withTag(tag)
    }
}
