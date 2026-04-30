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
 * Messages are sent to a process-wide shared bus.
 * All started Stores using `receiveMessages(...)` subscribe to the same bus.
 * Messages are not replayed, so receivers that are not actively collecting when a message is sent
 * will not receive that past message.
 *
 * @param message The message to send
 */
suspend fun StoreScope.message(message: Message) {
    MessageHub.send(message)
}
