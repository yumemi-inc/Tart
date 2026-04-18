package io.yumemi.tart.core

/**
 * Extension point for observing Store state snapshots and emitted events.
 *
 * For most test cases, prefer [StoreRecorder] or [Store.createRecorder].
 * Implement this interface when you need custom recording or observation behavior.
 */
interface StoreObserver<S : State, E : Event> {
    /**
     * Called when a state snapshot is observed.
     */
    fun onState(state: S)

    /**
     * Called when an event emission is observed.
     */
    fun onEvent(event: E)
}

/**
 * Factory function to easily create a StoreObserver instance.
 *
 * @param onState Callback invoked when a state snapshot is observed
 * @param onEvent Callback invoked when an event emission is observed
 * @return A new StoreObserver instance
 */
fun <S : State, E : Event> StoreObserver(
    onState: (S) -> Unit = {},
    onEvent: (E) -> Unit = {},
): StoreObserver<S, E> = object : StoreObserver<S, E> {
    override fun onState(state: S) {
        onState.invoke(state)
    }

    override fun onEvent(event: E) {
        onEvent.invoke(event)
    }
}

/**
 * Default in-memory [StoreObserver] implementation for tests.
 *
 * This API currently lives in `:tart-core`, but it is intended primarily for testing support.
 * It may be moved to a dedicated testing module in the future.
 */
@ExperimentalTartApi
class StoreRecorder<S : State, E : Event> : StoreObserver<S, E> {
    private val recordedStates = mutableListOf<S>()
    private val recordedEvents = mutableListOf<E>()

    /**
     * State snapshots recorded for this Store.
     */
    val states: List<S> = recordedStates

    /**
     * Events recorded for this Store.
     */
    val events: List<E> = recordedEvents

    /**
     * Clears all recorded history.
     */
    fun clear() {
        recordedStates.clear()
        recordedEvents.clear()
    }

    override fun onState(state: S) {
        recordedStates.add(state)
    }

    override fun onEvent(event: E) {
        recordedEvents.add(event)
    }
}

/**
 * Creates and attaches the default [StoreRecorder] for this Store.
 *
 * Prefer this in tests unless you need a custom [StoreObserver] implementation.
 *
 * @param notifyCurrentState Whether to notify the recorder with the current state immediately
 * @return The attached [StoreRecorder]
 */
@ExperimentalTartApi
fun <S : State, A : Action, E : Event> Store<S, A, E>.createRecorder(
    notifyCurrentState: Boolean = true,
): StoreRecorder<S, E> {
    val recorder = StoreRecorder<S, E>()
    attachObserver(recorder, notifyCurrentState)
    return recorder
}
