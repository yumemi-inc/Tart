package io.yumemi.tart.logging

import co.touchlab.kermit.Severity
import co.touchlab.kermit.Logger.Companion as Kermit

object DefaultLogger : Logger {
    private var isDisabled = false

    override suspend fun log(severity: Logger.Severity, tag: String, throwable: Throwable?, message: String) {
        if (isDisabled) return
        Kermit.log(
            severity = severity.map(),
            tag = tag,
            throwable = throwable,
            message = message,
        )
    }

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
