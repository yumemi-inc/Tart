package io.yumemi.tart.logging

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
     * @param message Provider for the log message
     */
    override fun log(severity: Logger.Severity, tag: String, throwable: Throwable?, message: () -> String) {
        if (isDisabled) return
        when (severity) {
            Logger.Severity.Verbose -> Kermit.v(tag, throwable, message)
            Logger.Severity.Debug -> Kermit.d(tag, throwable, message)
            Logger.Severity.Info -> Kermit.i(tag, throwable, message)
            Logger.Severity.Warn -> Kermit.w(tag, throwable, message)
            Logger.Severity.Error -> Kermit.e(tag, throwable, message)
            Logger.Severity.Assert -> Kermit.a(tag, throwable, message)
        }
    }

    /**
     * Disables the logger. After disabling, no logs will be output.
     */
    @Suppress("unused")
    fun disable() {
        isDisabled = true
    }
}
