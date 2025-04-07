package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
@TartStoreDsl
class StoreBuilder<S : State, A : Action, E : Event> internal constructor() {
    private var _initialState: S? = null
    private var _coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default
    private var _stateSaver: StateSaver<S> = StateSaver.Noop()
    private var _exceptionHandler: ExceptionHandler = ExceptionHandler.Default
    private var _middlewares: MutableList<Middleware<S, A, E>> = mutableListOf()

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
     * Adds a single middleware instance to the store.
     *
     * @param middleware The middleware instance to add
     */
    fun middleware(middleware: Middleware<S, A, E>) {
        _middlewares.add(middleware)
    }

    /**
     * Adds multiple middleware instances to the store.
     *
     * @param middleware Array of middleware instances to add
     */
    fun middlewares(vararg middleware: Middleware<S, A, E>) {
        _middlewares.addAll(middleware)
    }

    class EnterStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend EnterScope<S, A, E, S>.() -> Unit,
    )

    class ActionStateHandler<S : State, A : Action, E : Event>(
        val predicate: (S, A) -> Boolean,
        val handler: suspend ActionScope<S, A, E, S>.() -> Unit,
    )

    class ExitStateHandler<S : State, E : Event>(
        val predicate: (S) -> Boolean,
        val handler: suspend ExitScope<S, E>.() -> Unit,
    )

    class ErrorStateHandler<S : State, E : Event, S0 : State>(
        val predicate: (S) -> Boolean,
        val handler: suspend ErrorScope<S, E, S0>.() -> Unit,
    )

    val enterStateHandlers = mutableListOf<EnterStateHandler<S, A, E>>()
    val actionStateHandlers = mutableListOf<ActionStateHandler<S, A, E>>()
    val exitStateHandlers = mutableListOf<ExitStateHandler<S, E>>()
    val errorStateHandlers = mutableListOf<ErrorStateHandler<S, E, S>>()

    private val onEnter: suspend EnterScope<S, A, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.enterStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.actionStateHandlers.firstOrNull { it.predicate(state, action) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onExit: suspend ExitScope<S, E>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.exitStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this)
    }

    private val onError: suspend ErrorScope<S, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.errorStateHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: throw error
    }

    class ContextualEnterHandler<S : State, A : Action, E : Event, S0 : State> internal constructor(
        private val coroutineDispatcher: CoroutineDispatcher,
        private val handler: suspend EnterScope<S, A, E, S0>.() -> Unit,
    ) {
        suspend operator fun invoke(scope: EnterScope<S, A, E, S0>) {
            withContext(coroutineDispatcher) {
                handler(scope)
            }
        }
    }

    class ContextualActionHandler<S : State, A : Action, E : Event, S0 : State>(
        private val coroutineDispatcher: CoroutineDispatcher,
        val predicate: (A) -> Boolean,
        private val handler: suspend ActionScope<S, A, E, S0>.() -> Unit,
    ) {
        suspend operator fun invoke(scope: ActionScope<S, A, E, S0>) {
            withContext(coroutineDispatcher) {
                handler(scope)
            }
        }
    }

    class ContextualExitHandler<S : State, E : Event> internal constructor(
        private val coroutineDispatcher: CoroutineDispatcher,
        private val handler: suspend ExitScope<S, E>.() -> Unit,
    ) {
        suspend operator fun invoke(scope: ExitScope<S, E>) {
            withContext(coroutineDispatcher) {
                handler(scope)
            }
        }
    }

    class ContextualErrorHandler<S : State, E : Event, S0 : State> internal constructor(
        private val coroutineDispatcher: CoroutineDispatcher,
        private val handler: suspend ErrorScope<S, E, S0>.() -> Unit,
    ) {
        suspend operator fun invoke(scope: ErrorScope<S, E, S0>) {
            withContext(coroutineDispatcher) {
                handler(scope)
            }
        }
    }

    @TartStoreDsl
    class StateHandlerConfig<S : State, A : Action, E : Event, S2 : S> {
        val enterHandlers = mutableListOf<ContextualEnterHandler<S2, A, E, S>>()
        val actionHandlers = mutableListOf<ContextualActionHandler<S, A, E, S>>()
        val exitHandlers = mutableListOf<ContextualExitHandler<S2, E>>()
        val errorHandlers = mutableListOf<ContextualErrorHandler<S2, E, S>>()

        /**
         * Registers a handler to be invoked when entering this state with the specified CoroutineDispatcher.
         * If no coroutineDispatcher is provided, Dispatchers.Unconfined is used as default.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the enter handler (defaults to Dispatchers.Unconfined)
         * @param block The handler function that will be executed when entering this state
         */
        fun enter(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend EnterScope<S2, A, E, S>.() -> Unit) {
            enterHandlers.add(ContextualEnterHandler(coroutineDispatcher, block))
        }

        /**
         * Registers a handler for a specific action type in the current state configuration
         * with an optional CoroutineDispatcher.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the action handler, defaults to Dispatchers.Unconfined
         * @param block The handler function that processes the action and updates the state
         */
        inline fun <reified A2 : A> action(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, noinline block: suspend ActionScope<S2, A2, E, S>.() -> Unit) {
            actionHandlers.add(
                ContextualActionHandler(
                    coroutineDispatcher = coroutineDispatcher,
                    predicate = { it is A2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        block(this as ActionScope<S2, A2, E, S>)
                    },
                ),
            )
        }

        /**
         * Registers a handler to be invoked when exiting this state with the specified CoroutineDispatcher.
         * If no coroutineDispatcher is provided, Dispatchers.Unconfined is used as default.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the exit handler (defaults to Dispatchers.Unconfined)
         * @param block The handler function that will be executed when exiting this state
         */
        fun exit(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend ExitScope<S2, E>.() -> Unit) {
            exitHandlers.add(ContextualExitHandler(coroutineDispatcher, block))
        }

        /**
         * Registers a handler for errors that occur when in this state with the specified CoroutineDispatcher.
         * If no coroutineDispatcher is provided, Dispatchers.Unconfined is used as default.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the error handler (defaults to Dispatchers.Unconfined)
         * @param block The handler function that will be executed when an error occurs in this state
         */
        fun error(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend ErrorScope<S2, E, S>.() -> Unit) {
            errorHandlers.add(ContextualErrorHandler(coroutineDispatcher, block))
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
                        enterHandler.invoke(this as EnterScope<S2, A, E, S>)
                    },
                ),
            )
        }

        for (actionHandler in config.actionHandlers) {
            actionStateHandlers.add(
                ActionStateHandler(
                    predicate = { state, action -> state is S2 && actionHandler.predicate(action) },
                    handler = {
                        actionHandler.invoke(this)
                    },
                ),
            )
        }

        for (exitHandler in config.exitHandlers) {
            exitStateHandlers.add(
                ExitStateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        exitHandler.invoke(this as ExitScope<S2, E>)
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
                        errorHandler.invoke(this as ErrorScope<S2, E, S>)
                    },
                ),
            )
        }
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(_initialState) { "[Tart] InitialState must be set in Store{} DSL" }

        return object : StoreImpl<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = _coroutineContext
            override val stateSaver: StateSaver<S> = _stateSaver
            override val exceptionHandler: ExceptionHandler = _exceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = _middlewares
            override val onEnter: suspend EnterScope<S, A, E, S>.() -> Unit = this@StoreBuilder.onEnter
            override val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = this@StoreBuilder.onAction
            override val onExit: suspend ExitScope<S, E>.() -> Unit = this@StoreBuilder.onExit
            override val onError: suspend ErrorScope<S, E, S>.() -> Unit = this@StoreBuilder.onError
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
