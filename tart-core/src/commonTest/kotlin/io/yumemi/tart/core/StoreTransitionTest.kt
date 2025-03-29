package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 * Tests the login flow state transitions in a Tart Store.
 */

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreTransitionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun tartStore_shouldTransitionBetweenStates() = runTest(testDispatcher) {
        val store = createTestStore(TransitionState.Loading)

        assertEquals(TransitionState.Loading, store.currentState)

        store.state // access state to initialize the store

        assertEquals(TransitionState.Success, store.currentState)
    }

    @Test
    fun tartStore_shouldTransitionToErrorState() = runTest(testDispatcher) {
        val store = createTestStore(TransitionState.Loading)

        store.dispatch(TransitionAction.CauseError)

        assertTrue(store.currentState is TransitionState.Error)
    }

    @Test
    fun tartStore_shouldUseTypedStateHandlers() = runTest(testDispatcher) {
        val store = createTestStoreWithTypedHandlers(TransitionState.Loading)

        store.state // access state to initialize the store

        assertEquals(TransitionState.Success, store.currentState)

        store.dispatch(TransitionAction.CauseError)

        assertTrue(store.currentState is TransitionState.Error)
        assertEquals("typed error", (store.currentState as TransitionState.Error).message)
    }

    @Test
    fun typedHandlers_shouldOnlyReactToSpecifiedTypes() = runTest(testDispatcher) {
        val store = createTypeSpecificStore()

        // In Loading state, only ActionA should trigger a response, not ActionB
        assertEquals(TransitionState.Loading, store.currentState)

        // Dispatching ActionB in Loading state should have no effect (Loading type doesn't react to ActionB)
        store.dispatch(TypedTestAction.ActionB)
        assertEquals(TransitionState.Loading, store.currentState)

        // Dispatching ActionA in Loading state should trigger a transition
        store.dispatch(TypedTestAction.ActionA)
        assertEquals(TransitionState.Success, store.currentState)

        // The same ActionA in Success state should have no effect (Success type doesn't react to ActionA)
        store.dispatch(TypedTestAction.ActionA)
        assertEquals(TransitionState.Success, store.currentState)

        // Dispatching ActionB in Success state should trigger a transition
        store.dispatch(TypedTestAction.ActionB)
        assertEquals(TransitionState.Error("from success"), store.currentState)

        // Error state shouldn't react to any actions (no handler defined for Error type)
        store.dispatch(TypedTestAction.ActionA)
        assertEquals(TransitionState.Error("from success"), store.currentState)
    }
}

private sealed interface TransitionState : State {
    data object Loading : TransitionState
    data object Success : TransitionState
    data class Error(val message: String) : TransitionState
}

private sealed interface TransitionAction : Action {
    data object CauseError : TransitionAction
}

// Action for type specific test
private sealed interface TypedTestAction : Action {
    data object ActionA : TypedTestAction
    data object ActionB : TypedTestAction
}

private fun createTestStore(
    initialState: TransitionState,
): Store<TransitionState, TransitionAction, Nothing> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)
        onEnter<TransitionState> { state ->
            when (state) {
                TransitionState.Loading -> TransitionState.Success
                else -> state
            }
        }

        onDispatch<TransitionState.Loading> { state, action ->
            when (action) {
                TransitionAction.CauseError -> state // This case is not processed
            }
        }

        onDispatch<TransitionState.Success> { state, action ->
            when (action) {
                TransitionAction.CauseError -> throw RuntimeException("error")
            }
        }

        onDispatch<TransitionState.Error> { state, action ->
            // Action processing in error state
            state
        }

        onError<TransitionState> { _, error ->
            TransitionState.Error(error.message ?: "unknown error")
        }
    }
}

private fun createTestStoreWithTypedHandlers(
    initialState: TransitionState,
): Store<TransitionState, TransitionAction, Nothing> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)

        // Using type-specific onEnterState
        onEnter<TransitionState.Loading> { state ->
            TransitionState.Success
        }

        onEnter<TransitionState.Success> { state ->
            state
        }

        // Using type-specific onDispatch
        onDispatch<TransitionState.Success> { state, action ->
            when (action) {
                TransitionAction.CauseError -> throw RuntimeException("typed error")
            }
        }

        // Using type-specific onErrorState
        onError<TransitionState.Success> { state, error ->
            TransitionState.Error(error.message ?: "unknown error")
        }
    }
}

/**
 * Creates a test store to verify that type-specific handlers only react to their respective types
 */
private fun createTypeSpecificStore(): Store<TransitionState, TypedTestAction, Nothing> {
    return Store(TransitionState.Loading) {
        coroutineContext(Dispatchers.Unconfined)

        // Handler for Loading type only reacts to "ActionA"
        onDispatch<TransitionState.Loading> { state, action ->
            when (action) {
                TypedTestAction.ActionA -> TransitionState.Success
                TypedTestAction.ActionB -> state // Does not react to ActionB
            }
        }

        // Handler for Success type only reacts to "ActionB"
        onDispatch<TransitionState.Success> { state, action ->
            when (action) {
                TypedTestAction.ActionB -> TransitionState.Error("from success")
                TypedTestAction.ActionA -> state // Does not react to ActionA
            }
        }

        // No handler defined for Error type
        // This allows us to test that the Error state doesn't react to any actions
    }
}
