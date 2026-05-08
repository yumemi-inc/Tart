@file:OptIn(io.yumemi.tart.core.InternalTartApi::class)

package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

internal class TestStorePatchBuilder<S : State, A : Action, E : Event> {
    private var coroutineContextPatch: CoroutineContext? = null
    private var stateSaverPatch: StateSaver<S>? = null
    private var exceptionHandlerPatch: ExceptionHandler? = null
    private var autoStartPolicyPatch: AutoStartPolicy? = null
    private var pendingActionPolicyPatch: PendingActionPolicy? = null
    private var pluginExecutionPolicyPatch: PluginExecutionPolicy? = null
    private val pluginPatches = mutableListOf<PluginPatch<S, A, E>>()

    fun coroutineContext(coroutineContext: CoroutineContext) {
        coroutineContextPatch = coroutineContext
    }

    fun stateSaver(stateSaver: StateSaver<S>) {
        stateSaverPatch = stateSaver
    }

    fun exceptionHandler(exceptionHandler: ExceptionHandler) {
        exceptionHandlerPatch = exceptionHandler
    }

    fun autoStartPolicy(policy: AutoStartPolicy) {
        autoStartPolicyPatch = policy
    }

    fun pendingActionPolicy(policy: PendingActionPolicy) {
        pendingActionPolicyPatch = policy
    }

    fun pluginExecutionPolicy(policy: PluginExecutionPolicy) {
        pluginExecutionPolicyPatch = policy
    }

    fun plugin(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        pluginPatches += PluginPatch.Append(plugins = listOf(first) + rest)
    }

    fun replacePlugins(first: Plugin<S, A, E>, vararg rest: Plugin<S, A, E>) {
        pluginPatches += PluginPatch.Replace(plugins = listOf(first) + rest)
    }

    fun clearPlugins() {
        pluginPatches += PluginPatch.Clear()
    }

    fun build(): StorePatch<S, A, E> {
        return StorePatch(
            coroutineContext = coroutineContextPatch,
            stateSaver = stateSaverPatch,
            exceptionHandler = exceptionHandlerPatch,
            autoStartPolicy = autoStartPolicyPatch,
            pendingActionPolicy = pendingActionPolicyPatch,
            pluginExecutionPolicy = pluginExecutionPolicyPatch,
            pluginPatches = pluginPatches.toList(),
        )
    }
}

@OptIn(InternalTartApi::class)
internal fun <S : State, A : Action, E : Event> Store<S, A, E>.patchForTest(
    block: TestStorePatchBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    val builder = TestStorePatchBuilder<S, A, E>()
    builder.block()
    return requireStoreInternalApi().patch(builder.build())
}

@OptIn(InternalTartApi::class)
internal fun <S : State, A : Action, E : Event> Store<S, A, E>.attachObserverForTest(
    observer: StoreObserver<S, E>,
    notifyCurrentState: Boolean = true,
) {
    requireStoreInternalApi().attachObserver(observer, notifyCurrentState)
}

@Suppress("UNCHECKED_CAST")
@OptIn(InternalTartApi::class)
private fun <S : State, A : Action, E : Event> Store<S, A, E>.requireStoreInternalApi(): StoreInternalApi<S, A, E> {
    return this as? StoreInternalApi<S, A, E>
        ?: error("Expected Tart Store to implement StoreInternalApi")
}
