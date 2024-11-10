package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

open class TartStore<S : State, A : Action, E : Event> internal constructor(
    private val initialState: S,
    private val processInitialStateEnter: Boolean,
    private val latestState: suspend (state: S) -> Unit,
    onError: (error: Throwable) -> Unit,
    coroutineContext: CoroutineContext,
) : Store<S, A, E> {
    private val _state: MutableStateFlow<S> = MutableStateFlow(initialState)
    final override val state: StateFlow<S> by lazy {
        init()
        _state
    }

    private val _event: MutableSharedFlow<E> = MutableSharedFlow()
    final override val event: Flow<E> = _event

    final override val currentState: S get() = _state.value

    protected open val middlewares: List<Middleware<S, A, E>> = emptyList()

    private val coroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, exception -> onError(exception) })

    private val mutex = Mutex()

    final override fun dispatch(action: A) {
        state // initialize if need
        coroutineScope.launch {
            mutex.withLock {
                onActionDispatched(currentState, action)
            }
        }
    }

    final override fun collectState(state: (state: S) -> Unit) {
        coroutineScope.launch {
            this@TartStore.state.collect { state(it) }
        }
    }

    final override fun collectEvent(event: (event: E) -> Unit) {
        coroutineScope.launch {
            this@TartStore.event.collect { event(it) }
        }
    }

    final override fun dispose() {
        coroutineScope.cancel()
    }

    protected open suspend fun onEnter(state: S, emit: EmitFun<E>): S = state

    protected open suspend fun onExit(state: S, emit: EmitFun<E>) {}

    protected open suspend fun onDispatch(state: S, action: A, emit: EmitFun<E>): S = state

    protected open suspend fun onError(state: S, error: Throwable, emit: EmitFun<E>): S {
        throw error
    }

    private fun init() {
        coroutineScope.launch {
            mutex.withLock {
                coroutineScope {
                    middlewares.map {
                        launch { it.onInit(this@TartStore, coroutineScope.coroutineContext) }
                    }
                }
                if (processInitialStateEnter) {
                    onStateEntered(initialState)
                }
            }
        }
    }

    private suspend fun onActionDispatched(state: S, action: A) {
        try {
            val nextState = processActonDispatch(state, action)

            if (state::class != nextState::class) {
                processStateExit(state)
            }

            if (state != nextState) {
                processStateChange(state, nextState)
            }

            if (state::class != nextState::class) {
                onStateEntered(nextState)
            }
        } catch (t: Throwable) {
            onErrorOccurred(currentState, t)
        }
    }

    private suspend fun onStateEntered(state: S, inErrorHandling: Boolean = false) {
        try {
            val nextState = processStateEnter(state)

            if (state::class != nextState::class) {
                processStateExit(state)
            }

            if (state != nextState) {
                processStateChange(state, nextState)
            }

            if (state::class != nextState::class) {
                onStateEntered(nextState, inErrorHandling = inErrorHandling)
            }
        } catch (t: Throwable) {
            if (!inErrorHandling) {
                onErrorOccurred(currentState, t)
            } else {
                throw t
            }
        }
    }

    private suspend fun onErrorOccurred(state: S, throwable: Throwable) {
        val nextState = processError(state, throwable)

        if (state::class != nextState::class) {
            processStateExit(state)
        }

        if (state != nextState) {
            processStateChange(state, nextState)
        }

        if (state::class != nextState::class) {
            onStateEntered(nextState, inErrorHandling = true)
        }
    }

    private suspend fun processActonDispatch(state: S, action: A): S {
        coroutineScope {
            middlewares.map {
                launch { it.beforeActionDispatch(state, action) }
            }
        }
        val nextState = onDispatch(state, action, ::emit)
        coroutineScope {
            middlewares.map {
                launch { it.afterActionDispatch(state, action, nextState) }
            }
        }
        return nextState
    }

    private suspend fun processEventEmit(state: S, event: E) {
        coroutineScope {
            middlewares.map {
                launch { it.beforeEventEmit(state, event) }
            }
        }
        _event.emit(event)
        coroutineScope {
            middlewares.map {
                launch { it.afterEventEmit(state, event) }
            }
        }
    }

    private suspend fun processStateEnter(state: S): S {
        coroutineScope {
            middlewares.map {
                launch { it.beforeStateEnter(state) }
            }
        }
        val nextState = onEnter(state, ::emit)
        coroutineScope {
            middlewares.map {
                launch { it.afterStateEnter(state, nextState) }
            }
        }
        return nextState
    }

    private suspend fun processStateExit(state: S) {
        coroutineScope {
            middlewares.map {
                launch { it.beforeStateExit(state) }
            }
        }
        onExit(state, ::emit)
        coroutineScope {
            middlewares.map {
                launch { it.afterStateExit(state) }
            }
        }
    }

    private suspend fun processStateChange(state: S, nextState: S) {
        coroutineScope {
            middlewares.map {
                launch { it.beforeStateChange(state, nextState) }
            }
        }
        _state.update { nextState }
        latestState(nextState)
        coroutineScope {
            middlewares.map {
                launch { it.afterStateChange(nextState, state) }
            }
        }
    }

    private suspend fun processError(state: S, throwable: Throwable): S {
        coroutineScope {
            middlewares.map {
                launch { it.beforeError(state, throwable) }
            }
        }
        val nextState = onError(state, throwable, ::emit)
        coroutineScope {
            middlewares.map {
                launch { it.afterError(state, nextState, throwable) }
            }
        }
        return nextState
    }

    private suspend fun emit(event: E) {
        processEventEmit(currentState, event)
    }

    protected fun interface EmitFun<E> {
        suspend operator fun invoke(event: E)
    }
}
