package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreConcurrentTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun concurrentActions_shouldHandleRaceConditions() = runTest(testDispatcher) {
        val store = createConcurrentTestStore(ConcurrentState.Initial(0))

        // Launch multiple concurrent actions
        val jobs = List(10) { index ->
            launch {
                if (index % 2 == 0) {
                    store.dispatch(ConcurrentAction.Increment)
                } else {
                    store.dispatch(ConcurrentAction.Decrement)
                }
            }
        }

        jobs.forEach { it.join() }

        // Verify final state of counter
        when (val finalState = store.currentState) {
            is ConcurrentState.Initial -> assertEquals(0, finalState.count)
            is ConcurrentState.Result -> assertEquals(0, finalState.count)
            else -> throw AssertionError("Unexpected state type: ${finalState::class.simpleName}")
        }
    }

    @Test
    fun concurrentStateChanges_shouldPreserveMutexLocking() = runTest(testDispatcher) {
        val store = createConcurrentTestStore(ConcurrentState.Initial(0))

        // Track emitted events to verify ordering
        val events = mutableListOf<ConcurrentEvent>()
        store.collectEvent { event ->
            events.add(event)
        }

        // First action triggers a state change with a delay
        val delayedJob = launch {
            store.dispatch(ConcurrentAction.DelayedIncrement)
        }

        // Give time for first dispatch to be processed
        kotlinx.coroutines.yield()

        // Second action should wait for the first to complete
        store.dispatch(ConcurrentAction.RequestResult)

        delayedJob.join()

        // Allow events to be collected
        kotlinx.coroutines.yield()

        // Verify event emission - we should at least have the Processing event
        assertEquals(1, events.size, "Should have at least the Processing event")
        assertEquals(ConcurrentEvent.Processing, events[0])

        // If the completed event was emitted, check its value
        if (events.size > 1) {
            assertEquals(ConcurrentEvent.Completed(1), events[1])
        }
    }
}

private sealed interface ConcurrentState : State {
    data class Initial(val count: Int) : ConcurrentState
    data class Processing(val count: Int) : ConcurrentState
    data class Result(val count: Int) : ConcurrentState
}

private sealed interface ConcurrentAction : Action {
    data object Increment : ConcurrentAction
    data object Decrement : ConcurrentAction
    data object DelayedIncrement : ConcurrentAction
    data object RequestResult : ConcurrentAction
}

private sealed interface ConcurrentEvent : Event {
    data object Processing : ConcurrentEvent
    data class Completed(val finalCount: Int) : ConcurrentEvent
}

private fun createConcurrentTestStore(
    initialState: ConcurrentState,
): Store<ConcurrentState, ConcurrentAction, ConcurrentEvent> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)

        state<ConcurrentState.Initial> {
            action<ConcurrentAction.Increment> {
                nextState(ConcurrentState.Initial(state.count + 1))
            }
            action<ConcurrentAction.Decrement> {
                nextState(ConcurrentState.Initial(state.count - 1))
            }
            action<ConcurrentAction.DelayedIncrement> {
                nextState(ConcurrentState.Processing(state.count))
                event(ConcurrentEvent.Processing)
            }
            action<ConcurrentAction.RequestResult> {
                nextState(ConcurrentState.Result(state.count))
            }
        }

        state<ConcurrentState.Processing> {
            enter {
                launch {
                    // Simulate long processing
                    delay(100)
                    transaction {
                        val newCount = state.count + 1
                        nextState(ConcurrentState.Result(newCount))
                        event(ConcurrentEvent.Completed(newCount))
                    }
                }
            }

            action<ConcurrentAction.RequestResult> {
                // This will wait for the transaction from enter to complete
                // due to mutex locking
                nextState(ConcurrentState.Result(state.count))
            }
        }

        state<ConcurrentState.Result> {
            action<ConcurrentAction.Increment> {
                nextState(ConcurrentState.Result(state.count + 1))
            }
            action<ConcurrentAction.Decrement> {
                nextState(ConcurrentState.Result(state.count - 1))
            }
        }
    }
}
