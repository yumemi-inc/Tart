package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreOverridesTest {

    data class AppState(val count: Int) : State

    sealed interface AppAction : Action {
        data object Increment : AppAction
    }

    sealed interface PendingPolicyState : State {
        data object Initial : PendingPolicyState
        data class Active(val value: Int = 0) : PendingPolicyState
    }

    sealed interface PendingPolicyAction : Action {
        data object EnterActiveAfterDelay : PendingPolicyAction
        data object Increment : PendingPolicyAction
    }

    private class RecordingStateSaver(
        private val restoredState: AppState?,
    ) : StateSaver<AppState> {
        val savedStates = mutableListOf<AppState>()

        override fun save(state: AppState) {
            savedStates += state
        }

        override fun restore(): AppState? = restoredState
    }

    private fun recordingMiddleware(
        name: String,
        records: MutableList<String>,
    ): Middleware<AppState, AppAction, Nothing> {
        return Middleware(
            afterActionDispatch = { _, _, _ ->
                records += name
            },
        )
    }

    @Test
    fun storeInitialStateOverload_shouldApplyOverridesAfterSetup() {
        val setupSaver = RecordingStateSaver(restoredState = AppState(count = 100))
        val overrideSaver = RecordingStateSaver(restoredState = AppState(count = 10))
        val middlewareRecords = mutableListOf<String>()

        val store = Store(
            initialState = AppState(count = 0),
            overrides = {
                coroutineContext(Dispatchers.Unconfined)
                stateSaver(overrideSaver)
                replaceMiddlewares(recordingMiddleware("override", middlewareRecords))
            },
        ) {
            stateSaver(setupSaver)
            middleware(recordingMiddleware("setup", middlewareRecords))

            state<AppState> {
                action<AppAction.Increment> {
                    nextState(AppState(count = state.count + 1))
                }
            }
        }

        assertEquals(AppState(count = 10), store.currentState)

        store.dispatch(AppAction.Increment)

        assertEquals(listOf("override"), middlewareRecords)
        assertEquals(listOf(AppState(count = 11)), overrideSaver.savedStates)
        assertTrue(setupSaver.savedStates.isEmpty())
    }

    @Test
    fun storeDslInitialStateOverload_shouldApplyOverridesAndAllowMiddlewareAppendAfterReplacement() {
        val setupSaver = RecordingStateSaver(restoredState = AppState(count = 100))
        val overrideSaver = RecordingStateSaver(restoredState = AppState(count = 20))
        val middlewareRecords = mutableListOf<String>()

        val store = Store(
            overrides = {
                coroutineContext(Dispatchers.Unconfined)
                stateSaver(overrideSaver)
                replaceMiddlewares(recordingMiddleware("replacement", middlewareRecords))
                middleware(recordingMiddleware("extra", middlewareRecords))
            },
        ) {
            initialState(AppState(count = 0))
            stateSaver(setupSaver)
            middleware(recordingMiddleware("setup", middlewareRecords))

            state<AppState> {
                action<AppAction.Increment> {
                    nextState(AppState(count = state.count + 1))
                }
            }
        }

        assertEquals(AppState(count = 20), store.currentState)

        store.dispatch(AppAction.Increment)

        assertEquals(listOf("extra", "replacement"), middlewareRecords.sorted())
        assertEquals(listOf(AppState(count = 21)), overrideSaver.savedStates)
        assertTrue(setupSaver.savedStates.isEmpty())
    }

    @Test
    fun clearMiddlewaresInOverrides_shouldClearPreviouslyConfiguredMiddlewares() {
        val middlewareRecords = mutableListOf<String>()

        val store = Store(
            initialState = AppState(count = 0),
            overrides = {
                clearMiddlewares()
            },
        ) {
            coroutineContext(Dispatchers.Unconfined)
            middleware(recordingMiddleware("setup", middlewareRecords))

            state<AppState> {
                action<AppAction.Increment> {
                    nextState(AppState(count = state.count + 1))
                }
            }
        }

        store.dispatch(AppAction.Increment)

        assertTrue(middlewareRecords.isEmpty())
    }

    @Test
    fun middleware_shouldAcceptMultipleValuesInSetupAndOverrides() {
        val middlewareRecords = mutableListOf<String>()

        val store = Store(
            initialState = AppState(count = 0),
            overrides = {
                middleware(
                    recordingMiddleware("override1", middlewareRecords),
                    recordingMiddleware("override2", middlewareRecords),
                )
            },
        ) {
            coroutineContext(Dispatchers.Unconfined)
            middleware(
                recordingMiddleware("setup1", middlewareRecords),
                recordingMiddleware("setup2", middlewareRecords),
            )

            state<AppState> {
                action<AppAction.Increment> {
                    nextState(AppState(count = state.count + 1))
                }
            }
        }

        store.dispatch(AppAction.Increment)

        assertEquals(
            listOf("override1", "override2", "setup1", "setup2"),
            middlewareRecords.sorted(),
        )
    }

    @Test
    fun pendingActionPolicyInOverrides_shouldOverrideSetupConfiguration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = Store<PendingPolicyState, PendingPolicyAction, Nothing>(
            initialState = PendingPolicyState.Initial,
            overrides = {
                pendingActionPolicy(PendingActionPolicy.Keep)
            },
        ) {
            coroutineContext(testDispatcher)
            pendingActionPolicy(PendingActionPolicy.ClearOnStateExit)

            state<PendingPolicyState.Initial> {
                action<PendingPolicyAction.EnterActiveAfterDelay>(testDispatcher) {
                    delay(100)
                    nextState(PendingPolicyState.Active())
                }
            }

            state<PendingPolicyState.Active> {
                action<PendingPolicyAction.Increment>(testDispatcher) {
                    nextState(state.copy(value = state.value + 1))
                }
            }
        }

        store.dispatch(PendingPolicyAction.EnterActiveAfterDelay)
        runCurrent()

        store.dispatch(PendingPolicyAction.Increment)
        store.dispatch(PendingPolicyAction.Increment)

        advanceUntilIdle()

        assertEquals(PendingPolicyState.Active(value = 2), store.currentState)
    }
}
