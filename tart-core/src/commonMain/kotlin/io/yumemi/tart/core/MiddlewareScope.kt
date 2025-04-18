package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Provides middleware with access to store functionality and coroutine scoping.
 * Allows middlewares to dispatch actions and launch coroutines within the store's lifecycle.
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
     * @param coroutineDispatcher The dispatcher to use for the coroutine
     * @param block The coroutine body to execute
     */
    fun launch(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend CoroutineScope.() -> Unit)
}
