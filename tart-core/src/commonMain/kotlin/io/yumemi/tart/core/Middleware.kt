package io.yumemi.tart.core

/**
 * Interface for intercepting Store lifecycle events.
 * By implementing Middleware, you can insert processing at various lifecycle points
 * such as action dispatch, state changes, event emission, etc.
 */
interface Middleware<S : State, A : Action, E : Event> {
    /**
     * Lifecycle method called when the store is started.
     *
     * @param middlewareScope The scope that provides access to store functionality
     * @param state The current state
     */
    suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {}

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

/**
 * Factory function to easily create a Middleware instance.
 *
 * @param onStart Function called when the store is started with MiddlewareScope as receiver
 * @param beforeActionDispatch Function called before an action is dispatched
 * @param afterActionDispatch Function called after an action is dispatched
 * @param beforeEventEmit Function called before an event is emitted
 * @param afterEventEmit Function called after an event is emitted
 * @param beforeStateEnter Function called before a new state begins
 * @param afterStateEnter Function called after a new state has begun
 * @param beforeStateExit Function called before a state ends
 * @param afterStateExit Function called after a state has ended
 * @param beforeStateChange Function called before a state changes
 * @param afterStateChange Function called after a state has changed
 * @param beforeError Function called before an error occurs
 * @param afterError Function called after an error has been processed
 * @return A new Middleware instance
 */
fun <S : State, A : Action, E : Event> Middleware(
    onStart: suspend MiddlewareScope<A>.(S) -> Unit = {},
    beforeActionDispatch: suspend (S, A) -> Unit = { _, _ -> },
    afterActionDispatch: suspend (S, A, S) -> Unit = { _, _, _ -> },
    beforeEventEmit: suspend (S, E) -> Unit = { _, _ -> },
    afterEventEmit: suspend (S, E) -> Unit = { _, _ -> },
    beforeStateEnter: suspend (S) -> Unit = {},
    afterStateEnter: suspend (S, S) -> Unit = { _, _ -> },
    beforeStateExit: suspend (S) -> Unit = {},
    afterStateExit: suspend (S) -> Unit = {},
    beforeStateChange: suspend (S, S) -> Unit = { _, _ -> },
    afterStateChange: suspend (S, S) -> Unit = { _, _ -> },
    beforeError: suspend (S, Throwable) -> Unit = { _, _ -> },
    afterError: suspend (S, S, Throwable) -> Unit = { _, _, _ -> },
) = object : Middleware<S, A, E> {
    override suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {
        middlewareScope.onStart(state)
    }

    override suspend fun beforeActionDispatch(state: S, action: A) {
        beforeActionDispatch(state, action)
    }

    override suspend fun afterActionDispatch(state: S, action: A, nextState: S) {
        afterActionDispatch(state, action, nextState)
    }

    override suspend fun beforeEventEmit(state: S, event: E) {
        beforeEventEmit(state, event)
    }

    override suspend fun afterEventEmit(state: S, event: E) {
        afterEventEmit(state, event)
    }

    override suspend fun beforeStateEnter(state: S) {
        beforeStateEnter(state)
    }

    override suspend fun afterStateEnter(state: S, nextState: S) {
        afterStateEnter(state, nextState)
    }

    override suspend fun beforeStateExit(state: S) {
        beforeStateExit(state)
    }

    override suspend fun afterStateExit(state: S) {
        afterStateExit(state)
    }

    override suspend fun beforeStateChange(state: S, nextState: S) {
        beforeStateChange(state, nextState)
    }

    override suspend fun afterStateChange(state: S, prevState: S) {
        afterStateChange(state, prevState)
    }

    override suspend fun beforeError(state: S, error: Throwable) {
        beforeError(state, error)
    }

    override suspend fun afterError(state: S, nextState: S, error: Throwable) {
        afterError(state, nextState, error)
    }
}
