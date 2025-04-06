package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

/**
 * Context available in middleware components.
 * Provides access to dispatch and coroutine context for middleware operations.
 */
interface MiddlewareContext<S : State, A : Action, E : Event> {
    /**
     * Dispatches an action from middleware.
     * Use this to trigger new state transitions from within middleware.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * The coroutine context for executing middleware operations
     */
    val coroutineContext: CoroutineContext
}
