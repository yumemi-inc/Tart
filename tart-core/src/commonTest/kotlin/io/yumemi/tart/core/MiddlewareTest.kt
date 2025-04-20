package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

@OptIn(ExperimentalCoroutinesApi::class)
class MiddlewareTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // Test data source that can be controlled from tests
    class MessageDataSource {
        private val _dataFlow = MutableSharedFlow<String>()
        val dataFlow = _dataFlow.asSharedFlow()

        suspend fun simulateEmitMessage(message: String) {
            _dataFlow.emit(message)
        }
    }

    // State definitions
    data class AppState(val message: String) : State

    // Action definitions
    sealed interface AppAction : Action {
        data class UpdateMessage(val message: String) : AppAction
    }

    @Test
    fun middleware_shouldHandleDataSourceEvents() = runTest(testDispatcher) {
        // Setup data source
        val messageDataSource = MessageDataSource()

        // Create store with the date source
        val store = createTestStore(messageDataSource)

        // Verify initial state has empty message
        assertEquals(AppState(message = ""), store.currentState)

        // Emit message before any action is dispatched
        messageDataSource.simulateEmitMessage("hello")
        // Verify state remains unchanged (middleware should be active but messages are not yet processed)
        assertEquals(AppState(message = ""), store.currentState)

        // Manually dispatch update message action
        store.dispatch(AppAction.UpdateMessage("hello"))
        // Verify state updated to reflect the message
        assertEquals(AppState(message = "hello"), store.currentState)

        // Emit new message - middleware should now be actively collecting
        messageDataSource.simulateEmitMessage("good bye")
        // Verify middleware processed the message and updated state
        assertEquals(AppState(message = "good bye"), store.currentState)
    }

    private fun createTestStore(
        messageDataSource: MessageDataSource,
    ): Store<AppState, AppAction, Nothing> {
        return Store {
            initialState(AppState(message = ""))
            coroutineContext(Dispatchers.Unconfined)

            // Add the middleware that monitors data flow
            middleware(
                Middleware(
                    onStart = {
                        // Subscribe to data flow when the store starts
                        launch {
                            // Collect all messages from data source and convert to actions
                            messageDataSource.dataFlow.collect { message ->
                                // Dispatch message as an action to update the state
                                dispatch(AppAction.UpdateMessage(message = message))
                            }
                        }
                    },
                ),
            )

            state<AppState> {
                action<AppAction.UpdateMessage> {
                    // Update state with the new message
                    nextState(state.copy(message = action.message))
                }
            }
        }
    }
}
