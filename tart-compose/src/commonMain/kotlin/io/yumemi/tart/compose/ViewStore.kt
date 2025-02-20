package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Suppress("unused")
@Stable
class ViewStore<S : State, A : Action, E : Event> private constructor(
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
                create(
                    state = state,
                    dispatch = dispatch,
                    eventFlow = eventFlow,
                ),
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

    companion object {
        fun <S : State, A : Action, E : Event> create(state: S, dispatch: (action: A) -> Unit, eventFlow: Flow<E>): ViewStore<S, A, E> {
            return ViewStore(
                state = state,
                dispatch = dispatch,
                eventFlow = eventFlow,
            )
        }

        fun <S : State, A : Action, E : Event> mock(state: S): ViewStore<S, A, E> {
            return ViewStore(
                state = state,
                dispatch = {},
                eventFlow = emptyFlow(),
            )
        }
    }
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
        ViewStore.create(
            state = state,
            dispatch = rememberStore::dispatch,
            eventFlow = rememberStore.event,
        )
    }
}

@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(
    composeSaver: Saver<S?, out Any> = autoSaver(), autoDispose: Boolean = true, factory: (savedState: S?) -> Store<S, A, E>,
): ViewStore<S, A, E> {
    var savedState: S? by rememberSaveable(stateSaver = composeSaver) {
        mutableStateOf(null)
    }

    val store = remember {
        // if savedState is null, the initial State specified by the developer
        factory(savedState)
    }

    // store.currentState .. get the State when the Store instance is created, before the State is changed in TartStore's init() process
    val state = savedState ?: store.currentState

    LaunchedEffect(Unit) {
        // TartStore's init() is called her (see state for the first time)
        store.state.drop(1).collect { // drop(1) .. avoid unnecessary recompose when savedState is null
            savedState = it
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (autoDispose) {
                store.dispose()
            }
        }
    }

    return remember(state) {
        ViewStore.create(
            state = state,
            dispatch = store::dispatch,
            eventFlow = store.event,
        )
    }
}

interface StateSaver<S : State> {
    fun save(state: S)
    fun restore(): S?
}

@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(
    stateSaver: StateSaver<S>, autoDispose: Boolean = true, factory: (savedState: S?) -> Store<S, A, E>,
): ViewStore<S, A, E> {
    val store = remember {
        // if savedState is null, the initial State specified by the developer
        factory(stateSaver.restore())
    }

    val state by store.state.collectAsState()

    LaunchedEffect(state) {
        stateSaver.save(state)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (autoDispose) {
                store.dispose()
            }
        }
    }

    return remember(state) {
        ViewStore.create(
            state = state,
            dispatch = store::dispatch,
            eventFlow = store.event,
        )
    }
}
