package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Suppress("unused")
class ComposeStore<S : State, A : Action, E : Event> private constructor(
    val state: S,
    val dispatch: (action: A) -> Unit,
    val eventFlow: Flow<E>,
) {
    @Composable
    inline fun <reified S2 : S> render(block: ComposeStore<S2, A, E>.() -> Unit) {
        if (state is S2) {
            block(
                mock(
                    state = state,
                    dispatch = dispatch,
                    eventFlow = eventFlow,
                ),
            )
        }
    }

    @Composable
    inline fun <reified E2 : E> handle(crossinline block: ComposeStore<S, A, E>.(event: E2) -> Unit) {
        LaunchedEffect(Unit) {
            eventFlow.filter { it is E2 }.collect {
                block(this@ComposeStore, it as E2)
            }
        }
    }

    companion object {
        @Composable
        fun <S : State, A : Action, E : Event> create(store: Store<S, A, E>): ComposeStore<S, A, E> {
            val state by store.state.collectAsState()
            return ComposeStore(
                state = state,
                dispatch = store::dispatch,
                eventFlow = store.event,
            )
        }

        fun <S : State, A : Action, E : Event> mock(state: S, dispatch: (action: A) -> Unit = {}, eventFlow: Flow<E> = emptyFlow()): ComposeStore<S, A, E> {
            return ComposeStore(
                state = state,
                dispatch = dispatch,
                eventFlow = eventFlow,
            )
        }
    }
}
