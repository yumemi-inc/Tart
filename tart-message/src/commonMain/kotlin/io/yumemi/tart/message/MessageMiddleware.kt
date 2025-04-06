package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareContext
import io.yumemi.tart.core.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Middleware for receiving and processing messages.
 * Subscribes to messages from MessageHub and processes them with the specified callback function.
 *
 * @param receive Callback function called when a message is received
 */
@Suppress("unused")
class MessageMiddleware<S : State, A : Action, E : Event>(
    private val receive: suspend MiddlewareContext<S, A, E>.(Message) -> Unit,
) : Middleware<S, A, E> {
    override suspend fun onInit(middlewareContext: MiddlewareContext<S, A, E>) {
        CoroutineScope(middlewareContext.coroutineContext).launch {
            MessageHub.messages.collect {
                receive.invoke(middlewareContext, it)
            }
        }
    }
}
