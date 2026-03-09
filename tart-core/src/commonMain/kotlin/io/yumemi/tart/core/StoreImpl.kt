package io.yumemi.tart.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                handleException(t)
                initialState
            },
        )
    }

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    final override val state: StateFlow<S> by lazy {
        object : StateFlow<S> {
            override val replayCache: List<S> get() = _state.replayCache
            override val value: S get() = _state.value
            override suspend fun collect(collector: FlowCollector<S>): Nothing = coroutineScope {
                launch {
                    _state.collect(collector)
                }
                coroutineScope.launch {
                    mutex.withLock {
                        initializeIfNeeded()
                    }
                }
                awaitCancellation()
            }
        }
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

    protected abstract val onExit: suspend ExitScope<S, E, S>.() -> Unit

    protected abstract val onError: suspend ErrorScope<S, E, S, Throwable>.() -> Unit

    private val coroutineScope by lazy {
        CoroutineScope(
            coroutineContext + SupervisorJob(coroutineContext[Job]) + CoroutineExceptionHandler { _, exception ->
                handleException(exception)
            },
        )
    }

    private val dispatchScope by lazy {
        CoroutineScope(
            coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]),
        )
    }

    private val mutex = Mutex()

    private val stateScopes = mutableMapOf<KClass<out S>, CoroutineScope>()

    private var activeDispatchJob: Job? = null

    final override fun dispatch(action: A) {
        dispatchScope.launch {
            mutex.withLock {
                val dispatchJob = coroutineContext[Job]
                activeDispatchJob = dispatchJob
                try {
                    initializeIfNeeded()
                    onActionDispatched(currentState, action)
                } finally {
                    if (activeDispatchJob == dispatchJob) {
                        activeDispatchJob = null
                    }
                }
            }
        }
    }

    final override fun collectState(state: (S) -> Unit) {
        coroutineScope.launch(Dispatchers.Unconfined) {
            this@StoreImpl.state.collect { state(it) }
        }
    }

    final override fun collectEvent(event: (E) -> Unit) {
        coroutineScope.launch((Dispatchers.Unconfined)) {
            this@StoreImpl.event.collect { event(it) }
        }
    }

    final override fun dispose() {
        coroutineScope.cancel()
    }

    private var isInitialized: Boolean = false

    private suspend fun initializeIfNeeded() {
        if (isInitialized) return
        isInitialized = true
        processMiddleware {
            onStart(
                object : MiddlewareScope<A> {
                    override fun dispatch(action: A) {
                        this@StoreImpl.dispatch(action)
                    }

                    override fun launch(coroutineDispatcher: CoroutineDispatcher, block: suspend CoroutineScope.() -> Unit) {
                        coroutineScope.launch(coroutineDispatcher) {
                            block()
                        }
                    }
                },
                currentState,
            )
        }
        onStateEntered(currentState)
    }

    private suspend fun emit(event: E) {
        processEventEmit(currentState, event)
    }

    private suspend fun onActionDispatched(state: S, action: A) {
        try {
            val nextState = processActionDispatch(state, action)

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
            rethrowIfFatal(t)
            onErrorOccurred(currentState, t)
        }
    }

    private suspend fun onStateChanged(state: S, nextState: S) {
        try {
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
            rethrowIfFatal(t)
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
            rethrowIfFatal(t)
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
            rethrowIfFatal(t)
            throw InternalError(t)
        }
    }

    private suspend fun processActionDispatch(state: S, action: A): S {
        processMiddleware { beforeActionDispatch(state, action) }
        var newState: S? = null
        onAction.invoke(
            object : ActionScope<S, A, E, S> {
                override val state = state
                override val action = action
                override fun nextState(state: S) {
                    newState = state
                }

                override fun nextStateBy(block: () -> S) {
                    newState = block()
                }

                override fun cancelPendingActions() {
                    clearPendingDispatchJobs()
                }

                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun launch(coroutineDispatcher: CoroutineDispatcher, block: suspend ActionScope.LaunchScope<S, A, E, S>.() -> Unit) {
                    val stateScope = stateScopes[state::class] ?: throw InternalError(IllegalStateException("[Tart] State scope is not found"))
                    launchInStateScope(
                        stateScope = stateScope,
                        coroutineDispatcher = coroutineDispatcher,
                        buildLaunchScope = { buildActionLaunchScope(stateScope, action) },
                        block = block,
                    )
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
        val stateScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))
        stateScopes[state::class] = stateScope
        var newState: S? = null
        onEnter.invoke(
            object : EnterScope<S, A, E, S> {
                override val state = state
                override fun nextState(state: S) {
                    newState = state
                }

                override fun nextStateBy(block: () -> S) {
                    newState = block()
                }

                override fun cancelPendingActions() {
                    clearPendingDispatchJobs()
                }

                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun launch(coroutineDispatcher: CoroutineDispatcher, block: suspend EnterScope.LaunchScope<S, E, S>.() -> Unit) {
                    launchInStateScope(
                        stateScope = stateScope,
                        coroutineDispatcher = coroutineDispatcher,
                        buildLaunchScope = { buildEnterLaunchScope(stateScope) },
                        block = block,
                    )
                }
            },
        )
        val nextState = newState ?: state
        processMiddleware { afterStateEnter(state, nextState) }
        return nextState
    }

    private fun <LS> launchInStateScope(
        stateScope: CoroutineScope,
        coroutineDispatcher: CoroutineDispatcher,
        buildLaunchScope: () -> LS,
        block: suspend LS.() -> Unit,
    ) {
        stateScope.launch(coroutineDispatcher) {
            val launchScope = buildLaunchScope()
            try {
                block(launchScope)
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }
                coroutineScope.launch(coroutineDispatcher) {
                    mutex.withLock {
                        if (stateScope.isActive) {
                            onErrorOccurred(currentState, t)
                        }
                    }
                }
            }
        }
    }

    private fun buildEnterLaunchScope(stateScope: CoroutineScope): EnterScope.LaunchScope<S, E, S> {
        return object : EnterScope.LaunchScope<S, E, S> {
            override val isActive: Boolean get() = stateScope.isActive

            override suspend fun event(event: E) {
                emit(event)
            }

            override suspend fun transaction(coroutineDispatcher: CoroutineDispatcher, block: suspend EnterScope.LaunchScope.TransactionScope<S, E, S>.() -> Unit) {
                val job = coroutineScope.launch(coroutineDispatcher) {
                    mutex.withLock {
                        if (stateScope.isActive) {
                            var newState: S? = null
                            val transactionScope = object : EnterScope.LaunchScope.TransactionScope<S, E, S> {
                                override val state: S = currentState

                                override fun nextState(state: S) {
                                    newState = state
                                }

                                override fun nextStateBy(block: () -> S) {
                                    newState = block()
                                }

                                override fun cancelPendingActions() {
                                    clearPendingDispatchJobs()
                                }

                                override suspend fun event(event: E) {
                                    emit(event)
                                }
                            }
                            try {
                                block(transactionScope)
                            } catch (t: Throwable) {
                                if (t is CancellationException) {
                                    throw t
                                }
                                onErrorOccurred(currentState, t)
                                return@withLock
                            }
                            val nextState = newState ?: currentState
                            if (nextState != currentState) {
                                onStateChanged(currentState, nextState)
                            }
                        }
                    }
                }
                job.join()
            }
        }
    }

    private fun buildActionLaunchScope(stateScope: CoroutineScope, launchedAction: A): ActionScope.LaunchScope<S, A, E, S> {
        return object : ActionScope.LaunchScope<S, A, E, S> {
            override val isActive: Boolean get() = stateScope.isActive
            override val action: A = launchedAction

            override suspend fun event(event: E) {
                emit(event)
            }

            override suspend fun transaction(coroutineDispatcher: CoroutineDispatcher, block: suspend ActionScope.LaunchScope.TransactionScope<S, A, E, S>.() -> Unit) {
                val job = coroutineScope.launch(coroutineDispatcher) {
                    mutex.withLock {
                        if (stateScope.isActive) {
                            var newState: S? = null
                            val transactionScope = object : ActionScope.LaunchScope.TransactionScope<S, A, E, S> {
                                override val state: S = currentState
                                override val action: A = launchedAction

                                override fun nextState(state: S) {
                                    newState = state
                                }

                                override fun nextStateBy(block: () -> S) {
                                    newState = block()
                                }

                                override fun cancelPendingActions() {
                                    clearPendingDispatchJobs()
                                }

                                override suspend fun event(event: E) {
                                    emit(event)
                                }
                            }
                            try {
                                block(transactionScope)
                            } catch (t: Throwable) {
                                if (t is CancellationException) {
                                    throw t
                                }
                                onErrorOccurred(currentState, t)
                                return@withLock
                            }
                            val nextState = newState ?: currentState
                            if (nextState != currentState) {
                                onStateChanged(currentState, nextState)
                            }
                        }
                    }
                }
                job.join()
            }
        }
    }

    private suspend fun processStateExit(state: S) {
        try {
            processMiddleware { beforeStateExit(state) }
            onExit.invoke(
                object : ExitScope<S, E, S> {
                    override val state = state

                    override fun cancelPendingActions() {
                        clearPendingDispatchJobs()
                    }

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
            rethrowIfFatal(t)
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
                override fun nextState(state: S) {
                    newState = state
                }

                override fun nextStateBy(block: () -> S) {
                    newState = block()
                }

                override fun cancelPendingActions() {
                    clearPendingDispatchJobs()
                }

                override suspend fun event(event: E) {
                    emit(event)
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

    private fun clearPendingDispatchJobs() {
        val currentJob = activeDispatchJob
        val dispatchScopeJob = dispatchScope.coroutineContext[Job] ?: return
        dispatchScopeJob.children
            .filter { it != currentJob && it.isActive }
            .forEach { it.cancel() }
    }

    private suspend fun processMiddleware(block: suspend Middleware<S, A, E>.() -> Unit) {
        try {
            coroutineScope {
                middlewares.map {
                    launch { block(it) }
                }
            }
        } catch (t: Throwable) {
            rethrowIfFatal(t)
            throw InternalError(t)
        }
    }

    private fun handleException(t: Throwable) {
        val handled = if (t is InternalError) t.original else t
        exceptionHandler.handle(handled)
    }

    private fun rethrowIfFatal(t: Throwable) {
        if (t is CancellationException || t is Error || t is InternalError) {
            throw t
        }
    }

    private class InternalError(val original: Throwable) : Throwable(original)
}
