package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Core interface of Tart that provides application state management.
 * It has features such as state updates, event emission, action dispatching, etc.
 */
interface Store<S : State, A : Action, E : Event> {

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
     * @param skipInitialState Whether to skip the initial state
     * @param startStore Whether to start the Store with this call
     * @param state Callback called when the state changes
     */
    fun collectState(skipInitialState: Boolean = false, startStore: Boolean = true, state: (S) -> Unit)

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

    /**
     * Basic implementation class of Store.
     * Provides default StateSaver and ExceptionHandler.
     *
     * @param initialState Initial state
     * @param coroutineContext CoroutineContext to use
     */
    abstract class Base<S : State, A : Action, E : Event>(
        initialState: S,
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default,
    ) : TartStore<S, A, E>(initialState) {
        override val stateSaver: StateSaver<S> = StateSaver(
            save = {},
            restore = { null },
        )

        override val exceptionHandler: ExceptionHandler = ExceptionHandler {
            it.printStackTrace()
        }
    }
}
