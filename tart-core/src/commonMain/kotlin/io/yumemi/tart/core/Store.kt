package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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

@Suppress("unused")
fun <S : State, A : Action, E : Event> Store(state: S): Store<S, A, E> {
    return object : Store<S, A, E> {
        override val state: StateFlow<S> = MutableStateFlow(state)
        override val event: Flow<E> = emptyFlow()
        override val currentState: S = state
        override fun dispatch(action: A) {}
        override fun collectState(skipInitialState: Boolean, startStore: Boolean, state: (state: S) -> Unit) {}
        override fun collectEvent(event: (event: E) -> Unit) {}
        override fun dispose() {}
    }
}
