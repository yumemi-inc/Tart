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
    val dispatch: (A) -> Unit = {},
    @PublishedApi internal val eventFlow: Flow<E> = emptyFlow(),
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
    @Suppress("ComposableNaming")
    @Composable
    inline fun <reified S2 : S> render(block: @Composable ViewStore<S2, A, E>.() -> Unit) {
        if (state is S2) {
            @Suppress("UNCHECKED_CAST")
            block(this as ViewStore<S2, A, E>)
        }
    }

    /**
     * Handles events of a specified type.
     *
     * @param block Function to process the event
     */
    @Suppress("ComposableNaming")
    @Composable
    inline fun <reified E2 : E> handle(crossinline block: ViewStore<S, A, E>.(E2) -> Unit) {
        LaunchedEffect(eventFlow) {
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
 * @param key Key used to remember and retain the Store instance
 * @param autoDispose Whether to dispose the Store when the component is disposed
 * @param store Composable function to create the source Store instance
 * @return A ViewStore instance
 */
@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(key: Any? = null, autoDispose: Boolean = false, store: @Composable () -> Store<S, A, E>): ViewStore<S, A, E> {
    val fixedAutoDispose = remember { autoDispose }

    val holder = remember(key) {
        object {
            var value: Store<S, A, E>? = null
        }
    }
    val rememberedStore = holder.value ?: store().also { holder.value = it }

    val state by rememberedStore.state.collectAsState()

    DisposableEffect(rememberedStore) {
        onDispose {
            if (fixedAutoDispose) {
                rememberedStore.dispose()
            }
        }
    }

    return remember(state) {
        ViewStore(
            state = state,
            dispatch = rememberedStore::dispatch,
            eventFlow = rememberedStore.event,
        )
    }
}
