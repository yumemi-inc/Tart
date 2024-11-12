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

internal object MessageHub {
    private val _messages = MutableSharedFlow<Message>()
    val messages: Flow<Message> get() = _messages

    suspend fun send(message: Message) {
        _messages.emit(message)
    }
}

@Suppress("unused")
abstract class MessageSendMiddleware<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    private lateinit var store: Store<S, A, E>
    private lateinit var coroutineScope: CoroutineScope

    final override suspend fun onInit(store: Store<S, A, E>, coroutineContext: CoroutineContext) {
        this.store = store
        this.coroutineScope = CoroutineScope(coroutineContext)
    }

    final override suspend fun afterEventEmit(state: S, event: E) {
        coroutineScope.launch { // launch coroutine to avoid Store being blocked
            onEvent(event, this@MessageSendMiddleware::send, store)
        }
    }

    protected abstract suspend fun onEvent(event: E, send: SendFun, store: Store<S, A, E>)

    private suspend fun send(message: Message) {
        MessageHub.send(message)
    }

    final override suspend fun beforeActionDispatch(state: S, action: A) {}
    final override suspend fun afterActionDispatch(state: S, action: A, nextState: S) {}
    final override suspend fun beforeEventEmit(state: S, event: E) {}
    final override suspend fun beforeStateEnter(state: S) {}
    final override suspend fun afterStateEnter(state: S, nextState: S) {}
    final override suspend fun beforeStateExit(state: S) {}
    final override suspend fun afterStateExit(state: S) {}
    final override suspend fun beforeStateChange(state: S, nextState: S) {}
    final override suspend fun afterStateChange(state: S, prevState: S) {}
    final override suspend fun beforeError(state: S, error: Throwable) {}
    final override suspend fun afterError(state: S, nextState: S, error: Throwable) {}
}

@Suppress("unused")
abstract class MessageReceiveMiddleware<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    final override suspend fun onInit(store: Store<S, A, E>, coroutineContext: CoroutineContext) {
        CoroutineScope(coroutineContext).launch {
            MessageHub.messages.collect {
                receive(it, store)
            }
        }
    }

    protected abstract suspend fun receive(message: Message, store: Store<S, A, E>)

    final override suspend fun beforeActionDispatch(state: S, action: A) {}
    final override suspend fun afterActionDispatch(state: S, action: A, nextState: S) {}
    final override suspend fun beforeEventEmit(state: S, event: E) {}
    final override suspend fun afterEventEmit(state: S, event: E) {}
    final override suspend fun beforeStateEnter(state: S) {}
    final override suspend fun afterStateEnter(state: S, nextState: S) {}
    final override suspend fun beforeStateExit(state: S) {}
    final override suspend fun afterStateExit(state: S) {}
    final override suspend fun beforeStateChange(state: S, nextState: S) {}
    final override suspend fun afterStateChange(state: S, prevState: S) {}
    final override suspend fun beforeError(state: S, error: Throwable) {}
    final override suspend fun afterError(state: S, nextState: S, error: Throwable) {}
}

typealias SendFun = suspend (Message) -> Unit
