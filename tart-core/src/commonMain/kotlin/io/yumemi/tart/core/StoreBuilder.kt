package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Builder class for configuring and creating a [Store] instance.
 *
 * This builder provides methods to configure various aspects of a store including:
 * - Initial state
 * - CoroutineContext for execution
 * - State saving mechanism
 * - Exception handling
 * - Middleware chain
 * - State transition handlers
 */
@Suppress("unused")
class StoreBuilder<S : State, A : Action, E : Event> {
    private var _initialState: S? = null
    private var _coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default
    private var _stateSaver: StateSaver<S> = StateSaver.Noop()
    private var _exceptionHandler: ExceptionHandler = ExceptionHandler.Default
    private var _middlewares: MutableList<Middleware<S, A, E>> = mutableListOf()
    private var _onEnter: suspend StoreContext<S, A, E>.(S) -> S = { it }
    private var _onExit: suspend StoreContext<S, A, E>.(S) -> Unit = { }
    private var _onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = { state, _ -> state }
    private var _onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = { _, error -> throw error }

    /**
     * Sets the initial state of the store.
     *
     * @param state The initial state to set
     */
    fun initialState(state: S) {
        _initialState = state
    }

    /**
     * Sets the coroutine context for store operations.
     *
     * @param coroutineContext The coroutine context to use
     */
    fun coroutineContext(coroutineContext: CoroutineContext) {
        _coroutineContext = coroutineContext
    }

    /**
     * Sets the state saver implementation for persisting state.
     *
     * @param stateSaver The state saver implementation to use
     */
    fun stateSaver(stateSaver: StateSaver<S>) {
        _stateSaver = stateSaver
    }

    /**
     * Sets the exception handler for error handling.
     *
     * @param exceptionHandler The exception handler to use
     */
    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        _exceptionHandler = exceptionHandler
    }

    /**
     * Adds multiple middleware instances to the store.
     *
     * @param middleware Array of middleware instances to add
     */
    fun middlewares(vararg middleware: Middleware<S, A, E>) {
        _middlewares.addAll(middleware)
    }

    /**
     * Adds a single middleware instance to the store.
     *
     * @param middleware The middleware instance to add
     */
    fun middleware(middleware: Middleware<S, A, E>) {
        _middlewares.add(middleware)
    }

    /**
     * Sets the handler for state entry logic.
     * This is called when a state is entered.
     *
     * @param block The handler for state entry
     */
    fun onEnter(block: suspend StoreContext<S, A, E>.(S) -> S) {
        _onEnter = block
    }

    /**
     * Sets the handler for state exit logic.
     * This is called when a state is exited.
     *
     * @param block The handler for state exit
     */
    fun onExit(block: suspend StoreContext<S, A, E>.(S) -> Unit) {
        _onExit = block
    }

    /**
     * Sets the handler for action dispatch logic.
     * This is called when an action is dispatched to the store.
     *
     * @param block The handler for action dispatch
     */
    fun onDispatch(block: suspend StoreContext<S, A, E>.(S, A) -> S) {
        _onDispatch = block
    }

    /**
     * Sets the handler for error handling logic.
     * This is called when an error occurs during store operations.
     *
     * @param block The handler for error handling
     */
    fun onError(block: suspend StoreContext<S, A, E>.(S, Throwable) -> S) {
        _onError = block
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(_initialState) { "Tart: InitialState must be set in Store configuration" }

        return object : StoreImpl<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = _coroutineContext
            override val stateSaver: StateSaver<S> = _stateSaver
            override val exceptionHandler: ExceptionHandler = _exceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = _middlewares
            override val onEnter: suspend StoreContext<S, A, E>.(S) -> S = _onEnter
            override val onExit: suspend StoreContext<S, A, E>.(S) -> Unit = _onExit
            override val onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = _onDispatch
            override val onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = _onError
        }
    }
}

/**
 * Creates a Store instance with the specified initial state and optional configuration.
 *
 * @param initialState The initial state of the store
 * @param block Optional configuration block to customize the store
 * @return A configured Store instance
 */
fun <S : State, A : Action, E : Event> Store(
    initialState: S,
    block: StoreBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    return StoreBuilder<S, A, E>().apply {
        initialState(initialState)
        block()
    }.build()
}

/**
 * Creates a Store instance with configuration provided in the block.
 * The initial state must be set within the block using initialState().
 *
 * @param block Configuration block to customize the store
 * @return A configured Store instance
 * @throws IllegalArgumentException if the initial state is not set in the block
 */
fun <S : State, A : Action, E : Event> Store(
    block: StoreBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    return StoreBuilder<S, A, E>().apply(block).build()
}
