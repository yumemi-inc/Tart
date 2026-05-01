package io.yumemi.tart.test

import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.ExperimentalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import io.yumemi.tart.core.StoreObserver

/**
 * Default in-memory [StoreObserver] implementation for tests.
 *
 * It records every observed state snapshot and event in insertion order.
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
 * Creates and attaches a [StoreRecorder] for this Store.
 *
 * Prefer this in tests unless you need a custom [StoreObserver] implementation.
 *
 * @param notifyCurrentState Whether to record the current state snapshot immediately
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
