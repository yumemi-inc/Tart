package io.yumemi.tart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StoreBuilder
import io.yumemi.tart.core.Store

/**
 * A [ViewModel] with a [store][StoreViewModel.store] property that is its own dedicated [Store].
 * The [store][StoreViewModel.store] property is created by [ViewModelStore], enabling it to offer features more tightly integrated with the [ViewModel].
 * (For details, refer to the [ViewModelStore] documentation.)
 *
 * # Usage
 *
 * ```kt
 * class MyViewModel : StoreViewModel({
 *   // TODO
 * })
 *
 * val viewModel: MyViewModel = // ...
 * viewModel.store /* : Store<...> */
 * ```
 *
 * @param storeKey The key for the [Store] held within the [ViewModel]. A Store instance is created within the [ViewModel] for each of these keys.
 * @param builder Configuration block to customize the [Store]
 * @property store
 */
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
