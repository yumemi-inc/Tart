package io.yumemi.tart.core

/**
 * Extension point for observing Store state snapshots and emitted events.
 *
 * For most test cases, prefer `StoreRecorder` or `createRecorder()` from `:tart-test`.
 * Implement this interface when you need custom recording or observation behavior.
 */
interface StoreObserver<in S : State, in E : Event> {
    /**
     * Called when the Store exposes a state snapshot to observers.
     *
     * Depending on how the observer is attached, this may be called immediately with the
     * current state before the Store starts processing actions.
     *
     * @param state The observed state snapshot
     */
    fun onState(state: S)

    /**
     * Called when the Store emits an event.
     *
     * @param event The emitted event
     */
    fun onEvent(event: E)
}

/**
 * Internal bridge API exposed from `:tart-core` so companion modules such as `:tart-test`
 * can provide extensions without depending on internal implementation types.
 *
 * Most users should call extensions from `:tart-test` instead of using this interface directly.
 */
@InternalTartApi
interface StoreInternalApi<S : State, A : Action, E : Event> {
    suspend fun dispatchAndWait(action: A)
    fun attachObserver(observer: StoreObserver<S, E>, notifyCurrentState: Boolean = true)
}
