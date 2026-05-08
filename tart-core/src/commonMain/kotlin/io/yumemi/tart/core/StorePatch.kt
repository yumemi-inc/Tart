package io.yumemi.tart.core

import kotlin.coroutines.CoroutineContext

@InternalTartApi
data class StorePatch<S : State, A : Action, E : Event>(
    val coroutineContext: CoroutineContext? = null,
    val stateSaver: StateSaver<S>? = null,
    val exceptionHandler: ExceptionHandler? = null,
    val autoStartPolicy: AutoStartPolicy? = null,
    val pendingActionPolicy: PendingActionPolicy? = null,
    val pluginExecutionPolicy: PluginExecutionPolicy? = null,
    val pluginPatches: List<PluginPatch<S, A, E>> = emptyList(),
)

@InternalTartApi
sealed interface PluginPatch<S : State, A : Action, E : Event> {
    data class Append<S : State, A : Action, E : Event>(val plugins: List<Plugin<S, A, E>>) : PluginPatch<S, A, E>
    data class Replace<S : State, A : Action, E : Event>(val plugins: List<Plugin<S, A, E>>) : PluginPatch<S, A, E>
    class Clear<S : State, A : Action, E : Event> : PluginPatch<S, A, E>
}
