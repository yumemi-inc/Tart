package io.yumemi.tart.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreMiddlewareExecutionPolicyTest {

    data class AppState(val count: Int = 0) : State

    sealed interface AppAction : Action {
        data object Increment : AppAction
    }

    private fun createStore(
        testDispatcher: TestDispatcher,
        records: MutableList<String>,
        firstMiddlewareStarted: CompletableDeferred<Unit>,
        secondMiddlewareStarted: CompletableDeferred<Unit>,
        firstMiddlewareCanFinish: CompletableDeferred<Unit>,
        setupPolicy: MiddlewareExecutionPolicy = MiddlewareExecutionPolicy.CONCURRENT,
        overrides: Overrides<AppState, AppAction, Nothing> = {},
    ): Store<AppState, AppAction, Nothing> {
        return Store(
            initialState = AppState(),
            overrides = overrides,
        ) {
            coroutineContext(testDispatcher)
            middlewareExecutionPolicy(setupPolicy)

            middleware(
                Middleware(
                    beforeActionDispatch = { _, _ ->
                        records += "first:start"
                        firstMiddlewareStarted.complete(Unit)
                        firstMiddlewareCanFinish.await()
                        records += "first:end"
                    },
                ),
                Middleware(
                    beforeActionDispatch = { _, _ ->
                        records += "second:start"
                        secondMiddlewareStarted.complete(Unit)
                        records += "second:end"
                    },
                ),
            )

            state<AppState> {
                action<AppAction.Increment> {
                    nextState(state.copy(count = state.count + 1))
                }
            }
        }
    }

    @Test
    fun concurrent_isDefaultAndAllowsOtherMiddlewareToRunWhileOneIsSuspended() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val records = mutableListOf<String>()
        val firstMiddlewareStarted = CompletableDeferred<Unit>()
        val secondMiddlewareStarted = CompletableDeferred<Unit>()
        val firstMiddlewareCanFinish = CompletableDeferred<Unit>()
        val store = createStore(
            testDispatcher = testDispatcher,
            records = records,
            firstMiddlewareStarted = firstMiddlewareStarted,
            secondMiddlewareStarted = secondMiddlewareStarted,
            firstMiddlewareCanFinish = firstMiddlewareCanFinish,
        )

        store.dispatch(AppAction.Increment)
        runCurrent()

        assertTrue(firstMiddlewareStarted.isCompleted)
        assertTrue(secondMiddlewareStarted.isCompleted)
        assertEquals(
            listOf("first:start", "second:start", "second:end"),
            records,
        )

        firstMiddlewareCanFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("first:start", "second:start", "second:end", "first:end"),
            records,
        )
        assertEquals(AppState(count = 1), store.currentState)
    }

    @Test
    fun inRegistrationOrder_waitsForPreviousMiddlewareToFinishBeforeStartingNextOne() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val records = mutableListOf<String>()
        val firstMiddlewareStarted = CompletableDeferred<Unit>()
        val secondMiddlewareStarted = CompletableDeferred<Unit>()
        val firstMiddlewareCanFinish = CompletableDeferred<Unit>()
        val store = createStore(
            testDispatcher = testDispatcher,
            records = records,
            firstMiddlewareStarted = firstMiddlewareStarted,
            secondMiddlewareStarted = secondMiddlewareStarted,
            firstMiddlewareCanFinish = firstMiddlewareCanFinish,
            setupPolicy = MiddlewareExecutionPolicy.IN_REGISTRATION_ORDER,
        )

        store.dispatch(AppAction.Increment)
        runCurrent()

        assertTrue(firstMiddlewareStarted.isCompleted)
        assertFalse(secondMiddlewareStarted.isCompleted)
        assertEquals(listOf("first:start"), records)

        firstMiddlewareCanFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("first:start", "first:end", "second:start", "second:end"),
            records,
        )
        assertEquals(AppState(count = 1), store.currentState)
    }

    @Test
    fun overrides_canReplaceMiddlewareExecutionPolicy() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val records = mutableListOf<String>()
        val firstMiddlewareStarted = CompletableDeferred<Unit>()
        val secondMiddlewareStarted = CompletableDeferred<Unit>()
        val firstMiddlewareCanFinish = CompletableDeferred<Unit>()
        val store = createStore(
            testDispatcher = testDispatcher,
            records = records,
            firstMiddlewareStarted = firstMiddlewareStarted,
            secondMiddlewareStarted = secondMiddlewareStarted,
            firstMiddlewareCanFinish = firstMiddlewareCanFinish,
            setupPolicy = MiddlewareExecutionPolicy.CONCURRENT,
            overrides = {
                middlewareExecutionPolicy(MiddlewareExecutionPolicy.IN_REGISTRATION_ORDER)
            },
        )

        store.dispatch(AppAction.Increment)
        runCurrent()

        assertTrue(firstMiddlewareStarted.isCompleted)
        assertFalse(secondMiddlewareStarted.isCompleted)
        assertEquals(listOf("first:start"), records)

        firstMiddlewareCanFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("first:start", "first:end", "second:start", "second:end"),
            records,
        )
        assertEquals(AppState(count = 1), store.currentState)
    }
}
