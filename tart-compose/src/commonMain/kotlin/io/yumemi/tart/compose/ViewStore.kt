package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.takahirom.rin.rememberRetained
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver
import io.yumemi.tart.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Suppress("unused")
@Stable
class ViewStore<S : State, A : Action, E : Event> internal constructor(
    val state: S,
    val dispatch: (action: A) -> Unit,
    val eventFlow: Flow<E>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewStore<*, *, *>) return false
        return this.state == other.state
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    @Composable
    inline fun <reified S2 : S> render(block: ViewStore<S2, A, E>.() -> Unit) {
        if (state is S2) {
            block(
                remember(state) {
                    viewStore(
                        state = state,
                        dispatch = dispatch,
                        eventFlow = eventFlow,
                    )
                },
            )
        }
    }

    @Composable
    inline fun <reified E2 : E> handle(crossinline block: ViewStore<S, A, E>.(event: E2) -> Unit) {
        LaunchedEffect(Unit) {
            eventFlow.filter { it is E2 }.collect {
                block(this@ViewStore, it as E2)
            }
        }
    }
}

fun <S : State, A : Action, E : Event> viewStore(state: S, dispatch: (action: A) -> Unit = {}, eventFlow: Flow<E> = emptyFlow()): ViewStore<S, A, E> {
    return ViewStore(
        state = state,
        dispatch = dispatch,
        eventFlow = eventFlow,
    )
}

@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(store: Store<S, A, E>, autoDispose: Boolean = false): ViewStore<S, A, E> {
    val rememberStore = remember { store } // allow different Store instances to be passed

    val state by rememberStore.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (autoDispose) {
                rememberStore.dispose()
            }
        }
    }

    return remember(state) {
        ViewStore(
            state = state,
            dispatch = rememberStore::dispatch,
            eventFlow = rememberStore.event,
        )
    }
}

private class StateSaverImpl<S : State> : StateSaver<S> {
    private var savedState: S? = null

    override fun save(state: S) {
        savedState = state
    }

    override fun restore(): S? {
        return savedState
    }
}

@Suppress("unused")
@Composable
fun <S : State> rememberStateSaver(): StateSaver<S> {
    return rememberRetained {
        StateSaverImpl()
    }
}
