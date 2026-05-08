@file:OptIn(InternalTartApi::class)

package io.yumemi.tart.core

internal fun <S : State, A : Action, E : Event> Store<S, A, E>.patchForTest(
    block: StorePatchBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> {
    val patch = StorePatchBuilder<S, A, E>().apply(block).build()
    return requireStoreInternalApi().patch(patch)
}

internal fun <S : State, A : Action, E : Event> Store<S, A, E>.attachObserverForTest(
    observer: StoreObserver<S, E>,
    notifyCurrentState: Boolean = true,
) {
    requireStoreInternalApi().attachObserver(observer, notifyCurrentState)
}

internal suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.dispatchAndWaitForTest(action: A) {
    requireStoreInternalApi().dispatchAndWait(action)
}

internal suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.startAndWaitForTest() {
    requireStoreInternalApi().startAndWait()
}

@Suppress("UNCHECKED_CAST")
private fun <S : State, A : Action, E : Event> Store<S, A, E>.requireStoreInternalApi(): StoreInternalApi<S, A, E> {
    return this as? StoreInternalApi<S, A, E>
        ?: error("Expected Tart Store to implement StoreInternalApi")
}
