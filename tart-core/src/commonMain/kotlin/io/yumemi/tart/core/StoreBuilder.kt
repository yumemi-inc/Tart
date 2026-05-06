package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Builder used by the Store DSL to configure runtime behavior and register state-specific
 * handlers.
 *
 * Most users interact with this type through the top-level `Store(...)` factory overloads.
 */
@Suppress("unused")
@TartStoreDsl
class StoreBuilder<S : State, A : Action, E : Event> internal constructor() {
    private var storeInitialState: S? = null
    private var storeCoroutineContext: CoroutineContext = Dispatchers.Default
    private var storeStateSaver: StateSaver<S> = StateSaver.Noop()
    private var storeExceptionHandler: ExceptionHandler = ExceptionHandler.Unhandled
    private var storePendingActionPolicy: PendingActionPolicy = PendingActionPolicy.ClearOnStateExit
    private var storePluginExecutionPolicy: PluginExecutionPolicy = PluginExecutionPolicy.Concurrent
    private var storePlugins: MutableList<Plugin<S, A, E>> = mutableListOf()

    /**
     * Sets the declared initial state.
     *
     * This value is used when no [StateSaver] restores a saved snapshot.
     * Later calls overwrite earlier ones.
     *
     * @param state The initial state to set
     */
    fun initialState(state: S) {
        storeInitialState = state
    }

    /**
     * Sets the root coroutine context used by Store processing.
     *
     * The default is [Dispatchers.Default].
     *
     * @param coroutineContext The coroutine context to use
     */
    fun coroutineContext(coroutineContext: CoroutineContext) {
        storeCoroutineContext = coroutineContext
    }

    /**
     * Sets the saver used to restore a snapshot before startup and persist committed state changes.
     *
     * @param stateSaver The state saver implementation to use
     */
    fun stateSaver(stateSaver: StateSaver<S>) {
        storeStateSaver = stateSaver
    }

