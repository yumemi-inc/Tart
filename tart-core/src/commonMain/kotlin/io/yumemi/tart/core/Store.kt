package io.yumemi.tart.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface of Tart that provides application state management.
 * It has features such as state updates, event emission, action dispatching, etc.
 */
interface Store<S : State, A : Action, E : Event>: AutoCloseable {

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

    override fun close() { dispose() }
}
