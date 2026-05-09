package io.yumemi.tart.core

/**
 * Internal bridge API exposed from `:tart-core` so companion modules such as `:tart-test`
 * can provide extensions without depending on internal implementation types.
 *
 * Most users should call extensions from `:tart-test` instead of using this interface directly.
 */
@InternalTartApi
interface StoreInternalApi<S : State, A : Action, E : Event> {
    suspend fun startAndWait()
    suspend fun dispatchAndWait(action: A)
    fun patch(patch: StorePatch<S, A, E>): Store<S, A, E>
}
