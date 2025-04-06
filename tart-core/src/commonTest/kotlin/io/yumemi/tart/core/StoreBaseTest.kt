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
        val store = createTestStore(BaseState.Loading)

        store.dispatch(BaseAction.Increment)
        store.dispatch(BaseAction.Increment)
        store.dispatch(BaseAction.Decrement)

        assertEquals(BaseState.Main(1), store.currentState)
    }

    @Test
    fun tartStore_shouldEmitEvents() = runTest(testDispatcher) {
        val store = createTestStore(BaseState.Loading)

        var emittedEvent: BaseEvent? = null
        store.collectEvent { event ->
            emittedEvent = event
        }

        store.dispatch(BaseAction.Increment)
        store.dispatch(BaseAction.EmitEvent)

        assertNotNull(emittedEvent)
        assertEquals(BaseEvent.CountUpdated(1), emittedEvent)
    }
}

private sealed interface BaseState : State {
    data object Loading : BaseState
    data class Main(val count: Int) : BaseState
}

private sealed interface BaseAction : Action {
    data object Increment : BaseAction
    data object Decrement : BaseAction
    data object EmitEvent : BaseAction
}

private sealed interface BaseEvent : Event {
    data class CountUpdated(val count: Int) : BaseEvent
}

private fun createTestStore(
    initialState: BaseState,
): Store<BaseState, BaseAction, BaseEvent> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)
        state<BaseState.Loading> {
            enter {
                state(BaseState.Main(count = 0))
            }
        }
        state<BaseState.Main> {
            action<BaseAction.Increment> {
                state(state.copy(count = state.count + 1))
            }
            action<BaseAction.Decrement> {
                state(state.copy(count = state.count - 1))
            }
            action<BaseAction.EmitEvent> {
                event(BaseEvent.CountUpdated(state.count))
            }
        }
    }
}
