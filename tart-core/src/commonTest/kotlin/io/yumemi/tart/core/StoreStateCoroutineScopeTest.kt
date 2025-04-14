package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
// ┌───────────────────────────────────────────┐
// │                                           │
// │  Initial ─────────► Running ────► Final   │
// │                       │                   │
// │                       │                   │
// │                       ▼                   │
// │                  (Background              │
// │                   Operations)             │
// │                                           │
// └───────────────────────────────────────────┘
//
// Initial: Starting state, no background work
// Running: Active state with a background coroutine
// Final: End state, background work is cancelled

@OptIn(ExperimentalCoroutinesApi::class)
class StoreStateCoroutineScopeTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun enterCoroutineScope_dispatchesActionsAndEmitsEvents() = runTest(testDispatcher) {
        // Create store with test dispatcher
        val store = createBasicStore()

        // Initial state check
        assertEquals(StateScopeState.Initial, store.currentState)

        // Transition to Running state
        store.dispatch(StateScopeAction.Start)

        // Verify state transition occurred
        assertTrue(store.currentState is StateScopeState.Running)

        // Verify background task updated state via action dispatch
        val runningState = store.currentState as StateScopeState.Running
        assertEquals(5, runningState.value)

        // Move to final state
        store.dispatch(StateScopeAction.Stop)

        // Verify final state
        assertEquals(StateScopeState.Final, store.currentState)
    }

    @Test
    fun enterCoroutineScope_cancelsBackgroundTaskOnStateTransition() = runTest(testDispatcher) {
        // Flags to track background task execution
        var backgroundTaskRan = false
        var backgroundTaskCancelled = false

        // Create store with custom handling
        val store = createCancellationTestStore(
            onBackgroundTaskStart = { backgroundTaskRan = true },
            onBackgroundTaskCancel = { backgroundTaskCancelled = true },
        )

        // Start sequence
        assertEquals(StateScopeState.Initial, store.currentState)
        store.dispatch(StateScopeAction.Start)
        assertEquals(StateScopeState.Running::class, store.currentState::class)

        // Verify background task started
        assertTrue(backgroundTaskRan, "Background task should have started")

        // Immediately transition to final state
        store.dispatch(StateScopeAction.Stop)
        assertEquals(StateScopeState.Final, store.currentState)

        // Verify background task was cancelled
        assertTrue(backgroundTaskCancelled, "Background task should have been cancelled")
    }
}

// State definitions
private sealed interface StateScopeState : State {
    data object Initial : StateScopeState
    data class Running(val value: Int = 0) : StateScopeState
    data object Final : StateScopeState
}

// Action definitions
private sealed interface StateScopeAction : Action {
    data object Start : StateScopeAction
    data object Stop : StateScopeAction
    data class Update(val newValue: Int) : StateScopeAction
}

// Event definitions
private sealed interface StateScopeEvent : Event {
    data class ValueChanged(val value: Int) : StateScopeEvent
    data object Completed : StateScopeEvent
}

// Store factory for basic functionality test
private fun createBasicStore(): Store<StateScopeState, StateScopeAction, StateScopeEvent> {
    return Store(StateScopeState.Initial) {
        // Configure to use Unconfined dispatcher
        coroutineContext(Dispatchers.Unconfined)

        // Initial state handling
        state<StateScopeState.Initial> {
            action<StateScopeAction.Start> {
                nextState(StateScopeState.Running())
            }
        }

        // Running state with background operations
        state<StateScopeState.Running> {
            enter {
                // Launch background task with launch function
                launch {
                    // Update state via action
                    dispatch(StateScopeAction.Update(5))

                    // Emit event
                    event(StateScopeEvent.ValueChanged(5))
                }
            }

            // Update action handling
            action<StateScopeAction.Update> {
                nextState(StateScopeState.Running(action.newValue))
            }

            // Stop action transitions to Final
            action<StateScopeAction.Stop> {
                nextState(StateScopeState.Final)
            }
        }

        // Final state
        state<StateScopeState.Final> {
            enter {
                event(StateScopeEvent.Completed)
            }
        }
    }
}

// Store factory for cancellation test
private fun createCancellationTestStore(
    onBackgroundTaskStart: () -> Unit,
    onBackgroundTaskCancel: () -> Unit,
): Store<StateScopeState, StateScopeAction, StateScopeEvent> {
    return Store(StateScopeState.Initial) {
        coroutineContext(Dispatchers.Unconfined)

        state<StateScopeState.Initial> {
            action<StateScopeAction.Start> {
                nextState(StateScopeState.Running())
            }
        }

        state<StateScopeState.Running> {
            enter {
                launch {
                    try {
                        onBackgroundTaskStart()

                        // Long-running task that should be cancelled
                        delay(10000)

                        // We shouldn't reach this
                        throw IllegalStateException("Background task wasn't cancelled")
                    } catch (e: Exception) {
                        onBackgroundTaskCancel()
                    }
                }
            }

            action<StateScopeAction.Stop> {
                nextState(StateScopeState.Final)
            }
        }

        state<StateScopeState.Final> {
            // Empty final state
        }
    }
}
