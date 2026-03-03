package io.yumemi.tart.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal interface ActionDispatchController<A : Action> {
    fun attach(
        coroutineScope: CoroutineScope,
        dispatchBypassControllers: (A) -> Unit,
    )

    fun shouldDispatch(action: A): Boolean

    fun onDispose()
}

internal class DebounceActionDispatchController<A : Action>(
    private val timeoutMillis: Long,
    private val predicate: (A) -> Boolean,
    private val keySelector: (A) -> Any?,
) : ActionDispatchController<A> {

    private var attachedCoroutineScope: CoroutineScope? = null
    private var attachedDispatchBypassControllers: ((A) -> Unit)? = null

    private val jobsByKey = mutableMapOf<Any?, Job>()

    override fun attach(
        coroutineScope: CoroutineScope,
        dispatchBypassControllers: (A) -> Unit,
    ) {
        attachedCoroutineScope = coroutineScope
        attachedDispatchBypassControllers = dispatchBypassControllers
    }

    override fun shouldDispatch(action: A): Boolean {
        if (!predicate(action)) return true
        val coroutineScope = attachedCoroutineScope ?: return true
        val dispatchBypassControllers = attachedDispatchBypassControllers ?: return true

        val key = keySelector(action)
        jobsByKey.remove(key)?.cancel()
        jobsByKey[key] = coroutineScope.launch {
            delay(timeoutMillis)
            dispatchBypassControllers(action)
        }
        return false
    }

    override fun onDispose() {
        jobsByKey.values.forEach { it.cancel() }
        jobsByKey.clear()
    }
}

internal class ThrottleActionDispatchController<A : Action>(
    private val timeoutMillis: Long,
    private val predicate: (A) -> Boolean,
    private val keySelector: (A) -> Any?,
) : ActionDispatchController<A> {

    private var attachedCoroutineScope: CoroutineScope? = null
    private val activeWindowJobsByKey = mutableMapOf<Any?, Job>()

    override fun attach(
        coroutineScope: CoroutineScope,
        dispatchBypassControllers: (A) -> Unit,
    ) {
        attachedCoroutineScope = coroutineScope
    }

    override fun shouldDispatch(action: A): Boolean {
        if (!predicate(action)) return true

        val coroutineScope = attachedCoroutineScope ?: return true
        val key = keySelector(action)
        val activeWindowJob = activeWindowJobsByKey[key]
        if (activeWindowJob == null || !activeWindowJob.isActive) {
            activeWindowJobsByKey[key] = coroutineScope.launch {
                delay(timeoutMillis)
            }
            return true
        }
        return false
    }

    override fun onDispose() {
        activeWindowJobsByKey.values.forEach { it.cancel() }
        activeWindowJobsByKey.clear()
    }
}
