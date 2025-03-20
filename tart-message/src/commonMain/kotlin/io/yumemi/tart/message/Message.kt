package io.yumemi.tart.message

import io.yumemi.tart.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

interface Message

internal object MessageHub {
    private val _messages = MutableSharedFlow<Message>()
    val messages: Flow<Message> get() = _messages

    suspend fun send(message: Message) {
        _messages.emit(message)
    }
}

@Suppress("unused")
suspend fun Store.Base<*, *, *>.send(message: Message) {
    MessageHub.send(message = message)
}
