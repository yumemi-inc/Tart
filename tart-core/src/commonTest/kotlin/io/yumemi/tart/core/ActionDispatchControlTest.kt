package io.yumemi.tart.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ActionDispatchControlTest {

    data class AppState(
        val latestQuery: String = "",
        val submitCount: Int = 0,
        val incrementCount: Int = 0,
    ) : State

    sealed interface AppAction : Action {
        data class Search(val query: String) : AppAction
        data object Submit : AppAction
        data object Increment : AppAction
    }

    private fun createStore(dispatcher: TestDispatcher): Store<AppState, AppAction, Nothing> {
        return Store(AppState()) {
            coroutineContext(dispatcher)
            debounceAction<AppAction.Search>(timeoutMillis = 300)
            throttleAction<AppAction.Increment>(timeoutMillis = 500)

            state<AppState> {
                action<AppAction.Search> {
                    nextState(state.copy(latestQuery = action.query))
                }
                action<AppAction.Submit> {
                    nextState(state.copy(submitCount = state.submitCount + 1))
                }
                action<AppAction.Increment> {
                    nextState(state.copy(incrementCount = state.incrementCount + 1))
                }
            }
        }
    }

    @Test
    fun debounceAction_shouldDispatchOnlyLatestAction() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(dispatcher)

        store.dispatch(AppAction.Search("h"))
        runCurrent()
        advanceTimeBy(100)
        store.dispatch(AppAction.Search("he"))
        runCurrent()
        advanceTimeBy(100)
        store.dispatch(AppAction.Search("hello"))
        runCurrent()

        assertEquals("", store.currentState.latestQuery)

        store.dispatch(AppAction.Submit)
        runCurrent()
        assertEquals(1, store.currentState.submitCount)

        advanceTimeBy(299)
        runCurrent()
        assertEquals("", store.currentState.latestQuery)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("hello", store.currentState.latestQuery)
    }

    @Test
    fun throttleAction_shouldAllowAtMostOneActionPerWindow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(dispatcher)

        store.dispatch(AppAction.Increment)
        runCurrent()
        store.dispatch(AppAction.Increment)
        runCurrent()
        store.dispatch(AppAction.Increment)
        runCurrent()
        assertEquals(1, store.currentState.incrementCount)

        advanceTimeBy(499)
        store.dispatch(AppAction.Increment)
        runCurrent()
        assertEquals(1, store.currentState.incrementCount)

        advanceTimeBy(1)
        store.dispatch(AppAction.Increment)
        runCurrent()
        assertEquals(2, store.currentState.incrementCount)
    }
}
