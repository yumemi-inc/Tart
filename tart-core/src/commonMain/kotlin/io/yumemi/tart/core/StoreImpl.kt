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
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

@OptIn(InternalTartApi::class)
internal abstract class StoreImpl<S : State, A : Action, E : Event> : Store<S, A, E>, StoreInternalApi<S, A, E> {
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

    protected abstract val pendingActionPolicy: PendingActionPolicy

    protected abstract val middlewareExecutionPolicy: MiddlewareExecutionPolicy

    protected abstract val middlewares: List<Middleware<S, A, E>>

    protected abstract val onEnter: suspend EnterScope<S, E, S>.() -> Unit

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

    private val stateRuntimes = mutableMapOf<KClass<out S>, StateRuntime>()

    private val observers = mutableListOf<StoreObserver<S, E>>()

    private var activeDispatchJob: Job? = null

    private data class StateRuntime(
        val scope: CoroutineScope,
        val actionLaunchJobs: MutableMap<Any, Job> = mutableMapOf(),
    )

    final override fun dispatch(action: A) {
        launchDispatch(action)
    }

    final override suspend fun dispatchAndWait(action: A) {
        launchDispatch(action).join()
    }

    private fun launchDispatch(action: A): Job {
        return dispatchScope.launch {
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
        coroutineScope.launch {
            this@StoreImpl.state.collect { state(it) }
        }
    }

    final override fun collectEvent(event: (E) -> Unit) {
        coroutineScope.launch {
            this@StoreImpl.event.collect { event(it) }
        }
    }

    final override fun attachObserver(observer: StoreObserver<S, E>, notifyCurrentState: Boolean) {
        check(mutex.tryLock()) { "[Tart] Failed to attach observer because the Store is starting or already started" }
        try {
            check(!isInitialized) { "[Tart] Observer must be attached before the Store starts" }
            if (notifyCurrentState) {
                observer.onState(currentState)
            }
            observers.add(observer)
        } finally {
            mutex.unlock()
        }
    }

    final override fun close() {
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

                    override fun launch(dispatcher: CoroutineDispatcher?, block: suspend CoroutineScope.() -> Unit) {
                        coroutineScope.launch(dispatcher ?: EmptyCoroutineContext) {
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
                if (state::class != nextState::class) {
                    clearPendingActionsOnStateExitIfNeeded()
                }
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
                if (state::class != nextState::class) {
                    clearPendingActionsOnStateExitIfNeeded()
                }
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
                if (state::class != nextState::class) {
                    clearPendingActionsOnStateExitIfNeeded()
                }
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
                if (state::class != nextState::class) {
                    clearPendingActionsOnStateExitIfNeeded()
                }
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

                override fun clearPendingActions() {
                    clearPendingDispatchJobs()
                }

                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun cancelLaunch(lane: LaunchLane) {
                    val stateRuntime = stateRuntimes[state::class] ?: throw InternalError(IllegalStateException("[Tart] State scope is not found"))
                    cancelTrackedActionLaunch(stateRuntime, lane)
                }

                override fun launch(
                    dispatcher: CoroutineDispatcher?,
                    control: LaunchControl,
                    block: suspend ActionLaunchScope<S, A, E, S>.() -> Unit,
                ) {
                    val stateRuntime = stateRuntimes[state::class] ?: throw InternalError(IllegalStateException("[Tart] State scope is not found"))
                    launchActionInStateRuntime(
                        stateRuntime = stateRuntime,
                        action = action,
                        control = control,
                        dispatcher = dispatcher,
                        buildLaunchScope = { buildActionLaunchScope(stateRuntime.scope, action) },
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
        stateRuntimes[state::class]?.scope?.cancel()
        val stateRuntime = StateRuntime(
            scope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job])),
        )
        stateRuntimes[state::class] = stateRuntime
        var newState: S? = null
        onEnter.invoke(
            object : EnterScope<S, E, S> {
                override val state = state
                override fun nextState(state: S) {
                    newState = state
                }

                override fun nextStateBy(block: () -> S) {
                    newState = block()
                }

                override fun clearPendingActions() {
                    clearPendingDispatchJobs()
                }

                override suspend fun event(event: E) {
                    emit(event)
                }

                override fun launch(dispatcher: CoroutineDispatcher?, block: suspend EnterLaunchScope<S, E, S>.() -> Unit) {
                    launchInStateRuntime(
                        stateRuntime = stateRuntime,
                        dispatcher = dispatcher,
                        buildLaunchScope = { buildEnterLaunchScope(stateRuntime.scope) },
                        block = block,
                    )
                }
            },
        )
        val nextState = newState ?: state
        processMiddleware { afterStateEnter(state, nextState) }
        return nextState
    }

    private fun <LS> launchInStateRuntime(
        stateRuntime: StateRuntime,
        dispatcher: CoroutineDispatcher?,
        buildLaunchScope: () -> LS,
        block: suspend LS.() -> Unit,
    ): Job {
        return stateRuntime.scope.launch(dispatcher ?: EmptyCoroutineContext) {
            executeLaunchInStateRuntime(
                stateRuntime = stateRuntime,
                dispatcher = dispatcher,
                buildLaunchScope = buildLaunchScope,
                block = block,
            )
        }
    }

    private fun <LS> launchActionInStateRuntime(
        stateRuntime: StateRuntime,
        action: A,
        control: LaunchControl,
        dispatcher: CoroutineDispatcher?,
        buildLaunchScope: () -> LS,
        block: suspend LS.() -> Unit,
    ) {
        when (control) {
            LaunchControl.Concurrent -> {
                launchInStateRuntime(
                    stateRuntime = stateRuntime,
                    dispatcher = dispatcher,
                    buildLaunchScope = buildLaunchScope,
                    block = block,
                )
            }

            is LaunchControl.Replace -> {
                val trackedKey = resolveTrackedActionLaunchKey(action = action, control = control)
                cancelTrackedActionLaunch(stateRuntime, trackedKey)
                stateRuntime.actionLaunchJobs[trackedKey] = launchTrackedActionInStateRuntime(
                    stateRuntime = stateRuntime,
                    trackedKey = trackedKey,
                    dispatcher = dispatcher,
                    buildLaunchScope = buildLaunchScope,
                    block = block,
                )
            }

            is LaunchControl.DropNew -> {
                val trackedKey = resolveTrackedActionLaunchKey(action = action, control = control)
                if (stateRuntime.actionLaunchJobs[trackedKey]?.isActive == true) return
                stateRuntime.actionLaunchJobs[trackedKey] = launchTrackedActionInStateRuntime(
                    stateRuntime = stateRuntime,
                    trackedKey = trackedKey,
                    dispatcher = dispatcher,
                    buildLaunchScope = buildLaunchScope,
                    block = block,
                )
            }
        }
    }

    private fun <LS> launchTrackedActionInStateRuntime(
        stateRuntime: StateRuntime,
        trackedKey: Any,
        dispatcher: CoroutineDispatcher?,
        buildLaunchScope: () -> LS,
        block: suspend LS.() -> Unit,
    ): Job {
        return stateRuntime.scope.launch(dispatcher ?: EmptyCoroutineContext) {
            try {
                executeLaunchInStateRuntime(
                    stateRuntime = stateRuntime,
                    dispatcher = dispatcher,
                    buildLaunchScope = buildLaunchScope,
                    block = block,
                )
            } finally {
                if (stateRuntime.actionLaunchJobs[trackedKey] === coroutineContext[Job]) {
                    stateRuntime.actionLaunchJobs.remove(trackedKey)
                }
            }
        }
    }

    private fun resolveTrackedActionLaunchKey(action: A, control: LaunchControl): Any {
        return when (control) {
            LaunchControl.Concurrent -> error("Concurrent launches do not have a tracked lane")
            is LaunchControl.Replace -> control.lane ?: action::class
            is LaunchControl.DropNew -> control.lane ?: action::class
        }
    }

    private fun cancelTrackedActionLaunch(stateRuntime: StateRuntime, trackedKey: Any) {
        stateRuntime.actionLaunchJobs.remove(trackedKey)?.cancel()
    }

    private suspend fun <LS> executeLaunchInStateRuntime(
        stateRuntime: StateRuntime,
        dispatcher: CoroutineDispatcher?,
        buildLaunchScope: () -> LS,
        block: suspend LS.() -> Unit,
    ) {
        val launchScope = buildLaunchScope()
        try {
            block(launchScope)
        } catch (t: Throwable) {
            rethrowIfFatal(t)
            coroutineScope.launch(dispatcher ?: EmptyCoroutineContext) {
                mutex.withLock {
                    if (stateRuntime.scope.isActive) {
                        onErrorOccurred(currentState, t)
                    }
                }
            }
        }
    }

    private fun buildEnterLaunchScope(stateScope: CoroutineScope): EnterLaunchScope<S, E, S> {
        return object : EnterLaunchScope<S, E, S> {
            override val isActive: Boolean get() = stateScope.isActive

            override suspend fun event(event: E) {
                emit(event)
            }

            override suspend fun transaction(dispatcher: CoroutineDispatcher?, block: suspend EnterTransactionScope<S, E, S>.() -> Unit) {
                val job = coroutineScope.launch(dispatcher ?: EmptyCoroutineContext) {
                    mutex.withLock {
                        if (stateScope.isActive) {
                            var newState: S? = null
                            val transactionScope = object : EnterTransactionScope<S, E, S> {
                                override val state: S = currentState

                                override fun nextState(state: S) {
                                    newState = state
                                }

                                override fun nextStateBy(block: () -> S) {
                                    newState = block()
                                }

                                override fun clearPendingActions() {
                                    clearPendingDispatchJobs()
                                }

                                override suspend fun event(event: E) {
                                    emit(event)
                                }
                            }
                            try {
                                block(transactionScope)
                            } catch (t: Throwable) {
                                rethrowIfFatal(t)
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

    private fun buildActionLaunchScope(stateScope: CoroutineScope, launchedAction: A): ActionLaunchScope<S, A, E, S> {
        return object : ActionLaunchScope<S, A, E, S> {
            override val isActive: Boolean get() = stateScope.isActive
            override val action: A = launchedAction

            override suspend fun event(event: E) {
                emit(event)
            }

            override suspend fun transaction(dispatcher: CoroutineDispatcher?, block: suspend ActionTransactionScope<S, A, E, S>.() -> Unit) {
                val job = coroutineScope.launch(dispatcher ?: EmptyCoroutineContext) {
                    mutex.withLock {
                        if (stateScope.isActive) {
                            var newState: S? = null
                            val transactionScope = object : ActionTransactionScope<S, A, E, S> {
                                override val state: S = currentState
                                override val action: A = launchedAction

                                override fun nextState(state: S) {
                                    newState = state
                                }

                                override fun nextStateBy(block: () -> S) {
                                    newState = block()
                                }

                                override fun clearPendingActions() {
                                    clearPendingDispatchJobs()
                                }

                                override suspend fun event(event: E) {
                                    emit(event)
                                }
                            }
                            try {
                                block(transactionScope)
                            } catch (t: Throwable) {
                                rethrowIfFatal(t)
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

                    override fun clearPendingActions() {
                        clearPendingDispatchJobs()
                    }

                    override suspend fun event(event: E) {
                        emit(event)
                    }
                },
            )
            processMiddleware { afterStateExit(state) }
        } finally {
            stateRuntimes[state::class]?.scope?.cancel()
            stateRuntimes.remove(state::class)
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
        notifyStateRecorded(nextState)
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

                override fun clearPendingActions() {
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
        notifyEventRecorded(event)
        processMiddleware { afterEventEmit(state, event) }
    }

    private fun clearPendingActionsOnStateExitIfNeeded() {
        if (pendingActionPolicy == PendingActionPolicy.ClearOnStateExit) {
            clearPendingDispatchJobs()
        }
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
            when (middlewareExecutionPolicy) {
                MiddlewareExecutionPolicy.Concurrent -> coroutineScope {
                    middlewares.forEach { middleware ->
                        launch { middleware.block() }
                    }
                }

                MiddlewareExecutionPolicy.InRegistrationOrder -> middlewares.forEach { middleware ->
                    middleware.block()
                }
            }
        } catch (t: Throwable) {
            rethrowIfFatal(t)
            throw InternalError(t)
        }
    }

    private fun notifyStateRecorded(state: S) {
        observers.forEach { observer ->
            try {
                observer.onState(state)
            } catch (t: Throwable) {
                rethrowIfFatal(t)
                throw InternalError(t)
            }
        }
    }

    private fun notifyEventRecorded(event: E) {
        observers.forEach { observer ->
            try {
                observer.onEvent(event)
            } catch (t: Throwable) {
                rethrowIfFatal(t)
                throw InternalError(t)
            }
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
