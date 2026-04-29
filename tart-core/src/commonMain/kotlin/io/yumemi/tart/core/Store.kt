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
     * Collects state changes using a callback.
     *
     * This API is intended for platforms where [StateFlow] cannot be consumed directly.
     *
     * The callback runs in the Store's execution context.
     * Tart does not switch to a UI thread automatically and does not guarantee delivery
     * on the thread that called [collectState].
     * If your platform requires UI-thread access, move to the appropriate UI thread
     * before touching UI components from the callback.
     *
     * Collection continues until the Store is disposed.
     *
     * @param state Callback called when the state changes
     */
    fun collectState(state: (S) -> Unit)

    /**
     * Collects events using a callback.
     *
     * This API is intended for platforms where [Flow] cannot be consumed directly.
     *
     * The callback runs in the Store's execution context.
     * Tart does not switch to a UI thread automatically and does not guarantee delivery
     * on the thread that called [collectEvent].
     * If your platform requires UI-thread access, move to the appropriate UI thread
     * before touching UI components from the callback.
     *
     * Collection continues until the Store is disposed.
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
