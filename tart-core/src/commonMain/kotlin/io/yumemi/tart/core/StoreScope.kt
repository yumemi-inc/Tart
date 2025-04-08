package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
interface EnterScope<S : State, A : Action, E : Event, S0 : State> : StoreScope {
    /**
     * The current state that's being entered
     */
    val state: S

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
     * Within the block, you can use dispatch() method to send actions to the store.
     *
     * @param coroutineDispatcher The CoroutineDispatcher to use for this coroutine (defaults to Dispatchers.Unconfined)
     * @param block The suspending block of code to execute
     */
    fun launch(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined, block: suspend LaunchScope<A, E>.() -> Unit)

    /**
     * Updates the current state with a new state value.
     * Used within enter handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun state(state: S0)

    /**
     * Scope available inside launch blocks.
     * Provides the ability to dispatch actions within a coroutine.
     */

    @TartStoreDsl
    interface LaunchScope<A : Action, E : Event> : StoreScope {
        /**
         * Checks if the coroutine scope is still active.
         * Use this to verify if the state is still active before performing operations.
         *
         * @return True if the scope is still active, false otherwise
         */
        val isActive: Boolean

        /**
         * Dispatches an action to the store.
         * @param action The action to dispatch
         */
        fun dispatch(action: A)

        /**
         * Emits an event from the launch block.
         * Use this to communicate with the outside world about important occurrences.
         *
         * @param event The event to emit
         */
        suspend fun event(event: E)
    }
}

/**
 * Scope available when a state is being exited.
 * Used in exit handlers to perform cleanup or side effects when leaving a state.
 */
@TartStoreDsl
interface ExitScope<S : State, E : Event> : StoreScope {
    /**
     * The current state that's being exited
     */
    val state: S

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
interface ActionScope<S : State, A : Action, E : Event, S0 : State> : StoreScope {
    /**
     * The current state when the action is being processed
     */
    val state: S

    /**
     * The action being processed
     */
    val action: A

    /**
     * Emits an event from the action handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)

    /**
     * Updates the current state with a new state value.
     * Used within action handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun state(state: S0)
}

/**
 * Scope available when an error occurs in a state handler.
 * Used in error handlers to recover from errors or update state accordingly.
 */
@TartStoreDsl
interface ErrorScope<S : State, E : Event, S0 : State> : StoreScope {
    /**
     * The current state when the error occurred
     */
    val state: S

    /**
     * The error that occurred
     */
    val error: Throwable

    /**
     * Emits an event from the error handler.
     * Use this to communicate with the outside world about important occurrences.
     *
     * @param event The event to emit
     */
    suspend fun event(event: E)

    /**
     * Updates the current state with a new state value.
     * Used within error handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun state(state: S0)
}
