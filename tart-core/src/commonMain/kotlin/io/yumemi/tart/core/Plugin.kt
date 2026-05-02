package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Extension point for reacting to Store lifecycle events such as startup, actions, committed
 * state changes, event emission, and error handling.
 *
 * Plugin hooks are suspending functions and are awaited by the Store before it continues
 * processing. Long-running work in a hook can delay Store processing. When work should continue
 * in the background, start it from a hook using [PluginScope.launch].
 */
interface Plugin<S : State, A : Action, E : Event> {
    /**
     * Called once when the Store starts, before the initial `enter {}` processing runs.
     */
    suspend fun onStart(scope: PluginScope<S, A>, state: S) {}

    /**
     * Called before an action handler starts processing the dispatched action.
     */
    suspend fun onAction(scope: PluginScope<S, A>, state: S, action: A) {}

    /**
     * Called after a new state snapshot is committed and persisted.
     */
    suspend fun onStateChanged(scope: PluginScope<S, A>, prevState: S, state: S) {}

    /**
     * Called before an event is emitted to collectors and observers.
     */
    suspend fun onEvent(scope: PluginScope<S, A>, state: S, event: E) {}

    /**
     * Called before a matching `error {}` handler runs for a non-fatal [Exception].
     */
    suspend fun onError(scope: PluginScope<S, A>, state: S, error: Exception) {}
}

/**
 * Creates a [Plugin] from individual lifecycle callbacks.
 *
 * These callbacks are suspending and are awaited by the Store just like a custom
 * [Plugin] implementation.
 *
 * All callbacks use [PluginScope] as the receiver so they can inspect the latest committed
 * state, dispatch additional actions, or launch Store-scoped background work without having
 * to capture the scope manually.
 *
 * @param onStart Function called when the Store starts
 * @param onAction Function called before an action handler starts
 * @param onStateChanged Function called after a state snapshot is committed and persisted
 * @param onEvent Function called before an event is emitted
 * @param onError Function called before an error handler runs
 */
fun <S : State, A : Action, E : Event> Plugin(
    onStart: suspend PluginScope<S, A>.(S) -> Unit = {},
    onAction: suspend PluginScope<S, A>.(S, A) -> Unit = { _, _ -> },
    onStateChanged: suspend PluginScope<S, A>.(S, S) -> Unit = { _, _ -> },
    onEvent: suspend PluginScope<S, A>.(S, E) -> Unit = { _, _ -> },
    onError: suspend PluginScope<S, A>.(S, Exception) -> Unit = { _, _ -> },
) = object : Plugin<S, A, E> {
    override suspend fun onStart(scope: PluginScope<S, A>, state: S) {
        scope.onStart(state)
    }

    override suspend fun onAction(scope: PluginScope<S, A>, state: S, action: A) {
        scope.onAction(state, action)
    }

    override suspend fun onStateChanged(scope: PluginScope<S, A>, prevState: S, state: S) {
        scope.onStateChanged(prevState, state)
    }

    override suspend fun onEvent(scope: PluginScope<S, A>, state: S, event: E) {
        scope.onEvent(state, event)
    }

    override suspend fun onError(scope: PluginScope<S, A>, state: S, error: Exception) {
        scope.onError(state, error)
    }
}

/**
 * Scope exposed to [Plugin] hooks.
 *
 * Plugins can use this scope to inspect the latest committed state snapshot, dispatch additional
 * actions, or start Store-scoped background work.
 */
interface PluginScope<S : State, A : Action> {
    /**
     * The latest committed state snapshot.
     *
     * This value may change immediately after it is read if other Store work commits a new state.
     */
    val currentState: S

    /**
     * Dispatches an action to the Store.
     *
     * This enqueues the action and returns immediately.
     * It does not wait for action handling to complete.
     */
    fun dispatch(action: A)

    /**
     * Starts background work in the Store's root coroutine scope and returns immediately.
     *
     * The launched coroutine survives state changes and is cancelled when the Store's root
     * coroutine scope is cancelled, such as by [Store.close] or parent scope cancellation.
     */
    fun launch(dispatcher: CoroutineDispatcher? = null, block: suspend CoroutineScope.() -> Unit)
}
