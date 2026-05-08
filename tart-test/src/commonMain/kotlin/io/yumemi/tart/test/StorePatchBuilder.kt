@file:OptIn(io.yumemi.tart.core.InternalTartApi::class)

package io.yumemi.tart.test

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.AutoStartPolicy
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.ExceptionHandler
import io.yumemi.tart.core.PendingActionPolicy
import io.yumemi.tart.core.Plugin
import io.yumemi.tart.core.PluginPatch
import io.yumemi.tart.core.PluginExecutionPolicy
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver
import io.yumemi.tart.core.StorePatch
import kotlin.coroutines.CoroutineContext

/**
 * Builder used by `Store.patch { ... }` in `:tart-test`.
 *
 * This builder only exposes non-state Store configuration.
 */
@Suppress("unused")
class StorePatchBuilder<S : State, A : Action, E : Event> internal constructor() {
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

    internal fun build(): StorePatch<S, A, E> {
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
