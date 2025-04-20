package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Scope available to middleware components for processing actions.
 * Provides capabilities for action dispatching and coroutine launching.
 */
interface MiddlewareScope<A : Action> {
    /**
     * Dispatches an action to the store.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Launches a coroutine within the store's scope.
     *
     * @param coroutineDispatcher The dispatcher to use for the coroutine (defaults to Dispatchers.Unconfined)
     * @param block The coroutine body to execute
     */
    fun launch(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend CoroutineScope.() -> Unit)
}
