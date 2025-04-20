package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareScope
import io.yumemi.tart.core.State

/**
 * Middleware for receiving and processing messages.
 * Subscribes to messages from MessageHub and processes them with the specified callback function.
 *
 * @param receive Callback function called when a message is received
 */
@Suppress("unused")
class MessageMiddleware<S : State, A : Action, E : Event>(
    private val receive: suspend MiddlewareScope<A>.(Message) -> Unit,
) : Middleware<S, A, E> {
    override suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {
        middlewareScope.launch {
            MessageHub.messages.collect {
                receive.invoke(middlewareScope, it)
            }
        }
    }
}
