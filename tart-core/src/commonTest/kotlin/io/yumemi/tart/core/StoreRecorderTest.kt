package io.yumemi.tart.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTartApi::class)
class StoreRecorderTest {

    sealed interface AppState : State {
        data object Loading : AppState
        data class Main(val count: Int) : AppState
    }

    sealed interface AppEvent : Event {
        data class CountUpdated(val count: Int) : AppEvent
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
}
