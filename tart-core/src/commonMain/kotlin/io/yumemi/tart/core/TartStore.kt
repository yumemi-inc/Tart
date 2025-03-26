package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Abstract class providing the basic implementation of the Store interface.
 * Implements core functionality such as state management, event emission, middleware processing, etc.
 */
abstract class TartStore<S : State, A : Action, E : Event> internal constructor() : Store<S, A, E> {
    private val _state: MutableStateFlow<S> by lazy {
        MutableStateFlow(
            try {
                stateSaver.restore() ?: initialState
            } catch (t: Throwable) {
                exceptionHandler.handle(t)
                initialState
            },
        )
    }
    final override val state: StateFlow<S> by lazy {
        init()
        _state
    }

    private val _event: MutableSharedFlow<E> = MutableSharedFlow()
    final override val event: Flow<E> = _event

    final override val currentState: S get() = _state.value

    protected abstract val initialState: S

    protected abstract val coroutineContext: CoroutineContext

    protected abstract val stateSaver: StateSaver<S>

    protected abstract val exceptionHandler: ExceptionHandler

    protected abstract val middlewares: List<Middleware<S, A, E>>

    protected abstract val onEnter: suspend TartStore<S, A, E>.(S) -> S

    protected abstract val onExit: suspend TartStore<S, A, E>.(S) -> Unit

    protected abstract val onDispatch: suspend TartStore<S, A, E>.(S, A) -> S

    protected abstract val onError: suspend TartStore<S, A, E>.(S, Throwable) -> S

    private val coroutineScope by lazy {
        CoroutineScope(
            coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
                val t = if (exception is InternalError) exception.original else exception
                exceptionHandler.handle(t)
            },
        )
    }

    private val mutex = Mutex()

    final override fun dispatch(action: A) {
        state // initialize if need
        coroutineScope.launch {
            mutex.withLock {
                onActionDispatched(currentState, action)
            }
        }
    }

    final override fun collectState(skipInitialState: Boolean, startStore: Boolean, state: (S) -> Unit) {
        coroutineScope.launch(Dispatchers.Unconfined) {
            _state.drop(if (skipInitialState) 1 else 0).collect { state(it) }
        }
        if (startStore) this.state // initialize if need
    }

    final override fun collectEvent(event: (E) -> Unit) {
        coroutineScope.launch((Dispatchers.Unconfined)) {
            this@TartStore.event.collect { event(it) }
        }
    }

    final override fun dispose() {
        coroutineScope.cancel()
    }

    @Deprecated("Use onEnter property instead", ReplaceWith("onEnter"))
    protected open suspend fun onEnter(state: S): S = state

    @Deprecated("Use onExit property instead", ReplaceWith("onExit"))
    protected open suspend fun onExit(state: S) {
    }

    @Deprecated("Use onDispatch property instead", ReplaceWith("onDispatch"))
    protected open suspend fun onDispatch(state: S, action: A): S = state

    @Deprecated("Use onError property instead", ReplaceWith("onError"))
    protected open suspend fun onError(state: S, error: Throwable): S {
        throw error
    }

    /**
     * Emits an event to all event listeners.
     * This is used to communicate with the outside world about important occurrences within the store.
     *
     * @param event The event to emit
     */
    @Suppress("unused")
    suspend fun emit(event: E) {
        processEventEmit(currentState, event)
    }

    private fun init() {
        coroutineScope.launch {
            mutex.withLock {
                processMiddleware { onInit(this@TartStore, coroutineScope.coroutineContext) }
                onStateEntered(currentState)
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
            if (t is InternalError) {
                throw t
            }
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
            if (t is InternalError) {
                throw t
            }
            if (inErrorHandling) {
                throw InternalError(t)
            }
            onErrorOccurred(currentState, t)
        }
    }

    private suspend fun onErrorOccurred(state: S, throwable: Throwable) {
        try {
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
        } catch (t: Throwable) {
            if (t is InternalError) {
                throw t
            }
            throw InternalError(t)
        }
    }

    private suspend fun processActonDispatch(state: S, action: A): S {
        processMiddleware { beforeActionDispatch(state, action) }
        val nextState = onDispatch.invoke(this, state, action)
        processMiddleware { afterActionDispatch(state, action, nextState) }
        return nextState
    }

    private suspend fun processStateEnter(state: S): S {
        processMiddleware { beforeStateEnter(state) }
        val nextState = onEnter.invoke(this, state)
        processMiddleware { afterStateEnter(state, nextState) }
        return nextState
    }

    private suspend fun processStateExit(state: S) {
        processMiddleware { beforeStateExit(state) }
        onExit.invoke(this, state)
        processMiddleware { afterStateExit(state) }
    }

    private suspend fun processStateChange(state: S, nextState: S) {
        processMiddleware { beforeStateChange(state, nextState) }
        _state.update { nextState }
        try {
            stateSaver.save(nextState)
        } catch (t: Throwable) {
            throw InternalError(t)
        }
        processMiddleware { afterStateChange(nextState, state) }
    }

    private suspend fun processError(state: S, throwable: Throwable): S {
        processMiddleware { beforeError(state, throwable) }
        val nextState = onError.invoke(this, state, throwable)
        processMiddleware { afterError(state, nextState, throwable) }
        return nextState
    }

    private suspend fun processEventEmit(state: S, event: E) {
        processMiddleware { beforeEventEmit(state, event) }
        _event.emit(event)
        processMiddleware { afterEventEmit(state, event) }
    }

    private suspend fun processMiddleware(block: suspend Middleware<S, A, E>.() -> Unit) {
        try {
            coroutineScope {
                middlewares.map {
                    launch { block(it) }
                }
            }
        } catch (t: Throwable) {
            throw InternalError(t)
        }
    }

    private class InternalError(val original: Throwable) : Throwable(original)
}
