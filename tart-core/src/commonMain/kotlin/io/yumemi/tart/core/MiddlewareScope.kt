package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Scope exposed to [Middleware] hooks.
 *
 * Middleware can use this scope to dispatch additional actions or start Store-scoped background
 * work.
 */
interface MiddlewareScope<A : Action> {
    /**
     * Dispatches an action to the Store.
     *
     * This enqueues the action and returns immediately.
     * It does not wait for action handling to complete.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Starts background work in the Store's root coroutine scope and returns immediately.
     *
     * The launched coroutine survives state changes and is cancelled when the Store's root
     * coroutine scope is cancelled, such as by [Store.close] or parent scope cancellation.
     *
     * @param dispatcher Optional CoroutineDispatcher override for the coroutine.
     * When null, the coroutine inherits the Store's current execution context.
     * @param block The coroutine body to execute
     */
    fun launch(dispatcher: CoroutineDispatcher? = null, block: suspend CoroutineScope.() -> Unit)
}
