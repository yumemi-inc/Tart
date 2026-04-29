package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Base interface for all store scope types.
 * Provides a common type for different scopes in the state management flow.
 */
sealed interface StoreScope

/**
 * Scope available when a state is being entered.
 * Used in enter handlers to manage state transitions and side effects.
 */
@TartStoreDsl
interface EnterScope<S : State, A : Action, E : Event, S2 : S> : StoreScope {
    /**
     * The current state that's being entered
     */
    val state: S2

    /**
     * Updates the current state with a new state value.
     * Used within enter handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Updates the current state with a new state value computed from the given block.
     * Used within enter handlers to modify the state with computed values.
     *
     * @param block A function that computes and returns the new state
     */
    fun nextStateBy(block: () -> S)

    /**
     * Clears actions that are already queued behind the currently executing store work.
     * The action/transaction currently in progress keeps running.
     */
    fun clearPendingActions()

    /**
     * Emits an event from the enter handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)

    /**
     * Launches a coroutine within the context of this state.
     * The coroutine will be automatically cancelled when the state is exited.
     *
     * @param dispatcher Optional CoroutineDispatcher override for this coroutine.
     * When null, the coroutine inherits the Store's current execution context.
     * @param block The suspending block of code to execute
     */
    fun launch(dispatcher: CoroutineDispatcher? = null, block: suspend LaunchScope<S, E, S2>.() -> Unit)

    /**
     * Scope available within a launched coroutine.
     * Used for long-running operations or side effects within a state.
     */
    @TartStoreDsl
    interface LaunchScope<S : State, E : Event, S2 : S> : StoreScope {
        /**
         * Checks if the coroutine scope is still active.
         * Use this to verify if the state is still active before performing operations.
         *
         * @return True if the scope is still active, false otherwise
         */
        val isActive: Boolean

        /**
         * Emits an event from the launched coroutine.
         * Use this to communicate with the outside world about important occurrences.
         *
         * @param event The event to emit
         */
        suspend fun event(event: E)

        /**
         * Executes a transactional operation within the launch scope.
         * This allows state updates to be performed in an atomic, consistent manner.
         *
         * @param dispatcher Optional CoroutineDispatcher override for this operation.
         * When null, the transaction inherits the Store's current execution context.
         * @param block The suspending block of code to execute as a transaction
         */
        suspend fun transaction(dispatcher: CoroutineDispatcher? = null, block: suspend TransactionScope<S, E, S2>.() -> Unit)

        /**
         * Scope available within a transaction operation.
         * Used for atomic state updates with consistency guarantees.
         */
        @TartStoreDsl
        interface TransactionScope<S : State, E : Event, S2 : S> : StoreScope {
            /**
             * The current state when the transaction is being executed
             */
            val state: S2

            /**
             * Updates the current state with a new state value.
             * Used within transaction blocks to modify the state.
             *
             * @param state The new state value to update to
             */
            fun nextState(state: S)

            /**
             * Updates the current state with a new state value computed from the given block.
             * Used within transaction blocks to modify the state with computed values.
             *
             * @param block A function that computes and returns the new state
             */
            fun nextStateBy(block: () -> S)

            /**
             * Clears actions that are already queued behind the currently executing store work.
             * The action/transaction currently in progress keeps running.
             */
            fun clearPendingActions()

            /**
             * Emits an event from the transaction.
             * Use this to communicate with the outside world about important occurrences.
             *
             * @param event The event to emit
             */
            suspend fun event(event: E)
        }
    }
}

/**
 * Scope available when a state is being exited.
 * Used in exit handlers to perform cleanup or side effects when leaving a state.
 */
@TartStoreDsl
interface ExitScope<S : State, E : Event, S2 : S> : StoreScope {
    /**
     * The current state that's being exited
     */
    val state: S2

    /**
     * Clears actions that are already queued behind the currently executing store work.
     * The action/transaction currently in progress keeps running.
     */
    fun clearPendingActions()

    /**
     * Emits an event from the exit handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)
}

/**
 * Scope available when an action is being processed.
 * Used in action handlers to update state based on an action.
 */
@TartStoreDsl
interface ActionScope<S : State, A : Action, E : Event, S2 : S> : StoreScope {
    /**
     * The current state when the action is being processed
     */
    val state: S2

    /**
     * The action being processed
     */
    val action: A

    /**
     * Updates the current state with a new state value.
     * Used within action handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Updates the current state with a new state value computed from the given block.
     * Used within action handlers to modify the state with computed values.
     *
     * @param block A function that computes and returns the new state
     */
    fun nextStateBy(block: () -> S)

