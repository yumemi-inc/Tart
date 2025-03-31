package io.yumemi.tart.message

import io.yumemi.tart.core.StoreContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Marker interface representing notifications or messages within the application.
 * Used to define messages for communication between different modules via Store.
 */
interface Message

internal object MessageHub {
    private val _messages = MutableSharedFlow<Message>()
    val messages: Flow<Message> get() = _messages

    suspend fun send(message: Message) {
        _messages.emit(message)
    }
}

/**
 * Extension property that provides a function for sending messages to the MessageHub.
 * This allows any StoreContext to easily send messages to other components.
 *
 * @return A suspend function that takes a Message and sends it to the MessageHub
 */
val StoreContext.send: suspend (Message) -> Unit
    get() = { MessageHub.send(it) }
