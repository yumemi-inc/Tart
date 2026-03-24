package io.yumemi.tart.core

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreAnyHandlerDslTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    sealed interface AppState : State {
        data object First : AppState
        data object Second : AppState
        data class Counter(val value: Int) : AppState
        data class Failed(val reason: String) : AppState
    }

    sealed interface AppAction : Action {
        data object Toggle : AppAction
        data class AsyncAdd(val delta: Int) : AppAction
        data object ThrowException : AppAction
        data object ThrowError : AppAction
    }

    sealed interface AppEvent : Event

    private fun createTestStore(
        initialState: AppState,
        exceptionHandler: ExceptionHandler = ExceptionHandler.Noop,
    ): Store<AppState, AppAction, AppEvent> {
        return Store(initialState) {
            coroutineContext(testDispatcher)
            exceptionHandler(exceptionHandler)

            anyState {
                anyAction {
                    when (action) {
                        AppAction.Toggle -> {
                            when (state) {
                                AppState.First -> nextState(AppState.Second)
                                AppState.Second -> nextState(AppState.First)
                                is AppState.Counter -> Unit
                                is AppState.Failed -> Unit
                            }
                        }

                        is AppAction.AsyncAdd -> Unit
                        AppAction.ThrowException -> throw IllegalStateException("boom")
                        AppAction.ThrowError -> throw AssertionError("fatal")
                    }
                }

                anyError {
                    nextState(AppState.Failed(error.message ?: "unknown"))
                }
            }
        }
    }

    private fun createAnyActionLaunchStore(): Store<AppState, AppAction, AppEvent> {
        return Store(AppState.Counter(value = 0)) {
            coroutineContext(testDispatcher)

            anyState {
                anyAction {
                    when (val currentAction = action) {
                        is AppAction.AsyncAdd -> {
                            launch {
                                val delta = currentAction.delta
                                transaction {
                                    val currentState = state as? AppState.Counter ?: return@transaction
                                    nextState(currentState.copy(value = currentState.value + delta + currentAction.delta))
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    @Test
    fun anyState_anyAction_shouldHandleActionsAcrossStates() = runTest(testDispatcher) {
        val store = createTestStore(initialState = AppState.First)

        store.dispatch(AppAction.Toggle)
        assertEquals(AppState.Second, store.currentState)

        store.dispatch(AppAction.Toggle)
        assertEquals(AppState.First, store.currentState)
    }

    @Test
    fun anyError_shouldHandleExceptions() = runTest(testDispatcher) {
        val store = createTestStore(initialState = AppState.First)

        store.dispatch(AppAction.ThrowException)

        assertEquals(AppState.Failed("boom"), store.currentState)
    }

    @Test
    fun anyActionLaunch_canReferenceActionInLaunchAndTransaction() = runTest(testDispatcher) {
        val store = createAnyActionLaunchStore()

        store.dispatch(AppAction.AsyncAdd(delta = 2))
        yield()

        assertEquals(AppState.Counter(value = 4), store.currentState)
    }

    @Test
    fun anyError_shouldNotHandleError() = runTest(testDispatcher) {
        var handledThrowable: Throwable? = null
        val store = createTestStore(
            initialState = AppState.First,
            exceptionHandler = ExceptionHandler { handledThrowable = it },
        )

        store.dispatch(AppAction.ThrowError)

        assertNotNull(handledThrowable)
        assertIs<AssertionError>(handledThrowable)
        assertEquals(AppState.First, store.currentState)
    }
}
