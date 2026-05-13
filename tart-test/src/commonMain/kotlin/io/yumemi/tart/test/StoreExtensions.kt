package io.yumemi.tart.test

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.InternalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import io.yumemi.tart.core.StoreInternalApi
import io.yumemi.tart.core.StorePatchBuilder

/**
 * Starts the Store and suspends until the startup work completes.
 *
 * Prefer this for tests that need to observe startup behavior before the first dispatched action.
 *
 * This waits for plugin `onStart` hooks and the synchronous `enter {}` chain triggered by startup.
 * It does not wait for additional work launched from `enter {}` handlers.
 *
 * This extension is available for Store instances created by the Tart DSL.
 *
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
@OptIn(InternalTartApi::class)
suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.startAndAwait() {
    requireStoreInternalApi().startAndAwait()
}

/**
 * Dispatches an action and suspends until the Store finishes the dispatch work itself.
 *
 * This waits for startup when needed, the matching action handler, and any resulting synchronous
 * state transition work triggered by that dispatch.
 * It does not wait for additional work launched from `enter {}` or `action {}` handlers.
 *
 * This extension is available for Store instances created by the Tart DSL.
 *
 * @param action The action to dispatch
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
@OptIn(InternalTartApi::class)
suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.dispatchAndAwait(action: A) {
    requireStoreInternalApi().dispatchAndAwait(action)
}

/**
 * Applies a non-state Store patch before the Store is started.
 *
 * This is intended for tests that need to swap persistence, policies, exception handling,
 * or plugins without rewriting the Store definition itself.
 * The patch must happen before APIs such as `start()`, `dispatch()`, `collectState()`, or
 * `collectEvent()` are used. Once the Store has consumed configuration values (e.g. `stateSaver`
 * is consumed once `currentState` is read; `coroutineContext` is consumed once a coroutine is
 * launched), patches that target those values will fail.
 *
 * This extension is available for Store instances created by the Tart DSL.
 *
 * @param builder Builder lambda for a non-state Store patch
 * @throws IllegalStateException if the Store has already been started or is starting
 * @throws IllegalStateException if the patch targets a value that has already been consumed
 * @throws IllegalStateException if the Store is not backed by Tart's internal implementation
 */
@OptIn(InternalTartApi::class)
fun <S : State, A : Action, E : Event> Store<S, A, E>.patch(
    builder: StorePatchBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    val patch = StorePatchBuilder<S, A, E>().apply(builder).build()
    return requireStoreInternalApi().patch(patch)
}

@OptIn(InternalTartApi::class)
private fun <S : State, A : Action, E : Event> Store<S, A, E>.requireStoreInternalApi(): StoreInternalApi<S, A, E> {
    @Suppress("UNCHECKED_CAST")
    return this as? StoreInternalApi<S, A, E>
        ?: throw IllegalStateException("[Tart] This API is only supported for Store instances created by Tart DSL")
}
