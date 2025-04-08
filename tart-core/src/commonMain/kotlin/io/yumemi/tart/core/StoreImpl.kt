package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

internal abstract class StoreImpl<S : State, A : Action, E : Event> : Store<S, A, E> {
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

    protected abstract val onEnter: suspend EnterScope<S, A, E, S>.() -> Unit

    protected abstract val onAction: suspend ActionScope<S, A, E, S>.() -> Unit

    protected abstract val onExit: suspend ExitScope<S, E>.() -> Unit

    protected abstract val onError: suspend ErrorScope<S, E, S, Throwable>.() -> Unit

    private val coroutineScope by lazy {
        CoroutineScope(
            coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
                val t = if (exception is InternalError) exception.original else exception
                exceptionHandler.handle(t)
            },
        )
    }

    private val mutex = Mutex()

    private val stateScopes = mutableMapOf<KClass<out S>, CoroutineScope>()

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
            this@StoreImpl.event.collect { event(it) }
        }
    }

    final override fun dispose() {
        coroutineScope.cancel()
    }

    private fun init() {
        coroutineScope.launch {
            mutex.withLock {
                processMiddleware {
                    onInit(
                        object : MiddlewareContext<A> {
                            override fun dispatch(action: A) {
                                this@StoreImpl.dispatch(action)
                            }

                            override val coroutineContext: CoroutineContext = coroutineScope.coroutineContext + Job()
                        },
                    )
                }
                onStateEntered(currentState)
            }
        }
    }

    private suspend fun emit(event: E) {
        processEventEmit(currentState, event)
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
        var newState: S? = null
        onAction.invoke(
            object : ActionScope<S, A, E, S> {
                override val state = state
                override val action = action
                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun state(state: S) {
                    newState = state
                }
            },
        )
        val nextState = newState ?: state
        processMiddleware { afterActionDispatch(state, action, nextState) }
        return nextState
    }

    private suspend fun processStateEnter(state: S): S {
        processMiddleware { beforeStateEnter(state) }
        stateScopes[state::class]?.cancel()
        val stateScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob())
        stateScopes[state::class] = stateScope
        var newState: S? = null
        onEnter.invoke(
            object : EnterScope<S, A, E, S> {
                override val state = state
                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun launch(coroutineDispatcher: CoroutineDispatcher, block: suspend EnterScope.LaunchScope<A, E>.() -> Unit) {
                    stateScope.launch(coroutineDispatcher) {
                        try {
                            val launchScope = object : EnterScope.LaunchScope<A, E> {
                                override val isActive: Boolean get() = stateScope.isActive
                                override fun dispatch(action: A) {
                                    if (stateScope.isActive) {
                                        this@StoreImpl.dispatch(action)
                                    }
                                }

                                override suspend fun event(event: E) {
                                    if (stateScope.isActive) {
                                        emit(event)
                                    }
                                }
                            }
                            block(launchScope)
                        } catch (t: Throwable) {
                            coroutineScope.launch {
                                mutex.withLock {
                                    if (stateScope.isActive) {
                                        onErrorOccurred(currentState, t)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun state(state: S) {
                    newState = state
                }
            },
        )
        val nextState = newState ?: state
        processMiddleware { afterStateEnter(state, nextState) }
        return nextState
    }

    private suspend fun processStateExit(state: S) {
        try {
            processMiddleware { beforeStateExit(state) }
            onExit.invoke(
                object : ExitScope<S, E> {
                    override val state = state
                    override suspend fun event(event: E) {
                        emit(event)
                    }
                },
            )
            processMiddleware { afterStateExit(state) }
        } finally {
            stateScopes[state::class]?.cancel()
            stateScopes.remove(state::class)
        }
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
        var newState: S? = null
        onError.invoke(
            object : ErrorScope<S, E, S, Throwable> {
                override val state = state
                override val error = throwable
                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun state(state: S) {
                    newState = state
                }
            },
        )
        val nextState = newState ?: state
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
