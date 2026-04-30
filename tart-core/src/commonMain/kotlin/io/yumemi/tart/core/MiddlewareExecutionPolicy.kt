package io.yumemi.tart.core

/**
 * Controls how the Store invokes middleware when multiple middleware instances are registered.
 */
enum class MiddlewareExecutionPolicy {
    /**
     * Invokes middleware methods concurrently and waits until all of them complete.
     */
    Concurrent,

    /**
     * Invokes middleware methods one by one in registration order.
     */
    InRegistrationOrder,
}
