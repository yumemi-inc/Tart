package io.yumemi.tart.core

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

        store.attachObserver(observer)

        assertFalse(started)
        assertEquals(listOf<AppState>(AppState.Main(count = 5)), history.stateHistory)
        assertTrue(history.eventHistory.isEmpty())
    }

    @Test
    fun attachObserver_recordsStateChangesAndEvents() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()
        val observer = history.toObserver()

        store.attachObserver(observer)
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

        store.attachObserver(firstHistory.toObserver())
        store.attachObserver(secondHistory.toObserver())
        store.dispatch(AppAction.EmitEvent)

        assertEquals(firstHistory.stateHistory, secondHistory.stateHistory)
        assertEquals(firstHistory.eventHistory, secondHistory.eventHistory)
    }

    @Test
    fun attachObserver_isAllowedAfterCollectingEventsBeforeStart() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()

        store.collectEvent { }
        store.attachObserver(history.toObserver())
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
            store.attachObserver(StoreObserver())
            fail("Expected attachObserver to fail after store start")
        } catch (t: Throwable) {
            assertIs<IllegalStateException>(t)
        }
    }

    @Test
    fun attachObserver_canSkipInitialCurrentStateSnapshot() = runTest(testDispatcher) {
        val store = createTestStore()
        val history = ObservationHistory<AppState, AppEvent>()

        store.attachObserver(history.toObserver(), notifyCurrentState = false)
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
        val observer = StoreObserver<AppState, AppEvent>(
            onState = {
                observerInvocationCount++
                throw IllegalStateException("observer failed during attach")
            },
        )

        try {
            store.attachObserver(observer)
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
        val observer = StoreObserver<AppState, AppEvent>(
            onState = { state ->
                if (state == AppState.Main(count = 1)) {
                    throw IllegalArgumentException("observer state failed")
                }
            },
        )

        store.attachObserver(observer)
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
        val observer = StoreObserver<AppState, AppEvent>(
            onEvent = {
                throw IllegalArgumentException("observer event failed")
            },
        )

        store.attachObserver(observer)
        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.EmitEvent)
        testScheduler.runCurrent()

        assertEquals(AppState.Main(count = 1), store.currentState)
        assertIs<IllegalArgumentException>(handledException)
    }

    private fun createTestStore(
        stateSaver: StateSaver<AppState> = StateSaver.Noop(),
        onStart: (() -> Unit)? = null,
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

        fun toObserver(): StoreObserver<S, E> = StoreObserver(
            onState = { state ->
                stateHistory.add(state)
            },
            onEvent = { event ->
                eventHistory.add(event)
            },
        )
    }

    @Test
    fun storeObserverFactory_canCreateStateOnlyObserver() = runTest(testDispatcher) {
        val observedStates = mutableListOf<AppState>()
        val observer = StoreObserver<AppState, AppEvent>(
            onState = { state ->
                observedStates.add(state)
            },
        )

        observer.onState(AppState.Loading)

        assertEquals(listOf<AppState>(AppState.Loading), observedStates)
    }

    @Test
    fun storeObserverFactory_canCreateEventOnlyObserver() = runTest(testDispatcher) {
        val observedEvents = mutableListOf<AppEvent>()
        val observer = StoreObserver<AppState, AppEvent>(
            onEvent = { event ->
                observedEvents.add(event)
            },
        )

        observer.onEvent(AppEvent.CountUpdated(count = 1))

        assertEquals(listOf<AppEvent>(AppEvent.CountUpdated(count = 1)), observedEvents)
    }

    @Test
    fun storeObserverFactory_defaultsToNoopCallbacks() = runTest(testDispatcher) {
        val observer = StoreObserver<AppState, AppEvent>()

        observer.onState(AppState.Loading)
        observer.onEvent(AppEvent.CountUpdated(count = 1))

        assertTrue(true)
    }
}
