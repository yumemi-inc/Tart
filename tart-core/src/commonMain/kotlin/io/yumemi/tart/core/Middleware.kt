package io.yumemi.tart.core

/**
 * Interface for intercepting Store lifecycle events.
 * By implementing Middleware, you can insert processing at various lifecycle points
 * such as action dispatch, state changes, event emission, etc.
 */
interface Middleware<S : State, A : Action, E : Event> {
    /**
     * Called when the Store is initialized.
     *
     * @param middlewareContext The context providing access to store functionality
     */
    suspend fun onInit(middlewareContext: MiddlewareContext<S, A, E>) {}

    /**
     * Called before an action is dispatched.
     *
     * @param state The current state
     * @param action The action being dispatched
     */
    suspend fun beforeActionDispatch(state: S, action: A) {}

    /**
     * Called after an action is dispatched.
     *
     * @param state The original state
     * @param action The dispatched action
     * @param nextState The new state after action processing
     */
    suspend fun afterActionDispatch(state: S, action: A, nextState: S) {}

    /**
     * Called before an event is emitted.
     *
     * @param state The current state
     * @param event The event being emitted
     */
    suspend fun beforeEventEmit(state: S, event: E) {}

    /**
     * Called after an event is emitted.
     *
     * @param state The current state
     * @param event The emitted event
     */
    suspend fun afterEventEmit(state: S, event: E) {}

    /**
     * Called before a new state begins.
     *
     * @param state The state that is beginning
     */
    suspend fun beforeStateEnter(state: S) {}

    /**
     * Called after a new state has begun.
     *
     * @param state The original state
     * @param nextState The new state after beginning
     */
    suspend fun afterStateEnter(state: S, nextState: S) {}

    /**
     * Called before a state ends.
     *
     * @param state The state that is ending
     */
    suspend fun beforeStateExit(state: S) {}

    /**
     * Called after a state has ended.
     *
     * @param state The state that ended
     */
    suspend fun afterStateExit(state: S) {}

    /**
     * Called before a state changes.
     *
     * @param state The current state
     * @param nextState The next state
     */
    suspend fun beforeStateChange(state: S, nextState: S) {}

    /**
     * Called after a state has changed.
     *
     * @param state The new state
     * @param prevState The previous state
     */
    suspend fun afterStateChange(state: S, prevState: S) {}

    /**
     * Called before an error occurs.
     *
     * @param state The current state
     * @param error The error that occurred
     */
    suspend fun beforeError(state: S, error: Throwable) {}

    /**
     * Called after an error has been processed.
     *
     * @param state The original state
     * @param nextState The new state after error handling
     * @param error The error that occurred
     */
    suspend fun afterError(state: S, nextState: S, error: Throwable) {}
}
