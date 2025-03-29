package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
class StoreBuilder<S : State, A : Action, E : Event> {
    private var _initialState: S? = null
    private var _coroutineContext: CoroutineContext = EmptyCoroutineContext + Dispatchers.Default
    private var _stateSaver: StateSaver<S> = StateSaver.Noop()
    private var _exceptionHandler: ExceptionHandler = ExceptionHandler.Default
    private var _middlewares: MutableList<Middleware<S, A, E>> = mutableListOf()
    private var _onEnter: suspend StoreContext<S, A, E>.(S) -> S = { it }
    private var _onExit: suspend StoreContext<S, A, E>.(S) -> Unit = { }
    private var _onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = { state, _ -> state }
    private var _onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = { _, error -> throw error }

    fun initialState(state: S) {
        _initialState = state
    }

    fun coroutineContext(coroutineContext: CoroutineContext) {
        _coroutineContext = coroutineContext
    }

    fun stateSaver(stateSaver: StateSaver<S>) {
        _stateSaver = stateSaver
    }

    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        _exceptionHandler = exceptionHandler
    }

    fun middlewares(vararg middleware: Middleware<S, A, E>) {
        _middlewares.addAll(middleware)
    }

    fun middleware(middleware: Middleware<S, A, E>) {
        _middlewares.add(middleware)
    }

    fun onEnter(block: suspend StoreContext<S, A, E>.(S) -> S) {
        _onEnter = block
    }

    fun onExit(block: suspend StoreContext<S, A, E>.(S) -> Unit) {
        _onExit = block
    }

    fun onDispatch(block: suspend StoreContext<S, A, E>.(S, A) -> S) {
        _onDispatch = block
    }

    fun onError(block: suspend StoreContext<S, A, E>.(S, Throwable) -> S) {
        _onError = block
    }

    internal fun build(): Store<S, A, E> {
        val state = requireNotNull(_initialState) { "Tart: InitialState must be set in Store configuration" }

        return object : TartStore<S, A, E>() {
            override val initialState: S = state
            override val coroutineContext: CoroutineContext = _coroutineContext
            override val stateSaver: StateSaver<S> = _stateSaver
            override val exceptionHandler: ExceptionHandler = _exceptionHandler
            override val middlewares: List<Middleware<S, A, E>> = _middlewares
            override val onEnter: suspend StoreContext<S, A, E>.(S) -> S = _onEnter
            override val onExit: suspend StoreContext<S, A, E>.(S) -> Unit = _onExit
            override val onDispatch: suspend StoreContext<S, A, E>.(S, A) -> S = _onDispatch
            override val onError: suspend StoreContext<S, A, E>.(S, Throwable) -> S = _onError
        }
    }
}

fun <S : State, A : Action, E : Event> Store(
    initialState: S,
    block: StoreBuilder<S, A, E>.() -> Unit = {},
): Store<S, A, E> {
    return StoreBuilder<S, A, E>().apply {
        initialState(initialState)
        block()
    }.build()
}

fun <S : State, A : Action, E : Event> Store(
    block: StoreBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    return StoreBuilder<S, A, E>().apply(block).build()
}
