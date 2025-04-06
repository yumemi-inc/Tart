package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareContext
import io.yumemi.tart.core.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Middleware that logs Store operations.
 * Logs action dispatches, state changes, event emissions, error occurrences, etc.
 *
 * @param logger Logger to use
 * @param tag Tag for the logs
 * @param severity Severity of the logs
 * @param coroutineDispatcher Coroutine dispatcher to use for log processing
 */
@Suppress("unused")
open class LoggingMiddleware<S : State, A : Action, E : Event>(
    private val logger: Logger = DefaultLogger,
    private val tag: String = "Tart",
    private val severity: Logger.Severity = Logger.Severity.Debug,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Middleware<S, A, E> {
    private lateinit var coroutineScope: CoroutineScope

    override suspend fun onInit(middlewareContext: MiddlewareContext<S, A, E>) {
        this.coroutineScope = CoroutineScope(middlewareContext.coroutineContext + SupervisorJob() + coroutineDispatcher)
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
