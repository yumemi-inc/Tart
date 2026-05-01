package io.yumemi.tart.logging

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.MiddlewareScope
import io.yumemi.tart.core.State
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Base middleware for logging Store operations without blocking Store processing.
 *
 * Log writes are dispatched from [log] by launching work in [MiddlewareScope].
 *
 * @param logger Logger to use
 * @param dispatcher Optional CoroutineDispatcher override for log processing.
 * When null, logging inherits the Store's current execution context.
 */
abstract class LoggingMiddleware<S : State, A : Action, E : Event>(
    private val logger: Logger = DefaultLogger,
    private val dispatcher: CoroutineDispatcher? = null,
) : Middleware<S, A, E> {
    private lateinit var middlewareScope: MiddlewareScope<A>

    override suspend fun onStart(middlewareScope: MiddlewareScope<A>, state: S) {
        this.middlewareScope = middlewareScope
    }

    protected fun log(severity: Logger.Severity, tag: String, throwable: Throwable? = null, message: () -> String) {
        middlewareScope.launch(dispatcher) { // launch Coroutines to avoid blocking Store processing in case of heavy logging
            logger.log(severity = severity, tag = tag, throwable = throwable, message = message)
        }
    }
}

/**
 * Creates a middleware that logs actions, events, committed state changes, and errors.
 *
 * @param tag The tag to use for logging
 * @param severity The severity level for log messages
 * @param logger The logger implementation to use
 * @param dispatcher Optional CoroutineDispatcher override for logging operations
 * @return Middleware that logs common Store operations
 */
fun <S : State, A : Action, E : Event> simpleLogging(
    tag: String = "Tart",
    severity: Logger.Severity = Logger.Severity.Debug,
    logger: Logger = DefaultLogger,
    dispatcher: CoroutineDispatcher? = null,
): Middleware<S, A, E> {
    return object : LoggingMiddleware<S, A, E>(
        logger = logger,
        dispatcher = dispatcher,
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
