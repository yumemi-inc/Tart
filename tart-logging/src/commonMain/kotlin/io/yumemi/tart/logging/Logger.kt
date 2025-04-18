package io.yumemi.tart.logging

/**
 * Interface that provides logging functionality.
 * Used for logging internal Tart operations and diagnostic information.
 */
interface Logger {
    /**
     * Outputs a log with the specified severity.
     *
     * @param severity The severity of the log
     * @param tag The tag for the log
     * @param throwable Associated exception (if any)
     * @param message The log message
     */
    suspend fun log(
        severity: Severity,
        tag: String,
        throwable: Throwable?,
        message: String,
    )

    /**
     * Enumeration representing log severity levels.
     * Supports common logging levels.
     */
    enum class Severity {
        /** Very detailed debug information */
        Verbose,

        /** Debug information */
        Debug,

        /** General information */
        Info,

        /** Warning information */
        Warn,

        /** Error information */
        Error,

        /** Critical error information */
        Assert,
    }
}
