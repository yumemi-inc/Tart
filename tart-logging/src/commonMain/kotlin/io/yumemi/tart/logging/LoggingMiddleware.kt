package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@Suppress("unused")
open class LoggingMiddleware<S : State, A : Action, E : Event>(
    private val logger: Logger = TartLogger(),
    private val tag: String = "Tart",
    private val severity: Logger.Severity = Logger.Severity.Debug,
) : Middleware<S, A, E> {
    private lateinit var coroutineScope: CoroutineScope

    final override suspend fun onInit(store: Store<S, A, E>, coroutineContext: CoroutineContext) {
        this.coroutineScope = CoroutineScope(coroutineContext + Dispatchers.IO)
    }

    override suspend fun beforeActionDispatch(state: S, action: A) {
        log { "Action: $action" }
    }

    override suspend fun beforeEventEmit(state: S, event: E) {
        log { "Event: $event" }
    }

    override suspend fun beforeStateChange(state: S, nextState: S) {
        log { "State: $nextState <- $state" }
    }

    override suspend fun beforeError(state: S, error: Throwable) {
        log(throwable = error) { "Error: $error" }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun log(severity: Logger.Severity = this.severity, tag: String = this.tag, throwable: Throwable? = null, message: () -> String) {
        coroutineScope.launch { // launch Coroutines to avoid blocking Store processing in case of heavy logging
            logger.log(severity = severity, tag = tag, throwable = throwable, message = message())
        }
    }
}
