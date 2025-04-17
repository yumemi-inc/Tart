package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
@TartStoreDsl
class StoreBuilder<S : State, A : Action, E : Event> internal constructor() {
    private var storeInitialState: S? = null
    private var storeCoroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default
    private var storeStateSaver: StateSaver<S> = StateSaver.Noop()
    private var storeExceptionHandler: ExceptionHandler = ExceptionHandler.Default
    private var storeMiddlewares: MutableList<Middleware<S, A, E>> = mutableListOf()

    /**
     * Sets the initial state of the store.
     *
     * @param state The initial state to set
     */
    fun initialState(state: S) {
        storeInitialState = state
    }

    /**
     * Sets the coroutine context for store operations.
     *
     * @param coroutineContext The coroutine context to use
     */
    fun coroutineContext(coroutineContext: CoroutineContext) {
        storeCoroutineContext = coroutineContext
    }

    /**
     * Sets the state saver implementation for persisting state.
     *
     * @param stateSaver The state saver implementation to use
     */
    fun stateSaver(stateSaver: StateSaver<S>) {
        storeStateSaver = stateSaver
    }

    /**
     * Sets the exception handler for error handling.
     *
     * @param exceptionHandler The exception handler to use
     */
    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        storeExceptionHandler = exceptionHandler
    }

    /**
     * Adds a single middleware instance to the store.
     *
     * @param middleware The middleware instance to add
     */
    fun middleware(middleware: Middleware<S, A, E>) {
        storeMiddlewares.add(middleware)
    }

    /**
     * Adds multiple middleware instances to the store.
     *
     * @param middleware Array of middleware instances to add
     */
    fun middlewares(vararg middleware: Middleware<S, A, E>) {
        storeMiddlewares.addAll(middleware)
    }

    class StateHandler<P, SS : StoreScope>(
        val predicate: P,
        val handler: suspend SS.() -> Unit,
    )

    val registeredEnterHandlers = mutableListOf<StateHandler<(S) -> Boolean, EnterScope<S, A, E, S>>>()
    val registeredActionHandlers = mutableListOf<StateHandler<(S, A) -> Boolean, ActionScope<S, A, E, S>>>()
    val registeredExitHandlers = mutableListOf<StateHandler<(S) -> Boolean, ExitScope<S, E>>>()
    val registeredErrorHandlers = mutableListOf<StateHandler<(S, Throwable) -> Boolean, ErrorScope<S, E, S, Throwable>>>()

    private val onEnter: suspend EnterScope<S, A, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredEnterHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredActionHandlers.firstOrNull { it.predicate(state, action) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onExit: suspend ExitScope<S, E>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredExitHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this)
    }

    private val onError: suspend ErrorScope<S, E, S, Throwable>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredErrorHandlers.firstOrNull { it.predicate(state, error) }
        matchingHandler?.handler?.invoke(this) ?: throw error
    }

    @TartStoreDsl
    class StateHandlerConfig<S : State, A : Action, E : Event, S2 : S> {
        class ThreadedEnterHandler<S : State, A : Action, E : Event, S2 : S> internal constructor(
            private val coroutineDispatcher: CoroutineDispatcher,
            private val handler: suspend EnterScope<S, A, E, S2>.() -> Unit,
        ) {
            suspend operator fun invoke(scope: EnterScope<S, A, E, S2>) {
                withContext(coroutineDispatcher) {
                    handler(scope)
                }
            }
        }

        class ThreadedActionHandler<S : State, A : Action, E : Event, S2 : S>(
            private val coroutineDispatcher: CoroutineDispatcher,
            val predicate: (A) -> Boolean,
            private val handler: suspend ActionScope<S, A, E, S2>.() -> Unit,
        ) {
            suspend operator fun invoke(scope: ActionScope<S, A, E, S2>) {
                withContext(coroutineDispatcher) {
                    handler(scope)
                }
            }
        }

        class ThreadedExitHandler<S : State, E : Event> internal constructor(
            private val coroutineDispatcher: CoroutineDispatcher,
            private val handler: suspend ExitScope<S, E>.() -> Unit,
        ) {
            suspend operator fun invoke(scope: ExitScope<S, E>) {
                withContext(coroutineDispatcher) {
                    handler(scope)
                }
            }
        }

        class ThreadedErrorHandler<S : State, E : Event, S2 : S>(
            private val coroutineDispatcher: CoroutineDispatcher,
            val predicate: (Throwable) -> Boolean,
            private val handler: suspend ErrorScope<S, E, S2, Throwable>.() -> Unit,
        ) {
            suspend operator fun invoke(scope: ErrorScope<S, E, S2, Throwable>) {
                withContext(coroutineDispatcher) {
                    handler(scope)
                }
            }
        }

        val stateEnterHandlers = mutableListOf<ThreadedEnterHandler<S, A, E, S2>>()
        val stateActionHandlers = mutableListOf<ThreadedActionHandler<S, A, E, S2>>()
        val stateExitHandlers = mutableListOf<ThreadedExitHandler<S2, E>>()
        val stateErrorHandlers = mutableListOf<ThreadedErrorHandler<S, E, S2>>()

        /**
         * Registers a handler to be invoked when entering this state with the specified CoroutineDispatcher.
         * If no coroutineDispatcher is provided, Dispatchers.Unconfined is used as default.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the enter handler (defaults to Dispatchers.Unconfined)
         * @param block The handler function that will be executed when entering this state
         */
        fun enter(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend EnterScope<S, A, E, S2>.() -> Unit) {
            stateEnterHandlers.add(ThreadedEnterHandler(coroutineDispatcher, block))
        }

        /**
         * Registers a handler for a specific action type in the current state configuration
         * with an optional CoroutineDispatcher.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the action handler, defaults to Dispatchers.Unconfined
         * @param block The handler function that processes the action and updates the state
         */
        inline fun <reified A2 : A> action(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, noinline block: suspend ActionScope<S, A2, E, S2>.() -> Unit) {
            stateActionHandlers.add(
                ThreadedActionHandler(
                    coroutineDispatcher = coroutineDispatcher,
                    predicate = { it is A2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        block(this as ActionScope<S, A2, E, S2>)
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
            stateExitHandlers.add(ThreadedExitHandler(coroutineDispatcher, block))
        }

        /**
         * Registers a handler for a specific error type in the current state configuration
         * with an optional CoroutineDispatcher.
         *
         * @param coroutineDispatcher The CoroutineDispatcher to use for executing the error handler, defaults to Dispatchers.Unconfined
         * @param block The handler function that processes the error and updates the state
         */
        inline fun <reified T : Throwable> error(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, noinline block: suspend ErrorScope<S, E, S2, T>.() -> Unit) {
            stateErrorHandlers.add(
                ThreadedErrorHandler(
                    coroutineDispatcher = coroutineDispatcher,
                    predicate = { it is T },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        block(this as ErrorScope<S, E, S2, T>)
                    },
                ),
            )
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

        for (enterHandler in config.stateEnterHandlers) {
            registeredEnterHandlers.add(
                StateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        enterHandler.invoke(this as EnterScope<S, A, E, S2>)
                    },
                ),
            )
        }

        for (actionHandler in config.stateActionHandlers) {
            registeredActionHandlers.add(
                StateHandler(
                    predicate = { state, action -> state is S2 && actionHandler.predicate(action) },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        actionHandler.invoke(this as ActionScope<S, A, E, S2>)
                    },
                ),
            )
        }

        for (exitHandler in config.stateExitHandlers) {
            registeredExitHandlers.add(
                StateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        exitHandler.invoke(this as ExitScope<S2, E>)
                    },
                ),
            )
        }

        for (errorHandler in config.stateErrorHandlers) {
            registeredErrorHandlers.add(
                StateHandler(
                    predicate = { state, throwable -> state is S2 && errorHandler.predicate(throwable) },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        errorHandler.invoke(this as ErrorScope<S, E, S2, Throwable>)
                    },
                ),
            )
        }
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(storeInitialState) { "[Tart] InitialState must be set in Store{} DSL" }

        return object : StoreImpl<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = storeCoroutineContext
            override val stateSaver: StateSaver<S> = storeStateSaver
            override val exceptionHandler: ExceptionHandler = storeExceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = storeMiddlewares
            override val onEnter: suspend EnterScope<S, A, E, S>.() -> Unit = this@StoreBuilder.onEnter
            override val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = this@StoreBuilder.onAction
            override val onExit: suspend ExitScope<S, E>.() -> Unit = this@StoreBuilder.onExit
            override val onError: suspend ErrorScope<S, E, S, Throwable>.() -> Unit = this@StoreBuilder.onError
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
