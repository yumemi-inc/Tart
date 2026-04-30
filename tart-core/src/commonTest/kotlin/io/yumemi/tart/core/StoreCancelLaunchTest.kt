package io.yumemi.tart.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoreCancelLaunchTest {
    private val sharedLane = LaunchLane()
    private val otherLane = LaunchLane()
    private val missingLane = LaunchLane()

    sealed interface AppState : State {
        data object Active : AppState
    }

    sealed interface AppAction : Action {
        data class StartDropNewShared(val marker: Int) : AppAction
        data class StartDropNewOther(val marker: Int) : AppAction
        data class StartReplaceShared(val marker: Int) : AppAction
        data class StartConcurrentShared(val marker: Int) : AppAction
        data object StartEnterLaunch : AppAction
        data object CancelShared : AppAction
        data object CancelMissing : AppAction
    }

    private fun createStore(
        testDispatcher: TestDispatcher,
        onStart: ((Int) -> Unit)? = null,
        onCancel: ((Int) -> Unit)? = null,
    ): Store<AppState, AppAction, Nothing> {
        return Store(AppState.Active) {
            coroutineContext(testDispatcher)

            state<AppState.Active> {
                enter {
                    launch(testDispatcher) {
                        onStart?.invoke(-1)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancel?.invoke(-1)
                        }
                    }
                }

                action<AppAction.StartDropNewShared> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.DropNew(sharedLane),
                    ) {
                        onStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.StartDropNewOther> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.DropNew(otherLane),
                    ) {
                        onStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.StartReplaceShared> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Replace(sharedLane),
                    ) {
                        onStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.StartConcurrentShared> {
                    launch(
                        dispatcher = testDispatcher,
                        control = LaunchControl.Concurrent,
                    ) {
                        onStart?.invoke(action.marker)
                        try {
                            delay(Long.MAX_VALUE)
                        } finally {
                            onCancel?.invoke(action.marker)
                        }
                    }
                }

                action<AppAction.StartEnterLaunch> {
                    // no-op: collecting state starts enter.launch for this runtime
                }

                action<AppAction.CancelShared> {
                    cancelLaunch(sharedLane)
                }

                action<AppAction.CancelMissing> {
                    cancelLaunch(missingLane)
                }
            }
        }
    }

    @Test
    fun cancelLaunch_cancelsDropNewLaunchWithMatchingLane() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartDropNewShared(marker = 1))
        runCurrent()

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(listOf(1), cancelled)
    }

    @Test
    fun cancelLaunch_cancelsReplaceLaunchWithMatchingLane() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onStart = { started += it },
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartReplaceShared(marker = 9))
        runCurrent()

        assertEquals(listOf(-1, 9), started)

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(listOf(9), cancelled)
    }

    @Test
    fun cancelLaunch_onlyAffectsMatchingTrackedKey() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartDropNewShared(marker = 1))
        store.dispatch(AppAction.StartDropNewOther(marker = 100))
        runCurrent()

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(listOf(1), cancelled)
    }

    @Test
    fun cancelLaunch_isNoOpWhenLaneDoesNotExist() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.CancelMissing)
        runCurrent()

        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun cancelLaunch_affectsSharedTrackedLaneAcrossControls() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onStart = { started += it },
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartDropNewShared(marker = 1))
        runCurrent()

        store.dispatch(AppAction.StartReplaceShared(marker = 9))
        runCurrent()

        assertEquals(listOf(-1, 1, 9), started)
        assertEquals(listOf(1), cancelled)

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(listOf(1, 9), cancelled)
    }

    @Test
    fun cancelLaunch_doesNotCancelConcurrentLaunches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartConcurrentShared(marker = 7))
        runCurrent()

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(emptyList(), cancelled)

        store.close()
    }

    @Test
    fun cancelLaunch_doesNotCancelEnterLaunches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val store = createStore(
            testDispatcher = testDispatcher,
            onStart = { started += it },
            onCancel = { cancelled += it },
        )

        store.dispatch(AppAction.StartEnterLaunch)
        runCurrent()

        assertEquals(listOf(-1), started)

        store.dispatch(AppAction.CancelShared)
        runCurrent()

        assertEquals(emptyList(), cancelled)

        store.close()
        runCurrent()

        assertEquals(listOf(-1), cancelled)
    }
}
