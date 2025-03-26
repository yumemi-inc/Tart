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
    @Deprecated("Use Store() instead", ReplaceWith("Store()"))
    abstract class Base<S : State, A : Action, E : Event>(
        override val initialState: S,
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default,
    ) : TartStore<S, A, E>() {
        override val stateSaver: StateSaver<S> = StateSaver(
            save = {},
            restore = { null },
        )
        override val exceptionHandler: ExceptionHandler = ExceptionHandler {
            it.printStackTrace()
        }
        override val middlewares: List<Middleware<S, A, E>> = emptyList()
        override val onEnter: suspend TartStore<S, A, E>.(S) -> S = { state -> onEnter(state) }
        override val onExit: suspend TartStore<S, A, E>.(S) -> Unit = { onExit(it) }
        override val onDispatch: suspend TartStore<S, A, E>.(S, A) -> S = { state, action -> onDispatch(state, action) }
        override val onError: suspend TartStore<S, A, E>.(S, Throwable) -> S = { state, error -> onError(state, error) }
    }
}

/**
 * Factory function to create a Store instance with customizable behavior.
 *
 * @param initialState The initial state of the store
 * @param coroutineContext The context used for launching coroutines (defaults to EmptyCoroutineContext + Dispatchers.Default)
 * @param stateSaver Implementation for persisting and restoring state (defaults to no-op implementation)
 * @param exceptionHandler Handler for exceptions that occur during store operations (defaults to printStackTrace)
 * @param middlewares List of middlewares to apply to this store (empty by default)
 * @param onEnter Function called when a state is entered (defaults to returning the state unchanged)
 * @param onExit Function called when a state is exited (no-op by default)
 * @param onDispatch Function that handles actions and transforms state (defaults to returning the state unchanged)
 * @param onError Function called when an error occurs (defaults to rethrowing the error)
 * @return A new Store instance configured with the provided parameters
 */
fun <S : State, A : Action, E : Event> Store(
    initialState: S,
    coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default,
    stateSaver: StateSaver<S> = StateSaver(
        save = {},
        restore = { null },
    ),
    exceptionHandler: ExceptionHandler = ExceptionHandler {
        it.printStackTrace()
    },
    middlewares: List<Middleware<S, A, E>> = emptyList(),
    onEnter: suspend TartStore<S, A, E>.(S) -> S = { state -> state },
    onExit: suspend TartStore<S, A, E>.(S) -> Unit = { _ -> },
    onDispatch: suspend TartStore<S, A, E>.(S, A) -> S = { state, _ -> state },
    onError: suspend TartStore<S, A, E>.(S, Throwable) -> S = { _, error -> throw error },
): Store<S, A, E> {
    return object : TartStore<S, A, E>() {
        override val initialState: S = initialState
        override val coroutineContext: CoroutineContext = coroutineContext
        override val stateSaver: StateSaver<S> = stateSaver
        override val exceptionHandler: ExceptionHandler = exceptionHandler
        override val middlewares: List<Middleware<S, A, E>> = middlewares
        override val onEnter: suspend TartStore<S, A, E>.(S) -> S = onEnter
        override val onExit: suspend TartStore<S, A, E>.(S) -> Unit = onExit
        override val onDispatch: suspend TartStore<S, A, E>.(S, A) -> S = onDispatch
        override val onError: suspend TartStore<S, A, E>.(S, Throwable) -> S = onError
    }
}
