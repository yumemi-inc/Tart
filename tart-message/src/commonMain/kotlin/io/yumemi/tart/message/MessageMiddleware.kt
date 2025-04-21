package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareScope
import io.yumemi.tart.core.State

private abstract class MiddlewareImpl<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    override suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {
        middlewareScope.launch {
            MessageHub.messages.collect {
                receive(middlewareScope, it)
            }
        }
    }

    abstract suspend fun receive(middlewareScope: MiddlewareScope<A>, message: Message)
}

/**
 * Creates a middleware that receives messages from the MessageHub.
 * This middleware automatically subscribes to the message flow when the store starts 
 * and processes each message with the provided block.
 *
 * @param block The function to process received messages with MiddlewareScope as receiver
 * @return A middleware that processes messages
 */
fun <S : State, A : Action, E : Event> receiveMessages(block: suspend MiddlewareScope<A>.(Message) -> Unit): Middleware<S, A, E> {
    return object : MiddlewareImpl<S, A, E>() {
        override suspend fun receive(middlewareScope: MiddlewareScope<A>, message: Message) {
            middlewareScope.block(message)
        }
    }
}
