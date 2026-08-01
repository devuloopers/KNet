package com.devuloopers.knet.core.logger

import co.touchlab.kermit.Logger

/**
 * Core logging wrapper for KNet, routing all logging events through Kermit.
 * This class abstracts the logging backend, allowing dynamic routing of logs.
 */
object KNetLogger {

    /**
     * Logs a verbose level message.
     *
     * @param tag The tag categorizing the log output.
     * @param message The lambda returning the log message (lazy evaluation).
     */
    inline fun verbose(tag: String = LogTags.KNET, message: () -> String) {
        Logger.withTag(tag).v { message() }
    }

    /**
     * Logs an info level message.
     *
     * @param tag The tag categorizing the log output.
     * @param message The lambda returning the log message (lazy evaluation).
     */
    inline fun info(tag: String = LogTags.KNET, message: () -> String) {
        Logger.withTag(tag).i { message() }
    }

    /**
     * Logs a debug level message.
     *
     * @param tag The tag categorizing the log output.
     * @param message The lambda returning the log message (lazy evaluation).
     */
    inline fun debug(tag: String = LogTags.KNET, message: () -> String) {
        Logger.withTag(tag).d { message() }
    }

    /**
     * Logs a warning level message.
     *
     * @param tag The tag categorizing the log output.
     * @param message The lambda returning the log message (lazy evaluation).
     */
    inline fun warn(tag: String = LogTags.KNET, message: () -> String) {
        Logger.withTag(tag).w { message() }
    }

    /**
     * Logs an error level message, with an optional throwable root cause.
     *
     * @param tag The tag categorizing the log output.
     * @param throwable The optional exception or error triggering this log.
     * @param message The lambda returning the log message (lazy evaluation).
     */
    inline fun error(tag: String = LogTags.KNET, throwable: Throwable? = null, message: () -> String) {
        Logger.withTag(tag).e(throwable) { message() }
    }
}
