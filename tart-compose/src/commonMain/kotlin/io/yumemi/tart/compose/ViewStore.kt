package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
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

    companion object {
        @Deprecated("Use viewStore() function instead")
        fun <S : State, A : Action, E : Event> create(state: S, dispatch: (action: A) -> Unit, eventFlow: Flow<E>): ViewStore<S, A, E> {
            return ViewStore(
                state = state,
                dispatch = dispatch,
                eventFlow = eventFlow,
            )
        }

        @Deprecated("Use viewStore() function instead")
        fun <S : State, A : Action, E : Event> mock(state: S): ViewStore<S, A, E> {
            return ViewStore(
                state = state,
                dispatch = {},
                eventFlow = emptyFlow(),
            )
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

@Deprecated("Use rememberViewStoreSaveable instead")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStore(saver: Saver<S?, out Any> = autoSaver(), factory: CoroutineScope.(savedState: S?) -> Store<S, A, E>): ViewStore<S, A, E> {
    var savedState: S? by rememberSaveable(stateSaver = saver) {
        mutableStateOf(null)
    }

    val scope = rememberCoroutineScope()

    val store = remember {
        // if savedState is null, the initial State specified by the developer
        factory(scope, savedState)
    }

    // store.currentState .. get the State when the Store instance is created, before the State is changed in TartStore's init() process
    val state = savedState ?: store.currentState

    LaunchedEffect(Unit) {
        // TartStore's init() is called her (see state for the first time)
        store.state.drop(1).collect { // drop(1) .. avoid unnecessary recompose when savedState is null
            savedState = it
        }
    }

    return remember(state) {
        ViewStore(
            state = state,
            dispatch = store::dispatch,
            eventFlow = store.event,
        )
    }
}

@Composable
fun <S : State> defaultStateSaver(saver: Saver<S?, out Any> = autoSaver()): StateSaver<S> {
    var savedState: S? by rememberSaveable(stateSaver = saver) {
        mutableStateOf(null)
    }

    return remember {
        object : StateSaver<S> {
            override fun save(state: S) {
                savedState = state
            }

            override fun restore(): S? {
                return savedState
            }
        }
    }
}

@Suppress("unused")
@Composable
fun <S : State, A : Action, E : Event> rememberViewStoreSaveable(
    stateSaver: StateSaver<S> = defaultStateSaver(), autoDispose: Boolean = true, factory: (savedState: S?) -> Store<S, A, E>,
): ViewStore<S, A, E> {
    val store = remember {
        val savedState = stateSaver.restore()
        factory(savedState).apply { // if savedState is null, the initial State specified by the developer
            savedState ?: run {
                stateSaver.save(currentState)
            }
        }
    }

    var state by remember { mutableStateOf(store.currentState) }

    LaunchedEffect(Unit) {
        store.state.drop(1).collect {
            state = it
            stateSaver.save(it)
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
        ViewStore(
            state = state,
            dispatch = store::dispatch,
            eventFlow = store.event,
        )
    }
}
