package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

interface Message

private object MessageHub {
    private val _messages = MutableSharedFlow<Message>()
    val messages: Flow<Message> get() = _messages

    suspend fun send(message: Message) {
        _messages.emit(message)
    }
}

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

@Suppress("unused")
suspend fun send(message: Message) {
    MessageHub.send(message = message)
}
