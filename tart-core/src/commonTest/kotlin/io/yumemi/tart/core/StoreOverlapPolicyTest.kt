package io.yumemi.tart.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoreOverlapPolicyTest {

    sealed interface AppState : State {
        data class Active(val value: Int = 0) : AppState
        data object Completed : AppState
    }

    sealed interface AppAction : Action {
        data class CancelPreviousDefault(val marker: Int) : AppAction
        data object KeyedLaunches : AppAction
        data object SharedDefaultKey : AppAction
        data class DropIfRunningAdd(val delta: Int) : AppAction
        data class LatestAdd(val delta: Int) : AppAction
        data object MoveToCompleted : AppAction
    }

    private fun createStore(
        testDispatcher: TestDispatcher,
        onCancelPreviousStart: ((Int) -> Unit)? = null,
        onCancelPreviousCancel: ((Int) -> Unit)? = null,
    ): Store<AppState, AppAction, Nothing> {
        return Store(AppState.Active()) {
            coroutineContext(testDispatcher)

            state<AppState.Active> {
                action<AppAction.CancelPreviousDefault> {
                    launch(
                        coroutineDispatcher = testDispatcher,
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        onCancelPreviousStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancelPreviousCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.KeyedLaunches> {
                    launch(
                        coroutineDispatcher = testDispatcher,
                        key = "primary",
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 1))
                        }
                    }
                    launch(
                        coroutineDispatcher = testDispatcher,
                        key = "secondary",
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 10))
                        }
                    }
                }

                action<AppAction.SharedDefaultKey> {
                    launch(
                        coroutineDispatcher = testDispatcher,
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 1))
                        }
                    }
                    launch(
                        coroutineDispatcher = testDispatcher,
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 10))
                        }
                    }
                }

                action<AppAction.DropIfRunningAdd> {
                    launch(
                        coroutineDispatcher = testDispatcher,
                        policy = OverlapPolicy.DROP_IF_RUNNING,
                    ) {
                        delay(100)
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + action.delta))
                        }
                    }
                }

                action<AppAction.LatestAdd> {
                    launch(
                        coroutineDispatcher = testDispatcher,
                        policy = OverlapPolicy.CANCEL_PREVIOUS,
                    ) {
                        delay(100)
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + action.delta))
                        }
                    }
                }

                action<AppAction.MoveToCompleted> {
                    nextState(AppState.Completed)
                }
            }
        }
    }

    @Test
    fun launchPolicy_cancelPreviousCancelsMatchingKeyAcrossDispatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancelPreviousStart = { started += it },
            onCancelPreviousCancel = { cancelled += it },
        )

        store.dispatch(AppAction.CancelPreviousDefault(marker = 1))
        runCurrent()

        assertEquals(listOf(1), started)
        assertEquals(emptyList(), cancelled)

        store.dispatch(AppAction.CancelPreviousDefault(marker = 2))
        runCurrent()

        assertEquals(listOf(1, 2), started)
        assertEquals(listOf(1), cancelled)
    }

    @Test
    fun launchPolicy_cancelPreviousLaunchIsCancelledOnStateChange() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancelPreviousCancel = { cancelled += it },
        )

        store.dispatch(AppAction.CancelPreviousDefault(marker = 1))
        runCurrent()

        store.dispatch(AppAction.MoveToCompleted)
        runCurrent()

        assertEquals(listOf(1), cancelled)
        assertEquals(AppState.Completed, store.currentState)
    }

    @Test
    fun launchPolicy_distinctKeysKeepLaunchesIndependent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.KeyedLaunches)
        runCurrent()

        assertEquals(AppState.Active(value = 11), store.currentState)
    }

    @Test
    fun launchPolicy_sameKeyCoordinatesLaunchesWithinHandler() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.SharedDefaultKey)
        runCurrent()

        assertEquals(AppState.Active(value = 10), store.currentState)
    }

    @Test
    fun launchPolicy_dropIfRunningUsesMatchingKeyAcrossDispatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.DropIfRunningAdd(delta = 1))
        store.dispatch(AppAction.DropIfRunningAdd(delta = 10))
        runCurrent()

        assertEquals(AppState.Active(value = 0), store.currentState)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 1), store.currentState)

        store.dispatch(AppAction.DropIfRunningAdd(delta = 10))
        runCurrent()

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 11), store.currentState)
    }

    @Test
    fun launchPolicy_cancelPreviousUsesMatchingKeyAcrossDispatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.LatestAdd(delta = 1))
        store.dispatch(AppAction.LatestAdd(delta = 10))
        runCurrent()

        assertEquals(AppState.Active(value = 0), store.currentState)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 10), store.currentState)
    }
}
