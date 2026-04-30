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
interface EnterScope<S : State, E : Event, S2 : S> : StoreScope {
    /**
     * The current state snapshot when this handler is executing.
     * This value does not change immediately when [nextState] or [nextStateBy] is called.
     */
    val state: S2

    /**
     * Registers the next state to apply after the current enter handler finishes.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Registers the next state to apply after the current enter handler finishes
     * by computing it in the given block.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     * If neither [nextState] nor [nextStateBy] is called, the current [state] is kept.
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
     * Emits an event immediately from the current enter handler.
     * Emission is not deferred until after a next state registered in this handler is applied.
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
    fun launch(dispatcher: CoroutineDispatcher? = null, block: suspend EnterLaunchScope<S, E, S2>.() -> Unit)

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
         * Emits an event immediately from the launched coroutine.
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
        suspend fun transaction(dispatcher: CoroutineDispatcher? = null, block: suspend EnterTransactionScope<S, E, S2>.() -> Unit)

        /**
         * Scope available within a transaction operation.
         * Used for atomic state updates with consistency guarantees.
         */
        @TartStoreDsl
        interface TransactionScope<S : State, E : Event, S2 : S> : StoreScope {
            /**
             * The current state snapshot when this transaction is executing.
             * This value does not change immediately when [nextState] or [nextStateBy] is called.
             */
            val state: S2

            /**
             * Registers the next state to apply after the current transaction finishes.
             * This does not update [state] immediately.
             * If called multiple times in the same transaction, the last specified state is used.
             *
             * @param state The new state value to update to
             */
            fun nextState(state: S)

            /**
             * Registers the next state to apply after the current transaction finishes
             * by computing it in the given block.
             * This does not update [state] immediately.
             * If called multiple times in the same transaction, the last specified state is used.
             * If neither [nextState] nor [nextStateBy] is called, the current [state] is kept.
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
             * Emits an event immediately from the current transaction.
             * Emission is not deferred until after a next state registered in this transaction is applied.
             *
             * @param event The event to emit
             */
            suspend fun event(event: E)
        }
    }
}

/**
 * Flattened alias for [EnterScope.LaunchScope] to keep public signatures concise in IDE tooltips.
 *
 * `S2` remains because it carries the narrowed state type through to the transaction scope's `state`.
 */
typealias EnterLaunchScope<S, E, S2> = EnterScope.LaunchScope<S, E, S2>

/**
 * Flattened alias for [EnterScope.LaunchScope.TransactionScope] to keep public signatures concise in IDE tooltips.
 */
typealias EnterTransactionScope<S, E, S2> = EnterScope.LaunchScope.TransactionScope<S, E, S2>

/**
 * Scope available when a state is being exited.
 * Used in exit handlers to perform cleanup or side effects when leaving a state.
 */
@TartStoreDsl
interface ExitScope<S : State, E : Event, S2 : S> : StoreScope {
    /**
     * The current state snapshot when this handler is executing.
     */
    val state: S2

    /**
     * Clears actions that are already queued behind the currently executing store work.
     * The action/transaction currently in progress keeps running.
     */
    fun clearPendingActions()

    /**
     * Emits an event immediately from the current exit handler.
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
     * The current state snapshot when this handler is executing.
     * This value does not change immediately when [nextState] or [nextStateBy] is called.
     */
    val state: S2

    /**
     * The action being processed
     */
    val action: A

    /**
     * Registers the next state to apply after the current action handler finishes.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Registers the next state to apply after the current action handler finishes
     * by computing it in the given block.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     * If neither [nextState] nor [nextStateBy] is called, the current [state] is kept.
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
     * Emits an event immediately from the current action handler.
     * Emission is not deferred until after a next state registered in this handler is applied.
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
    fun launch(dispatcher: CoroutineDispatcher? = null, control: LaunchControl = LaunchControl.Concurrent, block: suspend ActionLaunchScope<S, A, E, S2>.() -> Unit)

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
         * Emits an event immediately from the launched coroutine.
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
        suspend fun transaction(dispatcher: CoroutineDispatcher? = null, block: suspend ActionTransactionScope<S, A, E, S2>.() -> Unit)

        /**
         * Scope available within a transaction operation.
         * Used for atomic state updates with consistency guarantees.
         */
        @TartStoreDsl
        interface TransactionScope<S : State, A : Action, E : Event, S2 : S> : StoreScope {
            /**
             * The current state snapshot when this transaction is executing.
             * This value does not change immediately when [nextState] or [nextStateBy] is called.
             */
            val state: S2

            /**
             * The action being processed when the launch was started.
             */
            val action: A

            /**
             * Registers the next state to apply after the current transaction finishes.
             * This does not update [state] immediately.
             * If called multiple times in the same transaction, the last specified state is used.
             *
             * @param state The new state value to update to
             */
            fun nextState(state: S)

            /**
             * Registers the next state to apply after the current transaction finishes
             * by computing it in the given block.
             * This does not update [state] immediately.
             * If called multiple times in the same transaction, the last specified state is used.
             * If neither [nextState] nor [nextStateBy] is called, the current [state] is kept.
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
             * Emits an event immediately from the current transaction.
             * Emission is not deferred until after a next state registered in this transaction is applied.
             *
             * @param event The event to emit
             */
            suspend fun event(event: E)
        }
    }
}

/**
 * Flattened alias for [ActionScope.LaunchScope] to keep public signatures concise in IDE tooltips.
 *
 * `S2` remains because it carries the narrowed state type through to the transaction scope's `state`.
 */
typealias ActionLaunchScope<S, A, E, S2> = ActionScope.LaunchScope<S, A, E, S2>

/**
 * Flattened alias for [ActionScope.LaunchScope.TransactionScope] to keep public signatures concise in IDE tooltips.
 */
typealias ActionTransactionScope<S, A, E, S2> = ActionScope.LaunchScope.TransactionScope<S, A, E, S2>

/**
 * Scope available when an error occurs in a state handler.
 * Used in error handlers to recover from errors or update state accordingly.
 */
@TartStoreDsl
interface ErrorScope<S : State, E : Event, S2 : S, T : Throwable> : StoreScope {
    /**
     * The current state snapshot when this handler is executing.
     * This value does not change immediately when [nextState] or [nextStateBy] is called.
     */
    val state: S2

    /**
     * The error that occurred
     */
    val error: T

    /**
     * Registers the next state to apply after the current error handler finishes.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     *
     * @param state The new state value to update to
     */
    fun nextState(state: S)

    /**
     * Registers the next state to apply after the current error handler finishes
     * by computing it in the given block.
     * This does not update [state] immediately.
     * If called multiple times in the same handler, the last specified state is used.
     * If neither [nextState] nor [nextStateBy] is called, the current [state] is kept.
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
     * Emits an event immediately from the current error handler.
     * Emission is not deferred until after a next state registered in this handler is applied.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)
}
