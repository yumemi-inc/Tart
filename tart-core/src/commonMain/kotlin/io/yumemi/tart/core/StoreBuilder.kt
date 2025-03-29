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

    val enterStateHandlers = mutableListOf<EnterStateHandler<S, A, E>>()
    val exitStateHandlers = mutableListOf<ExitStateHandler<S, A, E>>()
    val dispatchStateHandlers = mutableListOf<DispatchStateHandler<S, A, E>>()
    val errorStateHandlers = mutableListOf<ErrorStateHandler<S, A, E>>()

    private val onEnter: suspend StoreContext<S, A, E>.(S) -> S = { state ->
        val matchingHandler = enterStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this, state) ?: state
    }

    private val onExit: suspend StoreContext<S, A, E>.(S) -> Unit = { state ->
        val matchingHandler = exitStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this, state)
    }

    private val onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = { state, action ->
        val matchingHandler = dispatchStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this, state, action) ?: state
    }

    private val onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = { state, error ->
        val matchingHandler = errorStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this, state, error) ?: throw error
    }

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

    inline fun <reified S2 : S> onEnter(noinline block: suspend StoreContext<S, A, E>.(S2) -> S) {
        enterStateHandlers.add(
            EnterStateHandler(
                predicate = { it is S2 },
                handler = { state ->
                    if (state is S2) {
                        block(state)
                    } else {
                        state
                    }
                },
            ),
        )
    }

    inline fun <reified S2 : S> onExit(noinline block: suspend StoreContext<S, A, E>.(S2) -> Unit) {
        exitStateHandlers.add(
            ExitStateHandler(
                predicate = { it is S2 },
                handler = { state ->
                    if (state is S2) {
                        block(state)
                    }
                },
            ),
        )
    }

    inline fun <reified S2 : S> onDispatch(noinline block: suspend StoreContext<S, A, E>.(S2, A) -> S) {
        dispatchStateHandlers.add(
            DispatchStateHandler(
                predicate = { it is S2 },
                handler = { state, action ->
                    if (state is S2) {
                        block(state, action)
                    } else {
                        state
                    }
                },
            ),
        )
    }

    inline fun <reified S2 : S> onError(noinline block: suspend StoreContext<S, A, E>.(S2, Throwable) -> S) {
        errorStateHandlers.add(
            ErrorStateHandler(
                predicate = { it is S2 },
                handler = { state, error ->
                    if (state is S2) {
                        block(state, error)
                    } else {
                        state
                    }
                },
            ),
        )
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(_initialState) { "Tart: InitialState must be set in Store configuration" }

        return object : StoreImpl<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = _coroutineContext
            override val stateSaver: StateSaver<S> = _stateSaver
            override val exceptionHandler: ExceptionHandler = _exceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = _middlewares
            override val onEnter: suspend StoreContext<S, A, E>.(S) -> S = this@StoreBuilder.onEnter
            override val onExit: suspend StoreContext<S, A, E>.(S) -> Unit = this@StoreBuilder.onExit
            override val onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = this@StoreBuilder.onDispatch
            override val onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = this@StoreBuilder.onError
        }
    }

    class EnterStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend StoreContext<S, A, E>.(S) -> S,
    )

    class ExitStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend StoreContext<S, A, E>.(S) -> Unit,
    )

    class DispatchStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend StoreContext<S, A, E>.(S, A) -> S,
    )

    class ErrorStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend StoreContext<S, A, E>.(S, Throwable) -> S,
    )
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
