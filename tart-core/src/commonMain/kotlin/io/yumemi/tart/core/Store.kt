package io.yumemi.tart.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface of Tart that provides application state management.
 * It has features such as state updates, event emission, action dispatching, etc.
 */
interface Store<S : State, A : Action, E : Event> : AutoCloseable {

    /**
     * StateFlow representing the current state. You can monitor state changes by subscribing to this.
     */
    val state: StateFlow<S>

    /**
     * Flow of events. You can receive events by subscribing to this.
     */
    val event: Flow<E>

    /**
     * The value of the current state. Use this to get the current state without subscribing to the Flow.
     */
    val currentState: S

    /**
     * Dispatches an action. This triggers state changes.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Collects state changes.
     *
     * @param state Callback called when the state changes
     */
    fun collectState(state: (S) -> Unit)

    /**
     * Collects events.
     *
     * @param event Callback called when an event is emitted
     */
    fun collectEvent(event: (E) -> Unit)

    /**
     * Releases the Store's resources.
     */
    fun dispose()

    override fun close() {
        dispose()
    }
}

/**
 * Dispatches an action and suspends until the dispatch work completes.
 *
 * This waits for the action handling performed as part of the dispatch itself.
 * It does not wait for additional work launched from action/enter handlers.
 *
 * This extension is available for Store instances created by Tart DSL.
 *
 * @param action The action to dispatch
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.dispatchAndWait(action: A) {
    requireStoreImpl().dispatchAndWaitInternal(action)
}

/**
 * Attaches an observer before the store is started.
 * This does not start the store.
 *
 * This extension is available for Store instances created by Tart DSL.
 *
 * @param observer The observer to attach
 * @param notifyCurrentState Whether to notify the observer with the current state immediately
 * @throws IllegalStateException if the store is starting or has already started
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
fun <S : State, A : Action, E : Event> Store<S, A, E>.attachObserver(observer: StoreObserver<S, E>, notifyCurrentState: Boolean = true) {
    requireStoreImpl().attachObserverInternal(observer, notifyCurrentState)
}

private fun <S : State, A : Action, E : Event> Store<S, A, E>.requireStoreImpl(): StoreImpl<S, A, E> {
    return this as? StoreImpl<S, A, E>
        ?: throw IllegalStateException("[Tart] This API is only supported for Store instances created by Tart DSL")
}
