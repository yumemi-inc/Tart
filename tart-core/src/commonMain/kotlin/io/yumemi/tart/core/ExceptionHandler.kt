package io.yumemi.tart.core

/**
 * Handles non-fatal exceptions raised while a Store is running.
 *
 * This includes exceptions from DSL handlers, middleware, launched coroutines, and state
 * persistence callbacks.
 */
interface ExceptionHandler {
    /**
     * Handles the exception after Tart unwraps its internal bookkeeping errors.
     *
     * @param error The exception to handle
     */
    fun handle(error: Throwable)

    @Suppress("unused")
    companion object {
        /**
         * Ignores all handled exceptions.
         */
        val Noop: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {}
        }

        /**
         * Prints the exception message and stack trace for debugging.
         */
        val Log: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {
                println("[Tart] An exception occurred in the Tart Framework: ${error.message ?: "Unknown error"}")
                error.printStackTrace()
            }
        }

        /**
         * Rethrows handled exceptions instead of swallowing them.
         */
        val Unhandled: ExceptionHandler = object : ExceptionHandler {
            override fun handle(error: Throwable) {
                throw error
            }
        }
    }
}

/**
 * Creates an [ExceptionHandler] from a single callback.
 */
fun ExceptionHandler(block: (Throwable) -> Unit) = object : ExceptionHandler {
    override fun handle(error: Throwable) {
        block.invoke(error)
    }
}
