package io.yumemi.tart.logging

import co.touchlab.kermit.Severity
import co.touchlab.kermit.Logger.Companion as Kermit

/**
 * Default implementation of the Logger interface using Kermit.
 * Used as the default logger for the Tart framework.
 */
object DefaultLogger : Logger {
    private var isDisabled = false

    /**
     * Outputs a log with the specified severity.
     *
     * @param severity The severity of the log
     * @param tag The tag for the log
     * @param throwable Associated exception (if any)
     * @param message The log message
     */
    override suspend fun log(severity: Logger.Severity, tag: String, throwable: Throwable?, message: String) {
        if (isDisabled) return
        Kermit.log(
            severity = severity.map(),
            tag = tag,
            throwable = throwable,
            message = message,
        )
    }

    /**
     * Disables the logger. After disabling, no logs will be output.
     */
    @Suppress("unused")
    fun disable() {
        isDisabled = true
    }

    private fun Logger.Severity.map() = when (this) {
        Logger.Severity.Verbose -> Severity.Verbose
        Logger.Severity.Debug -> Severity.Debug
        Logger.Severity.Info -> Severity.Info
        Logger.Severity.Warn -> Severity.Warn
        Logger.Severity.Error -> Severity.Error
        Logger.Severity.Assert -> Severity.Assert
    }
}
