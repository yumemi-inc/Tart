package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StorePluginExceptionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    sealed interface AppState : State {
        data class Ready(val value: Int = 0) : AppState
        data class Failed(val message: String) : AppState
    }

    sealed interface AppAction : Action {
        data object Increment : AppAction
        data object Throw : AppAction
    }

    private fun createStore(
        plugin: Plugin<AppState, AppAction, Nothing>,
        exceptionHandler: ExceptionHandler = ExceptionHandler.Noop,
    ): Store<AppState, AppAction, Nothing> {
        return Store(AppState.Ready()) {
            coroutineContext(Dispatchers.Unconfined)
            exceptionHandler(exceptionHandler)
            plugin(plugin)

            state<AppState.Ready> {
                action<AppAction.Increment> {
                    nextState(state.copy(value = state.value + 1))
                }

                action<AppAction.Throw> {
                    throw IllegalStateException("action failed")
                }
            }

            state<AppState> {
                error<Exception> {
                    nextState(AppState.Failed(error.message ?: "unknown"))
                }
            }
        }
    }

    @Test
    fun pluginException_isHandledWithoutUsingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createStore(
            plugin = object : Plugin<AppState, AppAction, Nothing> {
                override suspend fun onAction(
                    scope: PluginScope<AppState, AppAction>,
                    state: AppState,
                    action: AppAction,
                ) {
                    throw IllegalArgumentException("plugin failed")
                }
            },
            exceptionHandler = ExceptionHandler { handledException = it },
        )

        store.dispatch(AppAction.Increment)
        testScheduler.runCurrent()

        assertEquals(AppState.Ready(), store.currentState)
        val error = assertIs<IllegalArgumentException>(handledException)
        assertEquals("plugin failed", error.message)
    }

    @Test
    fun pluginOnStateException_isHandledWithoutRetryingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createStore(
            plugin = object : Plugin<AppState, AppAction, Nothing> {
                override suspend fun onState(
                    scope: PluginScope<AppState, AppAction>,
                    prevState: AppState,
                    state: AppState,
                ) {
                    throw IllegalArgumentException("onState failed")
                }
            },
            exceptionHandler = ExceptionHandler { handledException = it },
        )

        store.dispatch(AppAction.Increment)
        testScheduler.runCurrent()

        assertEquals(AppState.Ready(value = 1), store.currentState)
        val error = assertIs<IllegalArgumentException>(handledException)
        assertEquals("onState failed", error.message)
    }
}
