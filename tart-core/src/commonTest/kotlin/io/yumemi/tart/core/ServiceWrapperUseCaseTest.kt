package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

// State Transition Diagram:
//
// ┌──────────────────────────┐
// │                          │
// │    Idle ────► Running    │
// │     ▲            │       │
// │     │            │       │
// │     └────────────┘       │
// │                          │
// └──────────────────────────┘
//
// Idle: Initial state, service not started
// Running: Service is active, receiving callbacks

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ServiceWrapperUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun serviceWrapper_shouldStartAndHandleCallbacks() = runTest(testDispatcher) {
        // Mock implementation of MyService
        val mockService = MockMyService()
        val serviceWrapper = MyServiceMonitor(mockService)

        // Create store with idle state
        val store = createTestStore(serviceWrapper)

        // Initial state should be Idle
        assertIs<CounterServiceState.Idle>(store.currentState)

        // Start the service
        store.dispatch(CounterServiceAction.Start)

        // State should change to Running with count 0
        assertIs<CounterServiceState.Running>(store.currentState)
        assertEquals(0, (store.currentState as CounterServiceState.Running).count)

        // Trigger callback from service with count update
        mockService.triggerCallbackForTest(5)

        // State should update with new count
        assertEquals(5, (store.currentState as CounterServiceState.Running).count)

        // Stop the service
        store.dispatch(CounterServiceAction.Stop)

        // State should return to Idle
        assertIs<CounterServiceState.Idle>(store.currentState)
    }
}

private interface MyService {
    fun register(listener: Listener)
    fun unRegister(listener: Listener)
    interface Listener {
        fun onCountUpdated(count: Int)
    }
}

// Mock implementation for testing
private class MockMyService : MyService {
    private val listeners = mutableListOf<MyService.Listener>()

    override fun register(listener: MyService.Listener) {
        listeners.add(listener)
    }

    override fun unRegister(listener: MyService.Listener) {
        listeners.remove(listener)
    }

    fun triggerCallbackForTest(count: Int) {
        listeners.forEach { it.onCountUpdated(count) }
    }
}

private class MyServiceMonitor(private val myService: MyService) {
    val count: Flow<Int> = callbackFlow {
        val listener = object : MyService.Listener {
            override fun onCountUpdated(count: Int) {
                trySend(count)
            }
        }

        myService.register(listener)

        awaitClose {
            myService.unRegister(listener)
        }
    }
}

private sealed interface CounterServiceState : State {
    data object Idle : CounterServiceState
    data class Running(val count: Int) : CounterServiceState
}

private sealed interface CounterServiceAction : Action {
    data object Start : CounterServiceAction
    data class UpdateCount(val count: Int) : CounterServiceAction
    data object Stop : CounterServiceAction
}

private fun createTestStore(
    myServiceMonitor: MyServiceMonitor,
): Store<CounterServiceState, CounterServiceAction, Nothing> {
    return Store {
        initialState(CounterServiceState.Idle)
        coroutineContext(Dispatchers.Unconfined)

        state<CounterServiceState.Idle> {
            action<CounterServiceAction.Start> {
                nextState(CounterServiceState.Running(count = 0))
            }
        }

        state<CounterServiceState.Running> {
            enter {
                launch {
                    myServiceMonitor.count.collect {
                        dispatch(CounterServiceAction.UpdateCount(it))
                    }
                }
            }

            action<CounterServiceAction.UpdateCount> {
                nextState(CounterServiceState.Running(count = action.count))
            }

            action<CounterServiceAction.Stop> {
                nextState(CounterServiceState.Idle)
            }
        }
    }
}
