package io.yumemi.tart.logging

import co.touchlab.kermit.Severity
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.Middleware
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger.Companion as Kermit

@Suppress("unused")
open class LoggingMiddleware<S : State, A : Action, E : Event>(
    private val logger: Logger = TartLogger(),
    private val tag: String = "Tart",
    private val severity: Logger.Severity = Logger.Severity.Debug,
) : Middleware<S, A, E> {
    private lateinit var coroutineScope: CoroutineScope
    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, exception -> onError(exception) }

    final override suspend fun onInit(store: Store<S, A, E>, coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
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
        coroutineScope.launch(exceptionHandler) {
            logger.log(severity = severity, tag = tag, throwable = throwable, message = message())
        }
    }

    protected open fun onError(error: Throwable) {
        Kermit.log(
            severity = Severity.Error,
            tag = tag,
            throwable = error,
            message = "An error occurred during logging in LoggingMiddleware.",
        )
    }
}
