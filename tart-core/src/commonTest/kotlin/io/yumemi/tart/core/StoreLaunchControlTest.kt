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
class StoreLaunchControlTest {
    sealed interface AppState : State {
        data class Active(val value: Int = 0) : AppState
        data object Completed : AppState
    }

    sealed interface AppAction : Action {
        data class ReplaceDefault(val marker: Int) : AppAction
        data object KeyedLaunches : AppAction
        data object SharedDefaultLane : AppAction
        data object SharedDefaultLaneAcrossControls : AppAction
        data class DropNewAdd(val delta: Int) : AppAction
        data class LatestAdd(val delta: Int) : AppAction
        data object MoveToCompleted : AppAction
    }

    private fun createStore(
        testDispatcher: TestDispatcher,
        onReplaceStart: ((Int) -> Unit)? = null,
        onReplaceCancel: ((Int) -> Unit)? = null,
    ): Store<AppState, AppAction, Nothing> {
        val primaryLane = LaunchLane()
        val secondaryLane = LaunchLane()
        return Store(AppState.Active()) {
            coroutineContext(testDispatcher)

            state<AppState.Active> {
                action<AppAction.ReplaceDefault> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(),
                    ) {
                        onReplaceStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onReplaceCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.KeyedLaunches> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(primaryLane),
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 1))
                        }
                    }
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(secondaryLane),
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 10))
                        }
                    }
                }

                action<AppAction.SharedDefaultLane> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(),
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 1))
                        }
                    }
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(),
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 10))
                        }
                    }
                }

                action<AppAction.SharedDefaultLaneAcrossControls> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(),
                    ) {
                        delay(100)
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 1))
                        }
                    }
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.DropNew(),
                    ) {
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + 10))
                        }
                    }
                }

                action<AppAction.DropNewAdd> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.DropNew(),
                    ) {
                        delay(100)
                        transaction(testDispatcher) {
                            nextState(state.copy(value = state.value + action.delta))
                        }
                    }
                }

                action<AppAction.LatestAdd> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(),
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
    fun launchControl_replaceCancelsMatchingLaneAcrossDispatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onReplaceStart = { started += it },
            onReplaceCancel = { cancelled += it },
        )

        store.dispatch(AppAction.ReplaceDefault(marker = 1))
        runCurrent()

        assertEquals(listOf(1), started)
        assertEquals(emptyList(), cancelled)

        store.dispatch(AppAction.ReplaceDefault(marker = 2))
        runCurrent()

        assertEquals(listOf(1, 2), started)
        assertEquals(listOf(1), cancelled)
    }

    @Test
    fun launchControl_replaceLaunchIsCancelledOnStateChange() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onReplaceCancel = { cancelled += it },
        )

        store.dispatch(AppAction.ReplaceDefault(marker = 1))
        runCurrent()

        store.dispatch(AppAction.MoveToCompleted)
        runCurrent()

        assertEquals(listOf(1), cancelled)
        assertEquals(AppState.Completed, store.currentState)
    }

    @Test
    fun launchControl_distinctLanesKeepLaunchesIndependent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.KeyedLaunches)
        runCurrent()

        assertEquals(AppState.Active(value = 11), store.currentState)
    }

    @Test
    fun launchControl_sameDefaultLaneCoordinatesLaunchesWithinHandler() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.SharedDefaultLane)
        runCurrent()

        assertEquals(AppState.Active(value = 10), store.currentState)
    }

    @Test
    fun launchControl_defaultLaneIsSharedAcrossControlsWithinHandler() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.SharedDefaultLaneAcrossControls)
        runCurrent()

        assertEquals(AppState.Active(value = 0), store.currentState)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 1), store.currentState)
    }

    @Test
    fun launchControl_dropNewUsesMatchingLaneAcrossDispatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)

        store.dispatch(AppAction.DropNewAdd(delta = 1))
        store.dispatch(AppAction.DropNewAdd(delta = 10))
        runCurrent()

        assertEquals(AppState.Active(value = 0), store.currentState)

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 1), store.currentState)

        store.dispatch(AppAction.DropNewAdd(delta = 10))
        runCurrent()

        advanceTimeBy(100)
        runCurrent()

        assertEquals(AppState.Active(value = 11), store.currentState)
    }

    @Test
    fun launchControl_replaceUsesMatchingLaneAcrossDispatches() = runTest {
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
