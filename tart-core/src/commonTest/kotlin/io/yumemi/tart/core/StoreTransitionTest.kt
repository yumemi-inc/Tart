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

        assertEquals(TransitionState.Success, store.state.value)
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
    return object : Store.Base<TransitionState, TransitionAction, Nothing>(initialState, Dispatchers.Unconfined) {
        override suspend fun onEnter(state: TransitionState): TransitionState {
            return when (state) {
                TransitionState.Loading -> TransitionState.Success
                else -> state
            }
        }

        override suspend fun onDispatch(state: TransitionState, action: TransitionAction): TransitionState {
            return when (state) {
                TransitionState.Success -> {
                    when (action) {
                        TransitionAction.CauseError -> throw RuntimeException("error")
                    }
                }

                else -> state
            }
        }

        override suspend fun onError(state: TransitionState, error: Throwable): TransitionState {
            return TransitionState.Error(error.message ?: "unknown error")
        }
    }
}
