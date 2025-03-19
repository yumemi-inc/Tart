package io.yumemi.tart.core

interface ExceptionHandler {
    fun handle(error: Throwable)
}

fun ExceptionHandler(block: (error: Throwable) -> Unit): ExceptionHandler {
    return object : ExceptionHandler {
        override fun handle(error: Throwable) {
            block.invoke(error)
        }
    }
}
