package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

/**
 * Base interface for all store context types.
 * Provides a common type for different contexts in the state management flow.
 */
sealed interface StoreContext

/**
 * Context available when a state is being entered.
 * Used in enter handlers to manage state transitions and side effects.
 */
interface EnterContext<S : State, A : Action, E : Event, S0 : State> : StoreContext {
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
     *
     * @param block The suspending block of code to execute
     */
    fun launch(block: suspend () -> Unit)

    /**
     * Dispatches an action to the store.
     * Use this to trigger new state transitions from within a state.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * Updates the current state with a new state value.
     * Used within enter handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun state(state: S0)
}

/**
 * Context available when a state is being exited.
 * Used in exit handlers to perform cleanup or side effects when leaving a state.
 */
interface ExitContext<S : State, A : Action, E : Event> : StoreContext {
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
 * Context available when an action is being processed.
 * Used in action handlers to update state based on an action.
 */
interface ActionContext<S : State, A : Action, E : Event, S0 : State> : StoreContext {
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
 * Context available when an error occurs in a state handler.
 * Used in error handlers to recover from errors or update state accordingly.
 */
interface ErrorContext<S : State, A : Action, E : Event, S0 : State> : StoreContext {
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

/**
 * Context available in middleware components.
 * Provides access to dispatch and coroutine context for middleware operations.
 */
interface MiddlewareContext<S : State, A : Action, E : Event> : StoreContext {
    /**
     * Dispatches an action from middleware.
     * Use this to trigger new state transitions from within middleware.
     *
     * @param action The action to dispatch
     */
    fun dispatch(action: A)

    /**
     * The coroutine context for executing middleware operations
     */
    val coroutineContext: CoroutineContext
}
