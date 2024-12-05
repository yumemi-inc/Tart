package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.coroutines.CoroutineContext

interface Store<S : State, A : Action, E : Event> {

    val state: StateFlow<S>

    val event: Flow<E>

    val currentState: S

    fun dispatch(action: A)

    fun collectState(state: (state: S) -> Unit)

    fun collectEvent(event: (event: E) -> Unit)

    fun dispose()

    @Suppress("unused")
    abstract class Base<S : State, A : Action, E : Event>(
        initialState: S,
        latestState: suspend (state: S) -> Unit = {},
        onError: (error: Throwable) -> Unit = { throw it },
        coroutineContext: CoroutineContext = Dispatchers.Default,
    ) : TartStore<S, A, E>(
        initialState = initialState,
        latestState = latestState,
        onError = onError,
        coroutineContext = coroutineContext,
    )

    companion object {
        @Suppress("unused")
        fun <S : State, A : Action, E : Event> mock(state: S): Store<S, A, E> {
            return object : Store<S, A, E> {
                override val state: StateFlow<S> = MutableStateFlow(state)
                override val event: Flow<E> = emptyFlow()
                override val currentState: S = state
                override fun dispatch(action: A) {}
                override fun collectState(state: (state: S) -> Unit) {}
                override fun collectEvent(event: (event: E) -> Unit) {}
                override fun dispose() {}
            }
        }
    }
}
