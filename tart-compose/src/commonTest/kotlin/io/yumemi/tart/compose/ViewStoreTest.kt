package io.yumemi.tart.compose

import io.yumemi.tart.core.State
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViewStoreTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun viewStore_equalsWorksCorrectly() = runTest(testDispatcher) {
        val viewStore1 = ViewStore<CounterState, Nothing, Nothing>(CounterState(10))
        val viewStore2 = ViewStore<CounterState, Nothing, Nothing>(CounterState(10))
        val viewStore3 = ViewStore<CounterState, Nothing, Nothing>(CounterState(20))

        assertEquals(viewStore1, viewStore2)
        assertNotEquals(viewStore1, viewStore3)
    }
}

private data class CounterState(val count: Int) : State
