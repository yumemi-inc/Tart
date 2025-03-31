package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

sealed interface StoreContext

interface EnterContext<S : State, A : Action, E : Event> : StoreContext {
    val state: S
    val emit: suspend (E) -> Unit
}

interface ExitContext<S : State, A : Action, E : Event> : StoreContext {
    val state: S
    val emit: suspend (E) -> Unit
}

interface DispatchContext<S : State, A : Action, E : Event> : StoreContext {
    val state: S
    val action: A
    val emit: suspend (E) -> Unit
}

interface ErrorContext<S : State, A : Action, E : Event> : StoreContext {
    val state: S
    val error: Throwable
    val emit: suspend (E) -> Unit
}

interface MiddlewareContext<S : State, A : Action, E : Event> : StoreContext {
    val dispatch: (A) -> Unit
    val coroutineContext: CoroutineContext
}
