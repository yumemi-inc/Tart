package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreBaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    sealed interface AppState : State {
        data object Loading : AppState
        data class Main(val count: Int) : AppState
    }

    sealed interface AppAction : Action {
        data object Increment : AppAction
        data object Decrement : AppAction
        data object EmitEvent : AppAction
    }

    sealed interface AppEvent : Event {
        data class CountUpdated(val count: Int) : AppEvent
    }

    private fun createTestStore(
        initialState: AppState,
    ): Store<AppState, AppAction, AppEvent> {
        return Store(initialState) {
            coroutineContext(Dispatchers.Unconfined)
            state<AppState.Loading> {
                enter {
                    nextState(AppState.Main(count = 0))
                }
            }
            state<AppState.Main> {
                action<AppAction.Increment> {
                    nextState(state.copy(count = state.count + 1))
                }
                action<AppAction.Decrement> {
                    nextState(state.copy(count = state.count - 1))
                }
                action<AppAction.EmitEvent> {
                    event(AppEvent.CountUpdated(state.count))
                }
            }
        }
    }

    @Test
    fun tartStore_shouldHandleActions() = runTest(testDispatcher) {
        val store = createTestStore(AppState.Loading)

        // Store is not started
        assertIs<AppState.Loading>(store.currentState)

        // Accessing state initializes the Store and runs Loading.enter
        assertIs<AppState.Main>(store.state.value)

        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.Decrement)

        assertEquals(AppState.Main(1), store.currentState)
    }

    @Test
    fun tartStore_shouldEmitEvents() = runTest(testDispatcher) {
        val store = createTestStore(AppState.Loading)

        var emittedEvent: AppEvent? = null
        store.collectEvent { event ->
            emittedEvent = event
        }

        store.dispatch(AppAction.Increment)
        store.dispatch(AppAction.EmitEvent)

        assertNotNull(emittedEvent)
        assertEquals(AppEvent.CountUpdated(1), emittedEvent)
    }
}
