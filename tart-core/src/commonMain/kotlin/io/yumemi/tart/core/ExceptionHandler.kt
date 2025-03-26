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
}

/**
 * Factory function to easily create an instance of ExceptionHandler.
 *
 * @param block Callback function to handle exceptions
 * @return A new ExceptionHandler instance
 */
fun ExceptionHandler(block: (error: Throwable) -> Unit): ExceptionHandler {
    return object : ExceptionHandler {
        override fun handle(error: Throwable) {
            block.invoke(error)
        }
    }
}
