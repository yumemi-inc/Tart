package io.yumemi.tart.message

import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MessageMiddlewareTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun messageMiddleware_shouldReceiveMessages() = runTest(testDispatcher) {
        val sendMessage = TestMessage("Hello")

        var receivedMessage: TestMessage? = null
        val middleware = MessageMiddleware<CounterState, Nothing, Nothing> { message ->
            if (message is TestMessage) {
                receivedMessage = message
            }
        }

        val store = createTestStore(CounterState(10), middleware, sendMessage)

        store.state // access state to initialize the store

        assertEquals(sendMessage, receivedMessage)
    }
}

private data class CounterState(val count: Int) : State

private data class TestMessage(val value: String) : Message

private fun createTestStore(
    initialState: CounterState,
    middleware: Middleware<CounterState, Nothing, Nothing>,
    message: Message,
): Store<CounterState, Nothing, Nothing> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)
        middleware(middleware)
        state<CounterState> {
            enter {
                send(message)
                state
            }
        }
    }
}
