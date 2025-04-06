package io.yumemi.tart.message

import io.yumemi.tart.core.StoreScope
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
 * Extension function for sending messages to the MessageHub.
 * This allows any StoreScope to easily send messages to other components.
 *
 * @param message The message to send
 */
suspend fun StoreScope.message(message: Message) {
    MessageHub.send(message)
}
