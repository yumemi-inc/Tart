package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun attachRecorder_createsAndAttachesRecorder() = runTest(testDispatcher) {
        val store = createTestStore()

        val recorder = store.attachRecorder()
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
}
