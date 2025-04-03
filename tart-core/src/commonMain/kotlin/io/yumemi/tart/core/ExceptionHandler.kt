package io.yumemi.tart.core

/**
 * Interface for handling exceptions that occur in a Store.
 * Exceptions that occur during Store operation are processed by this handler.
 */
interface ExceptionHandler {
    /**
     * Handles the occurred exception.
     *
     * @param error The exception to handle
     */
    fun handle(error: Throwable)

    @Suppress("unused")
    companion object {
        /**
         * No-op implementation of ExceptionHandler that silently ignores all exceptions.
         *
         * @return An ExceptionHandler instance that ignores all exceptions
         */
        val Noop: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {}
        }

        /**
         * Logging implementation of ExceptionHandler that prints exceptions to standard output.
         *
         * @return An ExceptionHandler instance that logs exceptions to standard output
         */
        val Log: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {
                println("[Tart] An exception occurred in the Tart Framework: ${error.message ?: "Unknown error"}")
                error.printStackTrace()
            }
        }

        /**
         * Default implementation of ExceptionHandler that rethrows all exceptions.
         *
         * @return An ExceptionHandler instance that rethrows all exceptions
         */
        val Default: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {
                throw error
            }
        }
    }
}

/**
 * Factory function to easily create an instance of ExceptionHandler.
 *
 * @param block Callback function to handle exceptions
 * @return A new ExceptionHandler instance
 */
fun ExceptionHandler(block: (Throwable) -> Unit) = object : ExceptionHandler {
    override fun handle(error: Throwable) {
        block.invoke(error)
    }
}
