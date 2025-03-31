package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreExceptionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun store_shouldUseExceptionHandlerOnError() = runTest(testDispatcher) {
        var handledException: Throwable? = null

        val store = createTestStore(
            initialState = ExceptionState(0),
            exceptionHandler = ExceptionHandler { error ->
                handledException = error
            },
        )

        store.state // access state to initialize the store

        assertNotNull(handledException)
    }
}

private data class ExceptionState(val value: Int) : State

private fun createTestStore(
    initialState: ExceptionState,
    exceptionHandler: ExceptionHandler,
): Store<ExceptionState, Nothing, Nothing> {
    return Store(initialState) {
        this.coroutineContext(Dispatchers.Unconfined)
        exceptionHandler(exceptionHandler)
        state<ExceptionState> {
            enter {
                throw RuntimeException("error")
            }
        }
    }
}
