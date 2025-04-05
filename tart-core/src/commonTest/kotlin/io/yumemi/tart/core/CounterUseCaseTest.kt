package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

// State Transition Diagram:
//
// ┌─────────────────────────────────────────┐
// │                                         │
// │  Initial ─────────► Active ───────┐     │
// │                        ▲          │     │
// │                        │          │     │
// │                        │          │     │
// │                        │          ▼     │
// │  Error ◄── Any State   │        Paused  │
// │                        │          │     │
// │                        └──────────┘     │
// │                                         │
// └─────────────────────────────────────────┘
//
// Initial: Counter not started yet
// Active: Counter is running and can be incremented/decremented
// Paused: Counter is temporarily paused but retains its value
// Error: An error occurred during counter operation

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CounterUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun counter_initialState() = runTest(testDispatcher) {
        val store = createCounterStore()
        assertEquals(CounterState.Initial, store.currentState)
    }

    @Test
    fun counter_startShouldTransitionToActive() = runTest(testDispatcher) {
        val store = createCounterStore()

        store.dispatch(CounterAction.Start)

        assertTrue(store.currentState is CounterState.Active)
        assertEquals(0, (store.currentState as CounterState.Active).count)
    }

    @Test
    fun counter_incrementAction() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(0))

        store.dispatch(CounterAction.Increment)

        assertEquals(CounterState.Active(1), store.currentState)
    }

    @Test
    fun counter_decrementAction() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(5))

        store.dispatch(CounterAction.Decrement)

        assertEquals(CounterState.Active(4), store.currentState)
    }

    @Test
    fun counter_resetAction() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(10))

        store.dispatch(CounterAction.Reset)

        assertEquals(CounterState.Active(0), store.currentState)
    }

    @Test
    fun counter_pauseAction() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(3))

        store.dispatch(CounterAction.Pause)

        assertTrue(store.currentState is CounterState.Paused)
        assertEquals(3, (store.currentState as CounterState.Paused).count)
    }

    @Test
    fun counter_resumeAction() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Paused(7))

        store.dispatch(CounterAction.Resume)

        assertTrue(store.currentState is CounterState.Active)
        assertEquals(7, (store.currentState as CounterState.Active).count)
    }

    @Test
    fun counter_errorHandling() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(0))

        // Force an error by dispatching a special action
        store.dispatch(CounterAction.ForceError("Test error"))

        assertTrue(store.currentState is CounterState.Error)
        assertEquals("Test error", (store.currentState as CounterState.Error).message)
    }

    @Test
    fun counter_eventEmission() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(5))

        var capturedEvent: CounterEvent? = null
        store.collectEvent { event ->
            capturedEvent = event
        }

        store.dispatch(CounterAction.Increment)

        assertNotNull(capturedEvent)
        assertTrue(capturedEvent is CounterEvent.CountChanged)
        assertEquals(6, (capturedEvent as CounterEvent.CountChanged).newCount)
    }

    @Test
    fun counter_thresholdReached() = runTest(testDispatcher) {
        val store = createCounterStore(CounterState.Active(9))

        var thresholdEvent: CounterEvent.ThresholdReached? = null
        store.collectEvent { event ->
            if (event is CounterEvent.ThresholdReached) {
                thresholdEvent = event
            }
        }

        store.dispatch(CounterAction.Increment) // This should reach 10

        assertNotNull(thresholdEvent)
        assertEquals(10, thresholdEvent?.threshold)
    }
}

// State definitions
private sealed interface CounterState : State {
    data object Initial : CounterState
    data class Active(val count: Int) : CounterState
    data class Paused(val count: Int) : CounterState
    data class Error(val message: String) : CounterState
}

// Action definitions
private sealed interface CounterAction : Action {
    data object Start : CounterAction
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset : CounterAction
    data object Pause : CounterAction
    data object Resume : CounterAction
    data class ForceError(val message: String) : CounterAction
}

// Event definitions
private sealed interface CounterEvent : Event {
    data class CountChanged(val newCount: Int) : CounterEvent
    data class ThresholdReached(val threshold: Int) : CounterEvent
    data class ErrorOccurred(val message: String) : CounterEvent
}

private fun createCounterStore(
    initialState: CounterState = CounterState.Initial,
): Store<CounterState, CounterAction, CounterEvent> {
    return Store(initialState) {
        // Configure coroutine context for tests
        coroutineContext(Dispatchers.Unconfined)

        // Initial state handling
        state<CounterState.Initial> {
            action<CounterAction.Start> {
                CounterState.Active(0)
            }
        }

        // Active state handling
        state<CounterState.Active> {
            enter {
                // Optional initialization when entering active state
                emit(CounterEvent.CountChanged(state.count))
            }

            action<CounterAction.Increment> {
                val newCount = state.count + 1
                emit(CounterEvent.CountChanged(newCount))

                // Emit threshold event if count reaches certain value
                if (newCount == 10) {
                    emit(CounterEvent.ThresholdReached(10))
                }

                state.copy(count = newCount)
            }

            action<CounterAction.Decrement> {
                val newCount = state.count - 1
                emit(CounterEvent.CountChanged(newCount))
                state.copy(count = newCount)
            }

            action<CounterAction.Reset> {
                emit(CounterEvent.CountChanged(0))
                state.copy(count = 0)
            }

            action<CounterAction.Pause> {
                CounterState.Paused(state.count)
            }
        }

        // Paused state handling
        state<CounterState.Paused> {
            action<CounterAction.Resume> {
                CounterState.Active(state.count)
            }
        }

        // Global error handling
        state<CounterState> {
            action<CounterAction.ForceError> {
                throw RuntimeException(action.message)
            }
            error {
                emit(CounterEvent.ErrorOccurred(error.message ?: "Unknown error"))
                CounterState.Error(error.message ?: "Unknown error")
            }
        }
    }
}
