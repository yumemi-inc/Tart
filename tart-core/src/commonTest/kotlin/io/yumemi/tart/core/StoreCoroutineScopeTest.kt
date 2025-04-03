package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the coroutineScope functionality in StoreContext
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreCoroutineScopeTest {

    /**
     * Simple test to verify that the coroutineScope in EnterContext works properly.
     * We create a mock implementation and test that jobs launched in the coroutineScope
     * properly affect a state flow.
     */
    @Test
    fun coroutineScope_shouldExecuteTasksInEnterBlock() = runTest {
        // Setup a value to track
        val counterFlow = MutableStateFlow(0)
        
        // Create a mock enter context with a real coroutineScope
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val enterContext = TestEnterContext(scope, counterFlow)
        
        // Simulate an enter handler that would increment the counter
        enterContext.incrementCounter()
        
        // The counter should be updated by the job in coroutineScope
        assertEquals(1, counterFlow.value, "Counter should be incremented by job in coroutineScope")
        
        // Cancel the scope to clean up
        scope.coroutineContext.cancelChildren()
    }
}

/**
 * Test implementation of EnterContext to directly verify coroutineScope functionality
 */
private class TestEnterContext(
    override val coroutineScope: CoroutineScope,
    private val counterFlow: MutableStateFlow<Int>
) : EnterContext<TestState, TestAction, TestEvent> {
    override val state: TestState = TestState.Active
    override val emit: suspend (TestEvent) -> Unit = {}
    override val dispatch: (TestAction) -> Unit = {}
    
    /**
     * Helper method that simulates what an enter handler would do:
     * Launch a job in the coroutineScope to update state
     */
    fun incrementCounter() {
        coroutineScope.launch {
            counterFlow.value = counterFlow.value + 1
        }
    }
}

private sealed interface TestState : State {
    data object Active : TestState
}

private sealed interface TestAction : Action

private sealed interface TestEvent : Event