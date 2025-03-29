package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

/**
 * Provides context for store operations.
 */
interface StoreContext<S : State, A : Action, E : Event> {
    /** Dispatches an action to the store */
    val dispatch: (A) -> Unit

    /** Emits an event from the store */
    val emit: suspend (E) -> Unit

    /** The coroutine context for execution */
    val coroutineContext: CoroutineContext
}
