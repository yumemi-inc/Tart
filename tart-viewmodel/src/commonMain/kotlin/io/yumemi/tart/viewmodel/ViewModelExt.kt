package io.yumemi.tart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import io.yumemi.tart.core.StoreBuilder

internal const val TART_STORE_KEY = "io.yumemi.tart.ViewModelStore"

@Suppress("FunctionName")
fun <S: State, A: Action, E: Event> ViewModel.ViewModelStore(
    key: String = TART_STORE_KEY,
    builder: StoreBuilder<S, A, E>.() -> Unit,
): Store<S, A, E> = getOrCreateCloseable(
    key = key,
    create = {
        Store {
            coroutineContext(viewModelScope.coroutineContext)
            builder()
        }
    },
    close = Store<S, A, E>::dispose,
)

private inline fun <reified T: Any> ViewModel.getOrCreateCloseable(
    key: String,
    create: () -> T,
    crossinline close: T.()->Unit,
) = getCloseable(key)
    ?: create().also {
        addCloseable(key, object : AutoCloseable {
            override fun close() {
                it.close()
            }
        })
    }
