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

    fun collectState(skipInitialState: Boolean = false, startStore: Boolean = true, state: (state: S) -> Unit)

    fun collectEvent(event: (event: E) -> Unit)

    fun dispose()

    @Suppress("unused")
    abstract class Base<S : State, A : Action, E : Event>(
        initialState: S,
        coroutineContext: CoroutineContext = Dispatchers.Default,
        onError: (error: Throwable) -> Unit = {}, // deprecated
    ) : TartStore<S, A, E>(initialState, coroutineContext, onError) {
        override val exceptionHandler: ExceptionHandler = exceptionHandler {
            it.printStackTrace()
        }
        override val stateSaver: StateSaver<S> = stateSaver(
            save = {},
            restore = { null },
        )
        override val middlewares: List<Middleware<S, A, E>> = emptyList()
        override suspend fun onEnter(state: S): S = state
        override suspend fun onExit(state: S) {}
        override suspend fun onDispatch(state: S, action: A): S = state
        override suspend fun onError(state: S, error: Throwable): S {
            throw error
        }
    }

    companion object {
        @Deprecated("Use store() function instead")
        fun <S : State, A : Action, E : Event> mock(state: S): Store<S, A, E> {
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
    }
}

@Suppress("unused")
fun <S : State, A : Action, E : Event> store(state: S): Store<S, A, E> {
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
