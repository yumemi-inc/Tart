package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreSaverTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    data class AppState(val value: Int) : State

    sealed interface AppAction : Action {
        data class Update(val value: Int) : AppAction
    }

    private fun createTestStore(
        initialState: AppState,
        stateSaver: StateSaver<AppState>,
    ): Store<AppState, AppAction, Nothing> {
        return Store(initialState) {
            coroutineContext(Dispatchers.Unconfined)
            stateSaver(stateSaver)
            state<AppState> {
                action<AppAction.Update> {
                    nextState(state.copy(value = action.value))
                }
            }
        }
    }

    @Test
    fun store_shouldUseStateSaverToRestoreState() = runTest(testDispatcher) {
        var savedState = AppState(10)

        val stateSaver = StateSaver(
            save = { state -> savedState = state },
            restore = { savedState },
        )

        val store = createTestStore(AppState(0), stateSaver)

        assertEquals(AppState(10), store.currentState)

        store.dispatch(AppAction.Update(20))

        assertEquals(AppState(20), savedState)
    }
}
