package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

// State Transition Diagram:
//
// ┌───────────────────────────────────────────────┐
// │                                               │
// │  Initial ────────► Active ────────► Completed │
// │                                               │
// └───────────────────────────────────────────────┘
//
// Initial: Flow collection not started
// Active: Flow is being collected with current data value
// Completed: Flow collection completed

@OptIn(ExperimentalCoroutinesApi::class)
class FlowCollectUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun flowCollect_collectsItemsAndDispatchesActions() = runTest(testDispatcher) {
        // Create a simple flow that emits integers immediately
        val testFlow = flowOf(1, 2, 3, 4, 5)

        // Create store with the test flow
        val store = createFlowCollectStore(testFlow)

        // Start state check
        assertEquals(FlowState.Initial, store.currentState)

        // Start collecting from flow
        store.dispatch(FlowAction.StartCollecting)

        // With UnconfinedTestDispatcher, the flow should be collected immediately
        assertTrue(
            store.currentState is FlowState.Active,
            "State should be Active, but was ${store.currentState}",
        )

        // Should have the last value
        assertEquals(5, (store.currentState as FlowState.Active).value)

        // Complete the collection
        store.dispatch(FlowAction.Complete)
        assertEquals(FlowState.Completed, store.currentState)
    }

    @Test
    fun flowCollect_cancellingStateStopsCollection() = runTest(testDispatcher) {
        // Create a flow that we can observe collection status
        var collectionCancelled = false
        val testFlow = flow {
            try {
                emit(42)
                // Just hang here until cancelled
                delay(Long.MAX_VALUE)
            } finally {
                collectionCancelled = true
            }
        }

        // Create store with the flow
        val store = createFlowCollectStore(testFlow)

        // Start collecting
        store.dispatch(FlowAction.StartCollecting)

        // Should have received first value
        assertTrue(store.currentState is FlowState.Active)
        assertEquals(42, (store.currentState as FlowState.Active).value)

        // Stop collecting by transitioning away from the state
        store.dispatch(FlowAction.Complete)

        // Collection should have stopped
        assertEquals(FlowState.Completed, store.currentState)

        // Verify collection was cancelled
        assertTrue(collectionCancelled, "Flow collection should have been cancelled")

        // This flow will be cancelled automatically when the test completes
    }
}

// State definitions
private sealed interface FlowState : State {
    data object Initial : FlowState
    data class Active(val value: Int) : FlowState
    data object Completed : FlowState
}

// Action definitions
private sealed interface FlowAction : Action {
    data object StartCollecting : FlowAction
    data class UpdateValue(val value: Int) : FlowAction
    data object Complete : FlowAction
}

// No events needed for this simplified example

private fun createFlowCollectStore(
    dataFlow: Flow<Int>,
    initialState: FlowState = FlowState.Initial,
): Store<FlowState, FlowAction, Nothing> {
    return Store(initialState) {
        // Configure coroutine context for tests
        coroutineContext(Dispatchers.Unconfined)

        // Initial state handling
        state<FlowState.Initial> {
            action<FlowAction.StartCollecting> {
                nextState(FlowState.Active(0)) // Start with a default value before flow collection
            }
        }

        // Active state handling
        state<FlowState.Active> {
            enter {
                // Use launch to collect the flow
                launch {
                    dataFlow.collect { value ->
                        // Dispatch action to update state with the new value
                        dispatch(FlowAction.UpdateValue(value))
                    }
                }
            }

            action<FlowAction.UpdateValue> {
                // Update state with the latest value from flow
                nextState(state.copy(value = action.value))
            }

            action<FlowAction.Complete> {
                nextState(FlowState.Completed)
            }
        }

        // Completed state handling
        state<FlowState.Completed> {
            // No additional behavior needed
        }
    }
}
