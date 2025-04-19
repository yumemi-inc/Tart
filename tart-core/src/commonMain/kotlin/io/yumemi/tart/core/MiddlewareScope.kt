package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Scope available to middleware components for processing actions and events.
 * Provides capabilities for action dispatching, event emission, and coroutine launching.
 */
interface MiddlewareScope<A : Action, E : Event> {
    /**
     * Dispatches an action to the store.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Emits an event from the middleware.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)

    /**
     * Launches a coroutine within the store's scope.
     *
     * @param coroutineDispatcher The dispatcher to use for the coroutine (defaults to Dispatchers.Unconfined)
     * @param block The coroutine body to execute
     */
    fun launch(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend CoroutineScope.() -> Unit)
}
