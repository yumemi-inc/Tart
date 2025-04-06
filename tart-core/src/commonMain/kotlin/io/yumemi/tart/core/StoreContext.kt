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
     * Function to emit events from the exit handler
     */
    val emit: suspend (E) -> Unit
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
     * Function to emit events from the action handler
     */
    val emit: suspend (E) -> Unit

    /**
     * Updates the current state with a new state value.
     * Used within action handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun S.update(state: S0)
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
     * Function to emit events from the error handler
     */
    val emit: suspend (E) -> Unit

    /**
     * Updates the current state with a new state value.
     * Used within error handlers to modify the state.
     *
     * @param state The new state value to update to
     */
    fun S.update(state: S0)
}

/**
 * Context available in middleware components.
 * Provides access to dispatch and coroutine context for middleware operations.
 */
interface MiddlewareContext<S : State, A : Action, E : Event> : StoreContext {
    /**
     * Function to dispatch actions from middleware
     */
    val dispatch: (A) -> Unit

    /**
     * The coroutine context for executing middleware operations
     */
    val coroutineContext: CoroutineContext
}
