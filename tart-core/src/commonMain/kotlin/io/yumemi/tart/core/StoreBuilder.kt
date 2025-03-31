package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
@TartDsl
class StoreBuilder<S : State, A : Action, E : Event> {
    private var _initialState: S? = null
    private var _coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default
    private var _stateSaver: StateSaver<S> = StateSaver.Noop()
    private var _exceptionHandler: ExceptionHandler = ExceptionHandler.Default
    private var _middlewares: MutableList<Middleware<S, A, E>> = mutableListOf()

    val enterStateHandlers = mutableListOf<EnterStateHandler<S, A, E>>()
    val actionStateHandlers = mutableListOf<ActionStateHandler<S, A, E>>()
    val exitStateHandlers = mutableListOf<ExitStateHandler<S, A, E>>()
    val errorStateHandlers = mutableListOf<ErrorStateHandler<S, A, E>>()

    private val onEnter: suspend EnterContext<S, A, E>.() -> S = {
        val matchingHandler = enterStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onAction: suspend ActionContext<S, A, E>.() -> S = {
        val matchingHandler = actionStateHandlers.firstOrNull { it.predicate(state, action) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onExit: suspend ExitContext<S, A, E>.() -> Unit = {
        val matchingHandler = exitStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this)
    }

    private val onError: suspend ErrorContext<S, A, E>.() -> S = {
        val matchingHandler = errorStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: throw error
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

    @TartDsl
    class StateHandlerConfig<S : State, A : Action, E : Event, S2 : S> {
        data class ActionHandler<S : State, A : Action, E : Event>(
            val isTypeOf: (A) -> Boolean,
            val handler: suspend ActionContext<S, A, E>.() -> S,
        )

        val enterHandlers = mutableListOf<(suspend EnterContext<S2, A, E>.() -> S)>()
        val actionHandlers = mutableListOf<ActionHandler<S, A, E>>()
        val exitHandlers = mutableListOf<(suspend ExitContext<S2, A, E>.() -> Unit)>()
        val errorHandlers = mutableListOf<(suspend ErrorContext<S2, A, E>.() -> S)>()

        /**
         * Registers a handler to be invoked when entering this state.
         *
         * @param block The handler function that will be executed when entering this state
         */
        fun enter(block: suspend EnterContext<S2, A, E>.() -> S) {
            enterHandlers.add(block)
        }

        /**
         * Registers a handler for a specific action type in the current state configuration.
         *
         * @param block The handler function that processes the action and returns a new state
         */
        inline fun <reified A2 : A> action(noinline block: suspend ActionContext<S2, A2, E>.() -> S) {
            actionHandlers.add(
                ActionHandler(
                    isTypeOf = { it is A2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        block(this as ActionContext<S2, A2, E>)
                    },
                ),
            )
        }

        /**
         * Registers a handler to be invoked when exiting this state.
         *
         * @param block The handler function that will be executed when exiting this state
         */
        fun exit(block: suspend ExitContext<S2, A, E>.() -> Unit) {
            exitHandlers.add(block)
        }

        /**
         * Registers a handler for errors that occur when in this state.
         *
         * @param block The handler function that will be executed when an error occurs in this state
         */
        fun error(block: suspend ErrorContext<S2, A, E>.() -> S) {
            errorHandlers.add(block)
        }
    }

    /**
     * Configures handlers for actions when the store is in a specific state type.
     * This creates a DSL scope for defining type-specific action handlers.
     *
     * @param block The configuration block where you can define action handlers using the action() function
     */
    inline fun <reified S2 : S> state(noinline block: StateHandlerConfig<S, A, E, S2>.() -> Unit) {
        val config = StateHandlerConfig<S, A, E, S2>().apply(block)

        for (enterHandler in config.enterHandlers) {
            enterStateHandlers.add(
                EnterStateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        enterHandler(this as EnterContext<S2, A, E>)
                    },
                ),
            )
        }

        for (actionHandler in config.actionHandlers) {
            actionStateHandlers.add(
                ActionStateHandler(
                    predicate = { state, action ->
                        state is S2 && actionHandler.isTypeOf(action)
                    },
                    handler = actionHandler.handler,
                ),
            )
        }

        for (exitHandler in config.exitHandlers) {
            exitStateHandlers.add(
                ExitStateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        exitHandler(this as ExitContext<S2, A, E>)
                    },
                ),
            )
        }

        for (errorHandler in config.errorHandlers) {
            errorStateHandlers.add(
                ErrorStateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        errorHandler(this as ErrorContext<S2, A, E>)
                    },
                ),
            )
        }
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(_initialState) { "Tart: InitialState must be set in Store configuration" }

        return object : StoreImpl<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = _coroutineContext
            override val stateSaver: StateSaver<S> = _stateSaver
            override val exceptionHandler: ExceptionHandler = _exceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = _middlewares
            override val onEnter: suspend EnterContext<S, A, E>.() -> S = this@StoreBuilder.onEnter
            override val onAction: suspend ActionContext<S, A, E>.() -> S = this@StoreBuilder.onAction
            override val onExit: suspend ExitContext<S, A, E>.() -> Unit = this@StoreBuilder.onExit
            override val onError: suspend ErrorContext<S, A, E>.() -> S = this@StoreBuilder.onError
        }
    }

    class EnterStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend EnterContext<S, A, E>.() -> S,
    )

    class ActionStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S, A) -> Boolean,
        val handler: suspend ActionContext<S, A, E>.() -> S,
    )

    class ExitStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend ExitContext<S, A, E>.() -> Unit,
    )

    class ErrorStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend ErrorContext<S, A, E>.() -> S,
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