    /**
     * Sets the handler for non-fatal exceptions raised during Store processing.
     *
     * @param exceptionHandler The exception handler to use
     */
    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        storeExceptionHandler = exceptionHandler
    }

    /**
     * Sets how queued actions are treated when a committed state change leaves the current state
     * variant.
     *
     * @param policy The pending action policy to use
     */
    fun pendingActionPolicy(policy: PendingActionPolicy) {
        storePendingActionPolicy = policy
    }

    /**
     * Sets how the Store invokes plugins when multiple plugin instances are registered.
     *
     * Regardless of policy, the Store waits for all matching plugin hooks to complete before it
     * continues processing.
     *
     * @param policy The plugin execution policy to use
     */
    fun pluginExecutionPolicy(policy: PluginExecutionPolicy) {
        storePluginExecutionPolicy = policy
    }

    /**
     * Appends one or more plugins to the Store.
     *
     * @param first The first plugin instance to add
     * @param rest Additional plugin instances to add
     */
    fun plugin(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        storePlugins.add(first)
        storePlugins.addAll(rest)
    }

    internal fun clearPlugins() {
        storePlugins.clear()
    }

    internal fun replacePlugins(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        clearPlugins()
        storePlugins.add(first)
        storePlugins.addAll(rest)
    }

    class StateHandler<P, SC : StoreScope>(
        val predicate: P,
        val handler: suspend SC.() -> Unit,
    )

    val registeredEnterHandlers = mutableListOf<StateHandler<(S) -> Boolean, EnterScope<S, E, S>>>()
    val registeredActionHandlers = mutableListOf<StateHandler<(S, A) -> Boolean, ActionScope<S, A, E, S>>>()
    val registeredExitHandlers = mutableListOf<StateHandler<(S) -> Boolean, ExitScope<S, E, S>>>()
    val registeredErrorHandlers = mutableListOf<StateHandler<(S, Exception) -> Boolean, ErrorScope<S, E, S, Exception>>>()

    private val onEnter: suspend EnterScope<S, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredEnterHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredActionHandlers.firstOrNull { it.predicate(state, action) }
        matchingHandler?.handler?.invoke(this) ?: state
    }

    private val onExit: suspend ExitScope<S, E, S>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredExitHandlers.firstOrNull { it.predicate(state) }
        matchingHandler?.handler?.invoke(this)
    }

    private val onError: suspend ErrorScope<S, E, S, Exception>.() -> Unit = {
        val matchingHandler = this@StoreBuilder.registeredErrorHandlers.firstOrNull { it.predicate(state, error) }
        matchingHandler?.handler?.invoke(this) ?: throw error
    }

    @TartStoreDsl
    class StateHandlerConfig<S : State, A : Action, E : Event, S2 : S> {

        class ThreadedHandler<P, SC : StoreScope>(
            private val dispatcher: CoroutineDispatcher?,
            val predicate: P,
            private val handler: suspend SC.() -> Unit,
        ) {
            suspend operator fun invoke(scope: SC) {
                if (dispatcher == null) {
                    handler(scope)
                } else {
                    withContext(dispatcher) {
                        handler(scope)
                    }
                }
            }
        }

        val stateEnterHandlers = mutableListOf<ThreadedHandler<Nothing?, EnterScope<S, E, S2>>>()
        val stateActionHandlers = mutableListOf<ThreadedHandler<(A) -> Boolean, ActionScope<S, A, E, S2>>>()
        val stateExitHandlers = mutableListOf<ThreadedHandler<Nothing?, ExitScope<S, E, S2>>>()
        val stateErrorHandlers = mutableListOf<ThreadedHandler<(Exception) -> Boolean, ErrorScope<S, E, S2, Exception>>>()

        /**
         * Registers a handler to be invoked when entering this state with the specified CoroutineDispatcher.
         * If no dispatcher is provided, the handler runs in the Store's current execution context.
         * If multiple `enter {}` handlers can match the current state, the first registered handler is used.
         * Supplying a dispatcher changes where the handler runs, but the Store still waits for it to finish.
         *
         * @param dispatcher Optional CoroutineDispatcher override for executing the enter handler
         * @param block The handler function that will be executed when entering this state
         */
        fun enter(dispatcher: CoroutineDispatcher? = null, block: suspend EnterScope<S, E, S2>.() -> Unit) {
            stateEnterHandlers.add(ThreadedHandler(dispatcher, null, block))
        }

        /**
         * Registers a handler for a specific action type in the current state configuration
         * with an optional CoroutineDispatcher.
         * If multiple `action {}` handlers can match the current state and action,
         * the first registered handler is used.
         * Supplying a dispatcher changes where the handler runs, but the Store still waits for it to finish.
         *
         * @param dispatcher Optional CoroutineDispatcher override for executing the action handler
         * @param block The handler function that processes the action and updates the state
         */
        inline fun <reified A2 : A> action(dispatcher: CoroutineDispatcher? = null, noinline block: suspend ActionScope<S, A2, E, S2>.() -> Unit) {
            stateActionHandlers.add(
                ThreadedHandler(
                    dispatcher = dispatcher,
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
         * If no dispatcher is provided, the handler runs in the Store's current execution context.
         * If multiple `exit {}` handlers can match the current state, the first registered handler is used.
         * Supplying a dispatcher changes where the handler runs, but the Store still waits for it to finish.
         *
         * @param dispatcher Optional CoroutineDispatcher override for executing the exit handler
         * @param block The handler function that will be executed when exiting this state
         */
        fun exit(dispatcher: CoroutineDispatcher? = null, block: suspend ExitScope<S, E, S2>.() -> Unit) {
            stateExitHandlers.add(ThreadedHandler(dispatcher, null, block))
        }

        /**
         * Registers a handler for a specific exception type in the current state configuration
         * with an optional CoroutineDispatcher.
         * If multiple `error {}` handlers can match the current state and error,
         * the first registered handler is used.
         * Supplying a dispatcher changes where the handler runs, but the Store still waits for it to finish.
         *
         * @param dispatcher Optional CoroutineDispatcher override for executing the error handler
         * @param block The handler function that processes the error and updates the state
         */
        inline fun <reified T : Exception> error(dispatcher: CoroutineDispatcher? = null, noinline block: suspend ErrorScope<S, E, S2, T>.() -> Unit) {
            stateErrorHandlers.add(
                ThreadedHandler(
                    dispatcher = dispatcher,
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
     * Configures handlers for a specific state subtype.
     *
     * Inside the block, you can register `enter {}`, `action {}`, `exit {}`, and `error {}`
     * handlers narrowed to [S2].
     * Handler selection is first-match in registration order.
     * If both broad and specific handlers can match, place broader handlers later.
     *
     * @param block Configuration block for handlers that target [S2]
     */
    inline fun <reified S2 : S> state(noinline block: StateHandlerConfig<S, A, E, S2>.() -> Unit) {
        val config = StateHandlerConfig<S, A, E, S2>().apply(block)

        for (enterHandler in config.stateEnterHandlers) {
            registeredEnterHandlers.add(
                StateHandler(
                    predicate = { it is S2 },
                    handler = {
                        @Suppress("UNCHECKED_CAST")
                        enterHandler.invoke(this as EnterScope<S, E, S2>)
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
                        exitHandler.invoke(this as ExitScope<S, E, S2>)
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
                        errorHandler.invoke(this as ErrorScope<S, E, S2, Exception>)
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
            override val pendingActionPolicy: PendingActionPolicy = storePendingActionPolicy
            override val pluginExecutionPolicy: PluginExecutionPolicy = storePluginExecutionPolicy
            override val plugins: List<Plugin<S, A, E>> = storePlugins
            override val onEnter: suspend EnterScope<S, E, S>.() -> Unit = this@StoreBuilder.onEnter
            override val onAction: suspend ActionScope<S, A, E, S>.() -> Unit = this@StoreBuilder.onAction
            override val onExit: suspend ExitScope<S, E, S>.() -> Unit = this@StoreBuilder.onExit
            override val onError: suspend ErrorScope<S, E, S, Exception>.() -> Unit = this@StoreBuilder.onError
        }
    }
}

/**
 * Main Store DSL block used to configure runtime behavior and register state handlers.
 */
typealias Setup<S, A, E> = StoreBuilder<S, A, E>.() -> Unit

/**
 * Store overrides block applied after the main [Setup] block.
 *
 * This block is limited to non-state configuration such as coroutine context, persistence,
 * exception handling, pending action policy, and plugins.
 */
typealias Overrides<S, A, E> = StoreOverridesBuilder<S, A, E>.() -> Unit

/**
 * DSL for overriding non-state Store configuration after the main setup has been applied.
 *
 * This DSL intentionally does not expose state or action handler APIs.
 */
@Suppress("unused")
@TartStoreDsl
class StoreOverridesBuilder<S : State, A : Action, E : Event> internal constructor() {
    private val operations = mutableListOf<StoreBuilder<S, A, E>.() -> Unit>()

    /**
     * Overrides the root coroutine context used by Store processing.
     */
    fun coroutineContext(coroutineContext: CoroutineContext) {
        operations.add { coroutineContext(coroutineContext) }
    }

    /**
     * Overrides the saver used to restore and persist state snapshots.
     */
    fun stateSaver(stateSaver: StateSaver<S>) {
        operations.add { stateSaver(stateSaver) }
    }

    /**
     * Overrides the handler used for non-fatal Store exceptions.
     */
    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        operations.add { exceptionHandler(exceptionHandler) }
    }

    /**
     * Overrides how queued actions are treated when a committed state change leaves the current
     * state variant.
     */
    fun pendingActionPolicy(policy: PendingActionPolicy) {
        operations.add { pendingActionPolicy(policy) }
    }

    /**
     * Overrides how the Store invokes plugins when multiple plugin instances are registered.
     */
    fun pluginExecutionPolicy(policy: PluginExecutionPolicy) {
        operations.add { pluginExecutionPolicy(policy) }
    }

    /**
     * Appends plugins after the main setup block has run.
     */
    fun plugin(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        val restValues = rest.copyOf()
        operations.add { plugin(first, *restValues) }
    }

    /**
     * Replaces every plugin configured so far.
     *
     * This is useful for removing default plugins in tests or debug setups.
     */
    fun replacePlugins(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        val restValues = rest.copyOf()
        operations.add { replacePlugins(first, *restValues) }
    }

    /**
     * Removes every plugin configured so far.
     *
     * This is useful for removing default plugins in tests or debug setups.
     */
    fun clearPlugins() {
        operations.add { clearPlugins() }
    }

    internal fun applyTo(builder: StoreBuilder<S, A, E>) {
        operations.forEach { operation -> operation(builder) }
    }
}

private fun <S : State, A : Action, E : Event> buildStore(
    initialState: S? = null,
    coroutineContext: CoroutineContext? = null,
    overrides: Overrides<S, A, E>? = null,
    setup: Setup<S, A, E>,
): Store<S, A, E> {
    val builder = StoreBuilder<S, A, E>()
    initialState?.let(builder::initialState)
    coroutineContext?.let(builder::coroutineContext)
    builder.setup()
    overrides?.let {
        StoreOverridesBuilder<S, A, E>().apply(it).applyTo(builder)
    }
    return builder.build()
}

/**
 * Creates a Store from a [Setup] block and optional [Overrides].
 *
 * The initial state must be set inside [setup] by calling [StoreBuilder.initialState].
 * [overrides] runs after [setup], so it can replace non-state configuration declared there.
 *
 * @param overrides Overrides block for non-state Store configuration
 * @param setup Setup block to customize the store
 * @return A configured Store instance
 * @throws IllegalArgumentException if the initial state is not set in the block
 */
fun <S : State, A : Action, E : Event> Store(
    overrides: Overrides<S, A, E> = {},
    setup: Setup<S, A, E>,
): Store<S, A, E> {
    return buildStore(overrides = overrides, setup = setup)
}

/**
 * Creates a Store with a declared initial state and optional [Overrides].
 *
 * [overrides] runs after [setup], so it can replace non-state configuration declared there.
 *
 * @param initialState The initial state of the Store when no saved snapshot is restored
 * @param overrides Overrides block for non-state Store configuration
 * @param setup Setup block to customize the store
 * @return A configured Store instance
 */
fun <S : State, A : Action, E : Event> Store(
    initialState: S,
    overrides: Overrides<S, A, E> = {},
    setup: Setup<S, A, E>,
): Store<S, A, E> {
    return buildStore(initialState = initialState, overrides = overrides, setup = setup)
}

/**
 * Creates a Store with an explicit root coroutine context and optional [Overrides].
 *
 * The [coroutineContext] parameter is applied before [setup].
 * The initial state must be set inside [setup] by calling [StoreBuilder.initialState].
 * [overrides] runs after [setup], so it can replace non-state configuration declared there.
 *
 * @param coroutineContext The coroutine context to use for Store processing
 * @param overrides Overrides block for non-state Store configuration
 * @param setup Setup block to customize the store
 * @return A configured Store instance
 * @throws IllegalArgumentException if the initial state is not set in the block
 */
fun <S : State, A : Action, E : Event> Store(
    coroutineContext: CoroutineContext,
    overrides: Overrides<S, A, E> = {},
    setup: Setup<S, A, E>,
): Store<S, A, E> {
    return buildStore(coroutineContext = coroutineContext, overrides = overrides, setup = setup)
}

/**
 * Creates a Store with a declared initial state, explicit root coroutine context, and optional
 * [Overrides].
 *
 * [initialState] and [coroutineContext] are applied before [setup].
 * [overrides] runs after [setup], so it can replace non-state configuration declared there.
 *
 * @param initialState The initial state of the Store when no saved snapshot is restored
 * @param coroutineContext The coroutine context to use for Store processing
 * @param overrides Overrides block for non-state Store configuration
 * @param setup Setup block to customize the store
 * @return A configured Store instance
 */
fun <S : State, A : Action, E : Event> Store(
    initialState: S,
    coroutineContext: CoroutineContext,
    overrides: Overrides<S, A, E> = {},
    setup: Setup<S, A, E>,
): Store<S, A, E> {
    return buildStore(initialState = initialState, coroutineContext = coroutineContext, overrides = overrides, setup = setup)
}
