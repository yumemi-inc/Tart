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
 * Compose-friendly wrapper around a [Store] snapshot.
 *
 * It exposes the latest state, a dispatch function, and access to the Store's event stream.
 *
 * @param state The current state
 * @param dispatch Function to dispatch actions
 * @param eventFlow Flow used to receive one-off events
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
     * Invokes [block] only when the current [state] is of type [S2].
     *
     * Inside [block], this [ViewStore] is narrowed to [S2].
     *
     * @param block Composable function to render the narrowed state
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
     * Collects only events of type [E2] while this composable is in the composition.
     *
     * Collection starts after the composable enters the composition.
     * Events emitted earlier are not replayed.
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
 * Remembers a [Store], collects its state as Compose state, and exposes it as a [ViewStore].
 *
 * Collecting [Store.state] starts the Store immediately.
 * Because [ViewStore.handle] starts collecting later from a [LaunchedEffect], startup events such
 * as events emitted from an initial `enter {}` handler may be emitted before handlers in the same
 * composition begin observing them.
 *
 * The [store] lambda is used only when a new remembered Store must be created for [key].
 *
 * @param key Key used to remember and retain the Store instance
 * @param autoClose Whether to close the Store when the composable leaves the composition
 * @param store Composable function to create the source Store instance
 * @return A ViewStore instance
 */
@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(key: Any? = null, autoClose: Boolean = false, store: @Composable () -> Store<S, A, E>): ViewStore<S, A, E> {
    val fixedAutoClose = remember { autoClose }

    val holder = remember(key) {
        object {
            var value: Store<S, A, E>? = null
        }
    }
    val rememberedStore = holder.value ?: store().also { holder.value = it }

    val state by rememberedStore.state.collectAsState()

    DisposableEffect(rememberedStore) {
        onDispose {
            if (fixedAutoClose) {
                rememberedStore.close()
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
