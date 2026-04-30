package io.yumemi.tart.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface of Tart that provides application state management.
 * It has features such as state updates, event emission, action dispatching, etc.
 *
 * Store startup is lazy.
 * Startup processing begins when the first action is [dispatch]ed, when [state] is collected,
 * or when [collectState] is called.
 * Collecting [event] or calling [collectEvent] alone does not start the Store.
 */
interface Store<S : State, A : Action, E : Event> : AutoCloseable {

    /**
     * StateFlow representing the current state. You can monitor state changes by subscribing to this.
     *
     * Collecting this flow starts the Store if it has not started yet.
     */
    val state: StateFlow<S>

    /**
     * Flow of events. You can receive events by subscribing to this.
     *
     * Collecting this flow does not start the Store by itself.
     */
    val event: Flow<E>

    /**
     * The value of the current state. Use this to get the current state without subscribing to the Flow.
     *
     * Reading this property does not start the Store.
     * If a [StateSaver] restores a saved state, this may return that restored snapshot
     * even before startup processing begins.
     */
    val currentState: S

    /**
     * Dispatches an action.
     *
     * This enqueues the action and returns immediately.
     * It does not wait for action handling to complete.
     * If the Store has not started yet, this also triggers startup processing before the action runs.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Collects state changes using a callback.
     *
     * This API is intended for platforms where [StateFlow] cannot be consumed directly.
     * Calling this method starts the Store if it has not started yet.
     *
     * The callback runs in the Store's execution context.
     * Tart does not switch to a UI thread automatically and does not guarantee delivery
     * on the thread that called [collectState].
     * If your platform requires UI-thread access, move to the appropriate UI thread
     * before touching UI components from the callback.
     *
     * Collection continues until the Store is closed.
     *
     * @param state Callback called when the state changes
     */
    fun collectState(state: (S) -> Unit)

    /**
     * Collects events using a callback.
     *
     * This API is intended for platforms where [Flow] cannot be consumed directly.
     * Calling this method does not start the Store by itself.
     * If you need startup processing to run, also trigger startup through [dispatch], [state],
     * or [collectState].
     *
     * The callback runs in the Store's execution context.
     * Tart does not switch to a UI thread automatically and does not guarantee delivery
     * on the thread that called [collectEvent].
     * If your platform requires UI-thread access, move to the appropriate UI thread
     * before touching UI components from the callback.
     *
     * Collection continues until the Store is closed.
     *
     * @param event Callback called when an event is emitted
     */
    fun collectEvent(event: (E) -> Unit)

    /**
     * Releases the Store's resources.
     */
    override fun close()

    /**
     * Releases the Store's resources.
     *
     * Kept for backward compatibility. Use [close] instead.
     */
    @Deprecated(message = "Use close()", replaceWith = ReplaceWith("close()"))
    fun dispose() {
        close()
    }
}
