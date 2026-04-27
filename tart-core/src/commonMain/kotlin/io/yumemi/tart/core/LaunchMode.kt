package io.yumemi.tart.core

/**
 * Controls how repeated coroutines launched from the same `action{}` handler behave
 * while previous work for the same launch key is still active.
 */
enum class LaunchMode {
    /**
     * Launch every asynchronous handler invocation concurrently.
     */
    CONCURRENT,

    /**
     * Cancel the previous asynchronous job from the same handler before starting a new one.
     */
    CANCEL_PREVIOUS,

    /**
     * Ignore new dispatches while the previous asynchronous job from the same handler is still active.
     */
    DROP_NEW,
}
