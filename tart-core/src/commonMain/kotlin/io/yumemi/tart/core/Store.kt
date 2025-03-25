package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface Store<S : State, A : Action, E : Event> {

    val state: StateFlow<S>

    val event: Flow<E>

    val currentState: S

    fun dispatch(action: A)

    fun collectState(skipInitialState: Boolean = false, startStore: Boolean = true, state: (state: S) -> Unit)

    fun collectEvent(event: (event: E) -> Unit)

    fun dispose()

    abstract class Base<S : State, A : Action, E : Event>(
        initialState: S,
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default,
    ) : TartStore<S, A, E>(initialState) {
        override val stateSaver: StateSaver<S> = StateSaver(
            save = {},
            restore = { null },
        )
        override val exceptionHandler: ExceptionHandler = ExceptionHandler {
            it.printStackTrace()
        }
    }
}
