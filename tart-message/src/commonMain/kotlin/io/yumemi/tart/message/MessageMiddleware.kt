package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Middleware for receiving and processing messages.
 * Subscribes to messages from MessageHub and processes them with the specified callback function.
 *
 * @param receive Callback function called when a message is received
 */
@Suppress("unused")
class MessageMiddleware<S : State, A : Action, E : Event>(
    private val receive: suspend (message: Message, dispatch: (action: A) -> Unit) -> Unit,
) : Middleware<S, A, E> {
    override suspend fun onInit(store: Store<S, A, E>, coroutineContext: CoroutineContext) {
        CoroutineScope(coroutineContext).launch {
            MessageHub.messages.collect {
                receive.invoke(it, store::dispatch)
            }
        }
    }
}
