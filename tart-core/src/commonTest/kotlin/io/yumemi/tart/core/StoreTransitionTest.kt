package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}

private sealed interface TransitionState : State {
    data object Loading : TransitionState
    data object Success : TransitionState
    data class Error(val message: String) : TransitionState
}

private sealed interface TransitionAction : Action {
    data object CauseError : TransitionAction
}

private fun createTestStore(
    initialState: TransitionState,
): Store<TransitionState, TransitionAction, Nothing> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)
        onEnter { state ->
            when (state) {
                TransitionState.Loading -> TransitionState.Success
                else -> state
            }
        }
        onDispatch { state, action ->
            when (state) {
                TransitionState.Success -> {
                    when (action) {
                        TransitionAction.CauseError -> throw RuntimeException("error")
                    }
                }

                else -> state
            }
        }
        onError { _, error ->
            TransitionState.Error(error.message ?: "unknown error")
        }
    }
}
