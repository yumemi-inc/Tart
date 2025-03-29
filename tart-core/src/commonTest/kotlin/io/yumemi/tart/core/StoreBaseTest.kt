package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreBaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun tartStore_shouldHandleActions() = runTest(testDispatcher) {
        val store = createTestStore(CounterState.Main(0))

        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.Decrement)

        assertEquals(CounterState.Main(1), store.currentState)
    }

    @Test
    fun tartStore_shouldIgnoreActionsInLoadingState() = runTest(testDispatcher) {
        val store = createTestStore(CounterState.Loading)

        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.Decrement)

        assertEquals(CounterState.Loading, store.currentState)
    }

    @Test
    fun tartStore_shouldEmitEvents() = runTest(testDispatcher) {
        val store = createTestStore(CounterState.Main(0))

        var emittedEvent: CounterEvent? = null
        store.collectEvent { event ->
            emittedEvent = event
        }

        store.dispatch(CounterAction.Increment)
        store.dispatch(CounterAction.EmitEvent)

        assertNotNull(emittedEvent)
        assertEquals(CounterEvent.CountUpdated(1), emittedEvent)
    }
}

private sealed interface CounterState : State {
    data object Loading : CounterState
    data class Main(val count: Int) : CounterState
}

private sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object EmitEvent : CounterAction
}

private sealed interface CounterEvent : Event {
    data class CountUpdated(val count: Int) : CounterEvent
}

private fun createTestStore(
    initialState: CounterState,
): Store<CounterState, CounterAction, CounterEvent> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)
        onDispatch { state, action ->
            suspend fun handleMainState(state: CounterState.Main): CounterState = when (action) {
                CounterAction.Increment -> state.copy(count = state.count + 1)
                CounterAction.Decrement -> state.copy(count = state.count - 1)
                CounterAction.EmitEvent -> {
                    emit(CounterEvent.CountUpdated(state.count))
                    state
                }
            }
            when (state) {
                CounterState.Loading -> state
                is CounterState.Main -> handleMainState(state)
            }
        }
    }
}
