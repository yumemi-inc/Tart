package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MessageMiddlewareTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun messageMiddleware_shouldReceiveMessages() = runTest {
        val sendMessage = TestMessage("Hello")

        var receivedMessage: TestMessage? = null
        val middleware = MessageMiddleware<CounterState, CounterAction, CounterEvent> { message, _ ->
            if (message is TestMessage) {
                receivedMessage = message
            }
        }

        val store = createTestStore(CounterState(10), testDispatcher, middleware)

        // Access state to initialize the store
        store.state

        // Send a message
        MessageHub.send(sendMessage)

        // Verify message was received
        assertEquals(sendMessage, receivedMessage)
    }
}

private data class CounterState(val count: Int) : State
private interface CounterAction : Action
private interface CounterEvent : Event

private data class TestMessage(val value: String) : Message

private fun createTestStore(
    initialState: CounterState,
    coroutineContext: CoroutineContext,
    middleware: Middleware<CounterState, CounterAction, CounterEvent>,
): Store<CounterState, CounterAction, CounterEvent> {
    return object : Store.Base<CounterState, CounterAction, CounterEvent>(initialState, coroutineContext) {
        override val middlewares = listOf(middleware)
    }
}
