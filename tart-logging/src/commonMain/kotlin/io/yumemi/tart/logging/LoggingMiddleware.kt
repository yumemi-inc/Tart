package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareScope
import io.yumemi.tart.core.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Middleware that logs Store operations.
 *
 * @param logger Logger to use
 * @param coroutineDispatcher Coroutine dispatcher to use for log processing
 */
abstract class LoggingMiddleware<S : State, A : Action, E : Event>(
    private val logger: Logger = DefaultLogger,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : Middleware<S, A, E> {
    private lateinit var middlewareScope: MiddlewareScope<A>

    override suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {
        this.middlewareScope = middlewareScope
    }

    protected fun log(severity: Logger.Severity, tag: String, throwable: Throwable? = null, message: () -> String) {
        middlewareScope.launch(coroutineDispatcher) { // launch Coroutines to avoid blocking Store processing in case of heavy logging
            logger.log(severity = severity, tag = tag, throwable = throwable, message = message())
        }
    }
}

/**
 * Creates a simple logging middleware that logs Store operations.
 * This middleware logs all action dispatches, state changes, event emissions, and errors
 * with the specified severity level.
 *
 * @param tag The tag to use for logging
 * @param severity The severity level for log messages
 * @param logger The logger implementation to use
 * @param coroutineDispatcher The dispatcher for logging operations
 * @return A middleware that performs logging for all store operations
 */
fun <S : State, A : Action, E : Event> simpleLogging(
    tag: String = "Tart",
    severity: Logger.Severity = Logger.Severity.Debug,
    logger: Logger = DefaultLogger,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
): Middleware<S, A, E> {
    return object : LoggingMiddleware<S, A, E>(
        logger = logger,
        coroutineDispatcher = coroutineDispatcher,
    ) {
        override suspend fun beforeActionDispatch(state: S, action: A) {
            log(severity, tag) { "Action: $action" }
        }

        override suspend fun beforeEventEmit(state: S, event: E) {
            log(severity, tag) { "Event: $event" }
        }

        override suspend fun beforeStateChange(state: S, nextState: S) {
            log(severity, tag) { "State: $nextState <- $state" }
        }

        override suspend fun beforeError(state: S, error: Throwable) {
            log(severity, tag, error) { "Error: $error" }
        }
    }
}
