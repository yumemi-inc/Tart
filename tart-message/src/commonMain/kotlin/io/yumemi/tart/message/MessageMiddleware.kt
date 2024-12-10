package io.yumemi.tart.message

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

interface Message

@Suppress("unused")
abstract class MessageMiddleware<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    final override suspend fun onInit(store: Store<S, A, E>, coroutineContext: CoroutineContext) {
        CoroutineScope(coroutineContext).launch {
            messages.collect {
                receive(it, store::dispatch)
            }
        }
    }

    protected abstract suspend fun receive(message: Message, dispatch: (action: A) -> Unit)

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

    companion object {
        private val messages = MutableSharedFlow<Message>()
        suspend fun send(message: Message) {
            messages.emit(message)
        }
    }
}
