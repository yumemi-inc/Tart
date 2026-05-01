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
 * Creates middleware that subscribes to Tart's shared message bus when the Store starts.
 *
 * The subscription stays active until the Store closes and invokes [block] for each received
 * [Message].
 * The underlying bus is process-wide and shared across all Stores using this middleware.
 * Messages are delivered only to active subscribers and are not replayed to Stores that start
 * later.
 *
 * @param block Function to process received messages with [MiddlewareScope] as receiver
 * @return Middleware that processes shared messages
 */
fun <S : State, A : Action, E : Event> receiveMessages(block: suspend MiddlewareScope<A>.(Message) -> Unit): Middleware<S, A, E> {
    return object : MiddlewareImpl<S, A, E>() {
        override suspend fun receive(middlewareScope: MiddlewareScope<A>, message: Message) {
            middlewareScope.block(message)
        }
    }
}
