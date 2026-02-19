package io.yumemi.tart.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withRunningRecomposer
import io.yumemi.tart.core.Action
import io.yumemi.tart.core.Event
import io.yumemi.tart.core.State
import io.yumemi.tart.core.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViewStoreJvmTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun render_callsBlockOnlyForMatchingState() = runTest(testDispatcher) {
        val renderedValues = mutableListOf<Int>()

        withComposition(
            content = {
                ViewStore<UiState, Nothing, Nothing>(state = UiState.Ready(10))
                    .render<UiState.Ready> {
                        renderedValues += state.value
                    }

                ViewStore<UiState, Nothing, Nothing>(state = UiState.Loading)
                    .render<UiState.Ready> {
                        renderedValues += state.value
                    }
            },
        )

        assertEquals(listOf(10), renderedValues)
    }

    @Test
    fun handle_collectsOnlySpecifiedEventType() = runTest(testDispatcher) {
        val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
        val handled = mutableListOf<UiEvent.ValueChanged>()

        withComposition(
            content = {
                ViewStore<UiState, UiAction, UiEvent>(
                    state = UiState.Ready(0),
                    eventFlow = events,
                ).handle<UiEvent.ValueChanged> { event ->
                    handled += event
                }
            },
            afterSetContent = {
                assertTrue(events.tryEmit(UiEvent.Reset))
                assertTrue(events.tryEmit(UiEvent.ValueChanged(42)))
            },
        )

        assertEquals(listOf(UiEvent.ValueChanged(42)), handled)
    }

    @Test
    fun rememberViewStore_providesCurrentStateAndDispatchesAction() = runTest(testDispatcher) {
        val store = TestStore(UiState.Ready(1))
        lateinit var viewStore: ViewStore<UiState, UiAction, UiEvent>

        withComposition(
            content = {
                viewStore = rememberViewStore { store }
            },
            afterSetContent = {
                assertEquals(UiState.Ready(1), viewStore.state)

                viewStore.dispatch(UiAction.Increment)
                assertEquals(listOf<UiAction>(UiAction.Increment), store.dispatchedActions)
            },
        )
    }

    @Test
    fun rememberViewStore_disposeBehaviorUsesInitialAutoDisposeValue() = runTest(testDispatcher) {
        val neverDisposeStore = TestStore(UiState.Ready(0))
        var autoDispose = mutableStateOf(false)

        withComposition(
            content = {
                rememberViewStore(autoDispose = autoDispose.value) { neverDisposeStore }
            },
            afterSetContent = {
                autoDispose.value = true
            },
        )

        assertEquals(0, neverDisposeStore.disposeCount)

        val disposeStore = TestStore(UiState.Ready(0))
        withComposition(
            content = {
                rememberViewStore(autoDispose = true) { disposeStore }
            },
        )

        assertEquals(1, disposeStore.disposeCount)
    }

    @Test
    fun rememberViewStore_withRealStore_reflectsStateTransitionAndDispatch() = runTest(testDispatcher) {
        val store = Store<UiState, UiAction, UiEvent>(initialState = UiState.Loading) {
            coroutineContext(coroutineContext)
            state<UiState.Loading> {
                enter {
                    nextState(UiState.Ready(0))
                }
            }
            state<UiState.Ready> {
                action<UiAction.Increment> {
                    nextState(state.copy(value = state.value + 1))
                }
            }
        }

        lateinit var viewStore: ViewStore<UiState, UiAction, UiEvent>
        withComposition(
            content = {
                viewStore = rememberViewStore { store }
            },
            afterSetContent = {
                assertEquals(UiState.Ready(0), viewStore.state)
                viewStore.dispatch(UiAction.Increment)
            },
        )

        assertEquals(UiState.Ready(1), store.currentState)
        store.dispose()
    }

    @Test
    fun rememberViewStore_withRealStore_autoDisposeStopsFurtherDispatch() = runTest(testDispatcher) {
        val store = Store<UiState, UiAction, UiEvent>(initialState = UiState.Ready(0)) {
            coroutineContext(coroutineContext)
            state<UiState.Ready> {
                action<UiAction.Increment> {
                    nextState(state.copy(value = state.value + 1))
                }
            }
        }

        withComposition(
            content = {
                rememberViewStore(autoDispose = true) { store }
            },
            afterSetContent = {
                store.dispatch(UiAction.Increment)
            },
        )

        assertEquals(UiState.Ready(1), store.currentState)

        store.dispatch(UiAction.Increment)
        testScheduler.runCurrent()

        assertEquals(UiState.Ready(1), store.currentState)
    }

    @Test
    fun rememberViewStore_usesStateFlowValueForInitialRenderingBeforeFirstCollectEmission() = runTest(testDispatcher) {
        val enterGate = CompletableDeferred<Unit>()
        val store = Store<UiState, UiAction, UiEvent>(initialState = UiState.Loading) {
            coroutineContext(coroutineContext)
            state<UiState.Loading> {
                enter(coroutineDispatcher = testDispatcher) {
                    enterGate.await()
                    nextState(UiState.Ready(0))
                }
            }
        }

        lateinit var viewStore: ViewStore<UiState, UiAction, UiEvent>

        withComposition(
            content = {
                viewStore = rememberViewStore(autoDispose = true) { store }
            },
            afterSetContent = {
                assertEquals(UiState.Loading, viewStore.state)
                assertEquals(UiState.Loading, store.currentState)
                assertTrue(enterGate.complete(Unit))
                testScheduler.runCurrent()
                assertEquals(UiState.Ready(0), store.currentState)
            },
        )
    }

    private suspend fun TestScope.withComposition(
        content: @Composable () -> Unit,
        afterSetContent: suspend () -> Unit = {},
    ) {
        val frameClock = BroadcastFrameClock()
        var frameTimeNanos = 0L

        suspend fun pumpFrame() {
            testScheduler.runCurrent()
            frameTimeNanos += 16_000_000L
            frameClock.sendFrame(frameTimeNanos)
            testScheduler.runCurrent()
        }

        withContext(frameClock) {
            withRunningRecomposer { recomposer ->
                val composition = Composition(NoOpApplier(), recomposer)
                try {
                    composition.setContent(content)
                    repeat(2) { pumpFrame() }

                    afterSetContent()
                    repeat(2) { pumpFrame() }
                } finally {
                    composition.dispose()
                    pumpFrame()
                }
            }
        }
    }
}

private sealed interface UiState : State {
    data object Loading : UiState
    data class Ready(val value: Int) : UiState
}

private sealed interface UiAction : Action {
    data object Increment : UiAction
}

private sealed interface UiEvent : Event {
    data object Reset : UiEvent
    data class ValueChanged(val value: Int) : UiEvent
}

private class TestStore(
    initialState: UiState,
) : Store<UiState, UiAction, UiEvent> {
    override val state = MutableStateFlow(initialState)
    private val eventFlow = MutableSharedFlow<UiEvent>()
    override val event: Flow<UiEvent> = eventFlow
    override val currentState: UiState get() = state.value

    val dispatchedActions = mutableListOf<UiAction>()
    var disposeCount: Int = 0
        private set

    override fun dispatch(action: UiAction) {
        dispatchedActions += action
    }

    override fun collectState(state: (UiState) -> Unit) = Unit

    override fun collectEvent(event: (UiEvent) -> Unit) = Unit

    override fun dispose() {
        disposeCount++
    }
}

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun remove(index: Int, count: Int) = Unit

    override fun onClear() = Unit
}
