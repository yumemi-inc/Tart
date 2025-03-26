package io.yumemi.tart.message

import io.yumemi.tart.core.TartStore
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
 * Extension function for Store that provides a convenient method for sending messages.
 */
@Suppress("unused")
suspend fun TartStore<*, *, *>.send(message: Message) {
    MessageHub.send(message = message)
}
