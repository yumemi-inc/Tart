package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreSaverTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun store_shouldUseStateSaverToRestoreState() = runTest(testDispatcher) {
        var savedState = SaverState(10)

        val stateSaver = StateSaver(
            save = { state -> savedState = state },
            restore = { savedState },
        )

        val store = createTestStore(SaverState(0), stateSaver)

        assertEquals(SaverState(10), store.currentState)

        store.dispatch(SaverAction.Update(20))

        assertEquals(SaverState(20), savedState)
    }
}

private data class SaverState(val value: Int) : State

private sealed interface SaverAction : Action {
    data class Update(val value: Int) : SaverAction
}

private fun createTestStore(
    initialState: SaverState,
    stateSaver: StateSaver<SaverState>,
): Store<SaverState, SaverAction, Nothing> {
    return object : Store.Base<SaverState, SaverAction, Nothing>(initialState, Dispatchers.Unconfined) {
        override val stateSaver = stateSaver
        override suspend fun onDispatch(state: SaverState, action: SaverAction): SaverState {
            return when (action) {
                is SaverAction.Update -> SaverState(action.value)
            }
        }
    }
}
