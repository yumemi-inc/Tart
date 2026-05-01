package io.yumemi.tart.test

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.InternalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import io.yumemi.tart.core.StoreInternalApi
import io.yumemi.tart.core.StoreObserver

/**
 * Dispatches an action and suspends until the Store finishes the dispatch work itself.
 *
 * This waits for startup, the matching action handler, and any resulting synchronous state
 * transition work triggered by that dispatch.
 * It does not wait for additional work launched from `enter {}` or `action {}` handlers.
 *
 * This extension is available for Store instances created by the Tart DSL.
 *
 * @param action The action to dispatch
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
@OptIn(InternalTartApi::class)
suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.dispatchAndWait(action: A) {
    requireStoreInternalApi().dispatchAndWait(action)
}

/**
 * Attaches an observer before the Store starts.
 *
 * This does not start the Store.
 * If [notifyCurrentState] is true, the observer receives the current snapshot immediately, which
 * may be a [io.yumemi.tart.core.StateSaver]-restored value.
 *
 * This extension is available for Store instances created by the Tart DSL.
 *
 * @param observer The observer to attach
 * @param notifyCurrentState Whether to notify the observer with the current state immediately
 * @throws IllegalStateException if the store is starting or has already started
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
@OptIn(InternalTartApi::class)
fun <S : State, A : Action, E : Event> Store<S, A, E>.attachObserver(observer: StoreObserver<S, E>, notifyCurrentState: Boolean = true) {
    requireStoreInternalApi().attachObserver(observer, notifyCurrentState)
}

@OptIn(InternalTartApi::class)
private fun <S : State, A : Action, E : Event> Store<S, A, E>.requireStoreInternalApi(): StoreInternalApi<S, A, E> {
    @Suppress("UNCHECKED_CAST")
    return this as? StoreInternalApi<S, A, E>
        ?: throw IllegalStateException("[Tart] This API is only supported for Store instances created by Tart DSL")
}
