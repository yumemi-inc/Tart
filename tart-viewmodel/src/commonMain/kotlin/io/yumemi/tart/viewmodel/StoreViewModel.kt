package io.yumemi.tart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StoreBuilder

abstract class StoreViewModel<S: State, A: Action, E: Event>(
    storeKey: String,
    builder: StoreBuilder<S, A, E>.() -> Unit,
) : ViewModel() {
    constructor(
        builder: StoreBuilder<S, A, E>.() -> Unit,
    ) : this(storeKey = TART_STORE_KEY, builder = builder)

    val store = ViewModelStore<S, A, E>(key = storeKey) {
        coroutineContext(viewModelScope.coroutineContext)

        builder()
    }
}
