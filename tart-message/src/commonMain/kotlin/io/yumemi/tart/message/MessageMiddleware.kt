package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StoreContext
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
    private val receive: suspend StoreContext<S, A, E>.(Message) -> Unit,
) : Middleware<S, A, E> {
    override suspend fun onInit(context: StoreContext<S, A, E>) {
        CoroutineScope(context.coroutineContext).launch {
            MessageHub.messages.collect {
                receive.invoke(context, it)
            }
        }
    }
}
