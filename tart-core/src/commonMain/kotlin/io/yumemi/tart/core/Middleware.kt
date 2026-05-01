package io.yumemi.tart.core

/**
 * Intercepts Store lifecycle milestones such as startup, action handling, state transitions,
 * event emission, and error handling.
 *
 * Middleware hooks are suspending functions and are awaited by the Store according to
 * [MiddlewareExecutionPolicy]. Long-running work in a hook can delay startup, action handling,
 * state transitions, event emission, and error processing.
 * When work should continue in the background, start it from [onStart] or another hook using
 * [MiddlewareScope.launch].
 */
interface Middleware<S : State, A : Action, E : Event> {
    /**
     * Called once when the Store starts, before the initial `enter {}` processing runs.
     *
     * @param middlewareScope Scope for dispatching actions or launching Store-scoped work
     * @param state The current state
     */
    suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {}

    /**
     * Called before an action handler starts processing the dispatched action.
     *
     * @param state The current state
     * @param action The action being dispatched
     */
    suspend fun beforeActionDispatch(state: S, action: A) {}

    /**
     * Called after the matching action handler finishes and its next state is determined.
     *
     * This runs before any resulting state exit, state change, or state enter processing occurs.
     *
     * @param state The original state
     * @param action The dispatched action
     * @param nextState The next state produced by action processing
     */
    suspend fun afterActionDispatch(state: S, action: A, nextState: S) {}

    /**
     * Called before an event is emitted to collectors and observers.
     *
     * @param state The current state
     * @param event The event being emitted
     */
    suspend fun beforeEventEmit(state: S, event: E) {}

    /**
     * Called after an event is emitted to collectors and observers.
     *
     * @param state The current state
     * @param event The emitted event
     */
    suspend fun afterEventEmit(state: S, event: E) {}

    /**
     * Called before the `enter {}` handler for a state runs.
     *
     * @param state The state that is beginning
     */
    suspend fun beforeStateEnter(state: S) {}

    /**
     * Called after the `enter {}` handler finishes and its next state is determined.
     *
     * @param state The original state
     * @param nextState The next state produced by the enter handler
     */
    suspend fun afterStateEnter(state: S, nextState: S) {}

    /**
     * Called before the `exit {}` handler for a state runs.
     *
     * @param state The state that is ending
     */
    suspend fun beforeStateExit(state: S) {}

    /**
     * Called after the `exit {}` handler for a state finishes.
     *
     * @param state The state that ended
     */
    suspend fun afterStateExit(state: S) {}

    /**
     * Called before a committed state snapshot is updated and persisted.
     *
     * @param state The current state
     * @param nextState The next state
     */
    suspend fun beforeStateChange(state: S, nextState: S) {}

    /**
     * Called after a new state snapshot is committed, persisted, and reported to observers.
     *
     * @param state The new state
     * @param prevState The previous state
     */
    suspend fun afterStateChange(state: S, prevState: S) {}

    /**
     * Called before a matching `error {}` handler runs for a non-fatal exception.
     *
     * @param state The current state
     * @param error The error that occurred
     */
    suspend fun beforeError(state: S, error: Throwable) {}

    /**
     * Called after the matching `error {}` handler finishes and its next state is determined.
     *
     * This runs before any resulting state exit, state change, or state enter processing occurs.
     *
     * @param state The original state
     * @param nextState The next state produced by error handling
     * @param error The error that occurred
     */
    suspend fun afterError(state: S, nextState: S, error: Throwable) {}
}

/**
 * Creates a [Middleware] from individual lifecycle callbacks.
 *
 * These callbacks are suspending and are awaited by the Store just like a custom
 * [Middleware] implementation.
 *
 * @param onStart Function called when the Store starts
 * @param beforeActionDispatch Function called before an action handler starts
 * @param afterActionDispatch Function called after an action handler determines its next state
 * @param beforeEventEmit Function called before an event is emitted
 * @param afterEventEmit Function called after an event is emitted
 * @param beforeStateEnter Function called before an enter handler runs
 * @param afterStateEnter Function called after an enter handler determines its next state
 * @param beforeStateExit Function called before an exit handler runs
 * @param afterStateExit Function called after an exit handler finishes
 * @param beforeStateChange Function called before a state snapshot is committed
 * @param afterStateChange Function called after a state snapshot is committed
 * @param beforeError Function called before an error handler runs
 * @param afterError Function called after an error handler determines its next state
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
