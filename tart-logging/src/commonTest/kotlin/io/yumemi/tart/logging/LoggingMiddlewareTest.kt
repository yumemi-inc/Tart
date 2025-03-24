package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoggingMiddlewareTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun loggingMiddleware_shouldLogAction() = runTest(testDispatcher) {
        val testLogger = TestLogger()
        val middleware = LoggingMiddleware<CounterState, CounterAction, CounterEvent>(
            logger = testLogger,
        )

        val store = createTestStore(CounterState(10), testDispatcher, middleware)

        store.dispatch(CounterAction.Increment)

        assertTrue(testLogger.logs.isNotEmpty())
        assertTrue(testLogger.logs.any { it.contains("Action:") })
    }
}

private data class CounterState(val count: Int) : State
private sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
}

private interface CounterEvent : Event

private class TestLogger : Logger {
    val logs = mutableListOf<String>()
    override suspend fun log(severity: Logger.Severity, tag: String, throwable: Throwable?, message: String) {
        logs.add(message)
    }
}

private fun createTestStore(
    initialState: CounterState,
    coroutineContext: CoroutineContext,
    middleware: Middleware<CounterState, CounterAction, CounterEvent>,
): Store<CounterState, CounterAction, CounterEvent> {
    return object : Store.Base<CounterState, CounterAction, CounterEvent>(initialState, coroutineContext) {
        override val middlewares = listOf(middleware)
        override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState {
            return when (action) {
                CounterAction.Increment -> CounterState(state.count + 1)
                CounterAction.Decrement -> CounterState(state.count - 1)
            }
        }
    }
}
