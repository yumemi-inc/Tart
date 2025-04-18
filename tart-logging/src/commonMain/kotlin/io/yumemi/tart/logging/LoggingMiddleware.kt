package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareScope
import io.yumemi.tart.core.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

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
    private lateinit var middlewareScope: MiddlewareScope<A>

    override suspend fun onInit(middlewareScope: MiddlewareScope<A>) {
        this.middlewareScope = middlewareScope
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
        middlewareScope.launch(coroutineDispatcher) { // launch Coroutines to avoid blocking Store processing in case of heavy logging
            logger.log(severity = severity, tag = tag, throwable = throwable, message = message())
        }
    }
}
