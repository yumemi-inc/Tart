package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Scope available to middleware components for processing actions.
 * Provides capabilities for action dispatching and coroutine launching.
 */
interface MiddlewareScope<A : Action> {
    /**
     * Dispatches an action to the store.
     * This enqueues the action and returns immediately.
     * It does not wait for action handling to complete.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Launches a coroutine within the store's scope.
     * The launched coroutine is tied to the Store lifecycle and is cancelled when the Store is closed.
     * It is not cancelled automatically when the current state changes.
     *
     * @param dispatcher Optional CoroutineDispatcher override for the coroutine.
     * When null, the coroutine inherits the Store's current execution context.
     * @param block The coroutine body to execute
     */
    fun launch(dispatcher: CoroutineDispatcher? = null, block: suspend CoroutineScope.() -> Unit)
}