    /**
     * Clears actions that are already queued behind the currently executing store work.
     * The action/transaction currently in progress keeps running.
     */
    fun clearPendingActions()

    /**
     * Emits an event from the action handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)

    /**
     * Cancels active coroutines launched from action handlers with the given lane
     * in the current state's runtime when those launches are tracked by their launch control
     * such as [LaunchControl.Replace] or [LaunchControl.DropNew].
     *
     * If no matching launch exists, this is a no-op.
     *
     * @param lane The explicit launch lane identifying the cancellation group
     */
    fun cancelLaunch(lane: LaunchLane)

    /**
     * Launches a coroutine within the context of the current state where this action is processed.
     * The coroutine will be automatically cancelled when this state is exited.
     *
     * @param dispatcher Optional CoroutineDispatcher override for this coroutine.
     * When null, the coroutine inherits the Store's current execution context.
     * @param control The launch control used for coordination. Tracked controls
     * may use an explicit [LaunchLane] or the action-local default lane.
     * @param block The suspending block of code to execute
     */
    fun launch(dispatcher: CoroutineDispatcher? = null, control: LaunchControl = LaunchControl.Concurrent, block: suspend LaunchScope<S, A, E, S2>.() -> Unit)

    /**
     * Scope available within a launched coroutine from an action handler.
     * Used for long-running operations or side effects within a state.
     */
    @TartStoreDsl
    interface LaunchScope<S : State, A : Action, E : Event, S2 : S> : StoreScope {
        /**
         * Checks if the coroutine scope is still active.
         * Use this to verify if the state is still active before performing operations.
         *
         * @return True if the scope is still active, false otherwise
         */
        val isActive: Boolean

        /**
         * The action being processed when the launch was started.
         */
        val action: A

        /**
         * Emits an event from the launched coroutine.
         * Use this to communicate with the outside world about important occurrences.
         *
         * @param event The event to emit
         */
        suspend fun event(event: E)

        /**
         * Executes a transactional operation within the launch scope.
         * This allows state updates to be performed in an atomic, consistent manner.
         *
         * @param dispatcher Optional CoroutineDispatcher override for this operation.
         * When null, the transaction inherits the Store's current execution context.
         * @param block The suspending block of code to execute as a transaction
         */
        suspend fun transaction(dispatcher: CoroutineDispatcher? = null, block: suspend TransactionScope<S, A, E, S2>.() -> Unit)

        /**
         * Scope available within a transaction operation.
         * Used for atomic state updates with consistency guarantees.
         */
        @TartStoreDsl
        interface TransactionScope<S : State, A : Action, E : Event, S2 : S> : StoreScope {
            /**
             * The current state when the transaction is being executed
             */
            val state: S2

            /**
             * The action being processed when the launch was started.
             */
            val action: A

            /**
             * Updates the current state with a new state value.
             * Used within transaction blocks to modify the state.
             *
             * @param state The new state value to update to
             */
            fun nextState(state: S)

            /**
             * Updates the current state with a new state value computed from the given block.
             * Used within transaction blocks to modify the state with computed values.
             *
             * @param block A function that computes and returns the new state
             */
            fun nextStateBy(block: () -> S)

            /**
             * Clears actions that are already queued behind the currently executing store work.
             * The action/transaction currently in progress keeps running.
             */
            fun clearPendingActions()

            /**
             * Emits an event from the transaction.
             * Use this to communicate with the outside world about important occurrences.
             *
             * @param event The event to emit
             */
            suspend fun event(event: E)
        }
    }
}

/**
 * Scope available when an error occurs in a state handler.
 * Used in error handlers to recover from errors or update state accordingly.
 */
@TartStoreDsl
interface ErrorScope<S : State, E : Event, S2 : S, T : Throwable> : StoreScope {
    /**
     * The current state when the error occurred
     */
    val state: S2

    /**
     * The error that occurred
     */
    val error: T

    /**
     * Updates the current state with a new state value.
     * Used within error handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Updates the current state with a new state value computed from the given block.
     * Used within error handlers to modify the state with computed values.
     *
     * @param block A function that computes and returns the new state
     */
    fun nextStateBy(block: () -> S)

    /**
     * Clears actions that are already queued behind the currently executing store work.
     * The action/transaction currently in progress keeps running.
     */
    fun clearPendingActions()

    /**
     * Emits an event from the error handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)
}
