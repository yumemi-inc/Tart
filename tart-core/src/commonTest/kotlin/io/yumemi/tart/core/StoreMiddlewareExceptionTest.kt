package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StoreMiddlewareExceptionTest {

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
        middleware: Middleware<AppState, AppAction, Nothing>,
        exceptionHandler: ExceptionHandler = ExceptionHandler.Noop,
    ): Store<AppState, AppAction, Nothing> {
        return Store(AppState.Ready()) {
            coroutineContext(Dispatchers.Unconfined)
            exceptionHandler(exceptionHandler)
            middleware(middleware)

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
    fun middlewareException_isHandledWithoutUsingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createStore(
            middleware = Middleware(
                beforeActionDispatch = { _, _ ->
                    throw IllegalArgumentException("middleware failed")
                },
            ),
            exceptionHandler = ExceptionHandler { handledException = it },
        )

        store.dispatch(AppAction.Increment)
        testScheduler.runCurrent()

        assertEquals(AppState.Ready(), store.currentState)
        val error = assertIs<IllegalArgumentException>(handledException)
        assertEquals("middleware failed", error.message)
    }

    @Test
    fun middlewareBeforeErrorException_isHandledWithoutRetryingStoreErrorHandling() = runTest(testDispatcher) {
        var handledException: Throwable? = null
        val store = createStore(
            middleware = Middleware(
                beforeError = { _, _ ->
                    throw IllegalArgumentException("beforeError failed")
                },
            ),
            exceptionHandler = ExceptionHandler { handledException = it },
        )

        store.dispatch(AppAction.Throw)
        testScheduler.runCurrent()

        assertEquals(AppState.Ready(), store.currentState)
        val error = assertIs<IllegalArgumentException>(handledException)
        assertEquals("beforeError failed", error.message)
    }
}
