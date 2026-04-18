package io.yumemi.tart.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class StoreObserverTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    sealed interface AppState : State {
        data object Loading : AppState
        data class Main(val count: Int) : AppState
    }

    sealed interface AppAction : Action {
        data object Increment : AppAction
        data object EmitEvent : AppAction
    }

    sealed interface AppEvent : Event {
        data class CountUpdated(val count: Int) : AppEvent
    }

    @Test
    fun attachObserver_recordsRestoredCurrentStateWithoutStartingStore() = runTest(testDispatcher) {
        var started = false
        val store = createTestStore(
            stateSaver = StateSaver(
                save = {},
                restore = { AppState.Main(count = 5) },
            ),
            onStart = { started = true },
        )
        val history = ObservationHistory<AppState, AppEvent>()
        val observer = history.toObserver()

        store.attachObserverForTest(observer)

        assertFalse(started)
        assertEquals(listOf<AppState>(AppState.Main(count = 5)), history.stateHistory)
        assertTrue(history.eventHistory.isEmpty())
    }

    @Test
    fun attachObserver_recordsStateChangesAndEvents() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()
        val observer = history.toObserver()

        store.attachObserverForTest(observer)
        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.EmitEvent)

        assertEquals(
            listOf(
                AppState.Loading,
                AppState.Main(count = 0),
                AppState.Main(count = 1),
            ),
            history.stateHistory,
        )
        assertEquals(listOf<AppEvent>(AppEvent.CountUpdated(count = 1)), history.eventHistory)
    }

    @Test
    fun attachObserver_canBeCalledMultipleTimesBeforeStart() = runTest(testDispatcher) {
        val store = createTestStore()
        val firstHistory = ObservationHistory<AppState, AppEvent>()
        val secondHistory = ObservationHistory<AppState, AppEvent>()

        store.attachObserverForTest(firstHistory.toObserver())
        store.attachObserverForTest(secondHistory.toObserver())
        store.dispatch(AppAction.EmitEvent)

        assertEquals(firstHistory.stateHistory, secondHistory.stateHistory)
        assertEquals(firstHistory.eventHistory, secondHistory.eventHistory)
    }

    @Test
    fun attachObserver_isAllowedAfterCollectingEventsBeforeStart() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()

        store.collectEvent { }
        store.attachObserverForTest(history.toObserver())
        store.dispatch(AppAction.EmitEvent)

        assertEquals(
            listOf(
                AppState.Loading,
                AppState.Main(count = 0),
            ),
            history.stateHistory,
        )
        assertEquals(listOf<AppEvent>(AppEvent.CountUpdated(count = 0)), history.eventHistory)
    }

    @Test
    fun attachObserver_throwsAfterStoreStart() = runTest(testDispatcher) {
        val store = createTestStore()

        store.dispatch(AppAction.Increment)

        try {
            store.attachObserverForTest(
                object : StoreObserver<AppState, AppEvent> {
                    override fun onState(state: AppState) = Unit
                    override fun onEvent(event: AppEvent) = Unit
                },
            )
            fail("Expected attachObserver to fail after store start")
        } catch (t: Throwable) {
            assertIs<IllegalStateException>(t)
        }
    }

    @Test
    fun attachObserver_throwsWhileStoreIsStarting() = runTest(testDispatcher) {
        val startEntered = CompletableDeferred<Unit>()
        val releaseStart = CompletableDeferred<Unit>()
        val store = createTestStore(
            onStart = {
                startEntered.complete(Unit)
                releaseStart.await()
            },
        )

        store.dispatch(AppAction.Increment)
        startEntered.await()

        try {
            store.attachObserverForTest(
                object : StoreObserver<AppState, AppEvent> {
                    override fun onState(state: AppState) = Unit
                    override fun onEvent(event: AppEvent) = Unit
                },
            )
            fail("Expected attachObserver to fail while store is starting")
        } catch (t: Throwable) {
            assertIs<IllegalStateException>(t)
            assertEquals(
                "[Tart] Failed to attach observer because the Store is starting or already started",
                t.message,
            )
        } finally {
            releaseStart.complete(Unit)
        }

        testScheduler.runCurrent()
        assertEquals(AppState.Main(count = 1), store.currentState)
    }

    @Test
    fun attachObserver_canSkipInitialCurrentStateSnapshot() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()

        store.attachObserverForTest(history.toObserver(), notifyCurrentState = false)
        store.dispatch(AppAction.Increment)

        assertEquals(
            listOf<AppState>(
                AppState.Main(count = 0),
                AppState.Main(count = 1),
            ),
            history.stateHistory,
        )
        assertTrue(history.eventHistory.isEmpty())
    }

    @Test
    fun attachObserver_initialStateObserverException_isRethrownAndObserverIsNotRegistered() = runTest(testDispatcher) {
        var observerInvocationCount = 0
        val store = createTestStore(
            stateSaver = StateSaver(
                save = {},
                restore = { AppState.Main(count = 5) },
            ),
        )
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) {
                observerInvocationCount++
                throw IllegalStateException("observer failed during attach")
            }

            override fun onEvent(event: AppEvent) = Unit
        }

        try {
            store.attachObserverForTest(observer)
            fail("Expected attachObserver to rethrow observer exception")
        } catch (t: Throwable) {
            assertIs<IllegalStateException>(t)
        }

        store.dispatch(AppAction.Increment)

        assertEquals(1, observerInvocationCount)
        assertEquals(AppState.Main(count = 6), store.currentState)
    }

    @Test
    fun observerStateException_isHandledWithoutUsingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createTestStore(
            exceptionHandler = ExceptionHandler { error ->
                handledException = error
            },
            errorStateOnException = AppState.Main(count = -1),
        )
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) {
                if (state == AppState.Main(count = 1)) {
                    throw IllegalArgumentException("observer state failed")
                }
            }

            override fun onEvent(event: AppEvent) = Unit
        }

        store.attachObserverForTest(observer)
        store.dispatch(AppAction.Increment)
        testScheduler.runCurrent()

        assertEquals(AppState.Main(count = 1), store.currentState)
        assertIs<IllegalArgumentException>(handledException)
    }

    @Test
    fun observerEventException_isHandledWithoutUsingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createTestStore(
            exceptionHandler = ExceptionHandler { error ->
                handledException = error
            },
            errorStateOnException = AppState.Main(count = -1),
        )
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) = Unit

            override fun onEvent(event: AppEvent) {
                throw IllegalArgumentException("observer event failed")
            }
        }

        store.attachObserverForTest(observer)
        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.EmitEvent)
        testScheduler.runCurrent()

        assertEquals(AppState.Main(count = 1), store.currentState)
        assertIs<IllegalArgumentException>(handledException)
    }

    private fun createTestStore(
        stateSaver: StateSaver<AppState> = StateSaver.Noop(),
        onStart: (suspend () -> Unit)? = null,
        exceptionHandler: ExceptionHandler = ExceptionHandler.Noop,
        errorStateOnException: AppState? = null,
    ): Store<AppState, AppAction, AppEvent> {
        return Store(AppState.Loading) {
            coroutineContext(Dispatchers.Unconfined)
            stateSaver(stateSaver)
            exceptionHandler(exceptionHandler)
            middleware(
                Middleware(
                    onStart = {
                        onStart?.invoke()
                    },
                ),
            )

            state<AppState.Loading> {
                enter {
                    nextState(AppState.Main(count = 0))
                }
            }

            state<AppState.Main> {
                action<AppAction.Increment> {
                    nextState(state.copy(count = state.count + 1))
                }
                action<AppAction.EmitEvent> {
                    event(AppEvent.CountUpdated(count = state.count))
                }
            }

            if (errorStateOnException != null) {
                state<AppState> {
                    error<Throwable> {
                        nextState(errorStateOnException)
                    }
                }
            }
        }
    }

    private class ObservationHistory<S : State, E : Event> {
        val stateHistory = mutableListOf<S>()
        val eventHistory = mutableListOf<E>()

        fun toObserver(): StoreObserver<S, E> = object : StoreObserver<S, E> {
            override fun onState(state: S) {
                stateHistory.add(state)
            }

            override fun onEvent(event: E) {
                eventHistory.add(event)
            }
        }
    }

    @OptIn(InternalTartApi::class)
    private fun Store<AppState, AppAction, AppEvent>.attachObserverForTest(
        observer: StoreObserver<AppState, AppEvent>,
        notifyCurrentState: Boolean = true,
    ) {
        @Suppress("UNCHECKED_CAST")
        val storeInternalApi = this as? StoreInternalApi<AppState, AppAction, AppEvent>
            ?: error("Expected Tart Store to implement StoreInternalApi")
        storeInternalApi.attachObserver(observer, notifyCurrentState)
    }

    @Test
    fun storeObserver_canObserveState() = runTest(testDispatcher) {
        val observedStates = mutableListOf<AppState>()
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) {
                observedStates.add(state)
            }

            override fun onEvent(event: AppEvent) = Unit
        }

        observer.onState(AppState.Loading)

        assertEquals(listOf<AppState>(AppState.Loading), observedStates)
    }

    @Test
    fun storeObserver_canObserveEvent() = runTest(testDispatcher) {
        val observedEvents = mutableListOf<AppEvent>()
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) = Unit

            override fun onEvent(event: AppEvent) {
                observedEvents.add(event)
            }
        }

        observer.onEvent(AppEvent.CountUpdated(count = 1))

        assertEquals(listOf<AppEvent>(AppEvent.CountUpdated(count = 1)), observedEvents)
    }

    @Test
    fun storeObserver_canBeImplementedWithNoopCallbacks() = runTest(testDispatcher) {
        val observer = object : StoreObserver<AppState, AppEvent> {
            override fun onState(state: AppState) = Unit
            override fun onEvent(event: AppEvent) = Unit
        }

        observer.onState(AppState.Loading)
        observer.onEvent(AppEvent.CountUpdated(count = 1))

        assertTrue(true)
    }
}
