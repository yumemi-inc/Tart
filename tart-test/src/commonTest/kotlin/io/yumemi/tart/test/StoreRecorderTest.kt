package io.yumemi.tart.test

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.ExperimentalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import io.yumemi.tart.core.StoreObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalTartApi::class, ExperimentalCoroutinesApi::class)
class StoreRecorderTest {

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

    private fun createTestStore(): Store<AppState, AppAction, AppEvent> {
        return Store(AppState.Loading) {
            coroutineContext(Dispatchers.Unconfined)
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
                    event(AppEvent.CountUpdated(state.count))
                }
            }
        }
    }

    @Test
    fun storeRecorder_recordsStateHistoryAndEventHistory() {
        val recorder = StoreRecorder<AppState, AppEvent>()

        recorder.onState(AppState.Loading)
        recorder.onState(AppState.Main(count = 0))
        recorder.onEvent(AppEvent.CountUpdated(count = 0))
        recorder.onState(AppState.Main(count = 1))
        recorder.onEvent(AppEvent.CountUpdated(count = 1))

        assertEquals(
            listOf(
                AppState.Loading,
                AppState.Main(count = 0),
                AppState.Main(count = 1),
            ),
            recorder.states,
        )
        assertEquals(
            listOf(
                AppEvent.CountUpdated(count = 0),
                AppEvent.CountUpdated(count = 1),
            ),
            recorder.events,
        )
    }

    @Test
    fun recorder_clear_resetsRecordedHistory() {
        val recorder = StoreRecorder<AppState, AppEvent>()

        recorder.onState(AppState.Main(count = 9))
        recorder.onEvent(AppEvent.CountUpdated(count = 9))

        assertTrue(recorder.states.isNotEmpty())
        assertTrue(recorder.events.isNotEmpty())
        recorder.clear()

        assertTrue(recorder.states.isEmpty())
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun createRecorder_createsAndAttachesRecorder() = runTest(testDispatcher) {
        val store = createTestStore()

        val recorder = store.createRecorder()
        store.dispatchAndWait(AppAction.Increment)
        store.dispatchAndWait(AppAction.EmitEvent)

        assertEquals(
            listOf(
                AppState.Loading,
                AppState.Main(count = 0),
                AppState.Main(count = 1),
            ),
            recorder.states,
        )
        assertEquals(
            listOf(
                AppEvent.CountUpdated(count = 1),
            ),
            recorder.events,
        )
    }

    @Test
    fun extensions_throwForStoresThatDoNotImplementTestingInterfaces() = runTest(testDispatcher) {
        val store = FakeStore()

        assertFailsWith<IllegalStateException> {
            store.attachObserver(
                object : StoreObserver<AppState, AppEvent> {
                    override fun onState(state: AppState) = Unit
                    override fun onEvent(event: AppEvent) = Unit
                },
            )
        }
        try {
            store.dispatchAndWait(AppAction.Increment)
            fail("Expected dispatchAndWait to fail for stores without StoreInternalApi support")
        } catch (_: IllegalStateException) {
        }
    }

    private class FakeStore :
        Store<AppState, AppAction, AppEvent> {
        override val state: StateFlow<AppState> = MutableStateFlow(AppState.Loading)
        override val event: Flow<AppEvent> = emptyFlow()
        override val currentState: AppState = AppState.Loading

        override fun dispatch(action: AppAction) = Unit

        override fun collectState(state: (AppState) -> Unit) = Unit

        override fun collectEvent(event: (AppEvent) -> Unit) = Unit

        override fun close() = Unit
    }
}
