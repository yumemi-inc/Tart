package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

/**
 * Store wrapper class for use with Compose UI.
 * Provides state, action dispatching, and event handling.
 *
 * @param state The current state
 * @param dispatch Function to dispatch actions
 * @param eventFlow Flow to receive events
 */
@Suppress("unused")
@Stable
class ViewStore<S : State, A : Action, E : Event>(
    val state: S,
    val dispatch: (action: A) -> Unit = {},
    val eventFlow: Flow<E> = emptyFlow(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewStore<*, *, *>) return false
        return this.state == other.state
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    /**
     * Renders UI for a state of a specified type.
     *
     * @param block Composable function to perform rendering
     */
    @Composable
    inline fun <reified S2 : S> render(block: ViewStore<S2, A, E>.() -> Unit) {
        if (state is S2) {
            block(
                remember(state) {
                    ViewStore(
                        state = state,
                        dispatch = dispatch,
                        eventFlow = eventFlow,
                    )
                },
            )
        }
    }

    /**
     * Handles events of a specified type.
     *
     * @param block Callback function to process the event
     */
    @Composable
    inline fun <reified E2 : E> handle(crossinline block: ViewStore<S, A, E>.(event: E2) -> Unit) {
        LaunchedEffect(Unit) {
            eventFlow.filter { it is E2 }.collect {
                block(this@ViewStore, it as E2)
            }
        }
    }
}

/**
 * Composable function that creates and returns a new ViewStore instance from a Store.
 * Monitors state changes in the Store and triggers UI redrawing.
 *
 * @param store The source Store instance
 * @param autoDispose Whether to dispose the Store when the component is disposed
 * @return A new ViewStore instance
 */
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
