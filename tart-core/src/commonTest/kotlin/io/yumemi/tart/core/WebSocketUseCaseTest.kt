package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

// State Transition Diagram:
//
// ┌───────────────────────────────────────────────────┐
// │                                                   │
// │            Disconnected       Error               │
// │                 ▲               ▲                 │
// │                 │               │                 │
// │                 │   ┌───────────┘                 │
// │                 │   │                             │
// │                 ▼   ▼                             │
// │  ┌─────────────────────────────────────────────┐  │
// │  │              Connected                      │  │
// │  │                                             │  │
// │  │   Status: Connecting/Connected/Disconnected │  │
// │  │   Messages: Received real-time updates      │  │
// │  │                                             │  │
// │  └─────────────────────────────────────────────┘  │
// │                                                   │
// └───────────────────────────────────────────────────┘
//
// Disconnected: Initial state, no connection to WebSocket server
// Connected: Managing WebSocket connection with different internal statuses
// Error: Error state for handling connection failures

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WebSocketUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun webSocket_shouldConnectAndHandleMessages() = runTest(testDispatcher) {
        // Mock implementation of WebSocket service
        val mockWebSocket = MockWebSocketService()

        // Create store with disconnected state
        val store = createTestStore(mockWebSocket)

        // Initial state should be Disconnected
        assertIs<WebSocketState.Disconnected>(store.currentState)

        // Connect to WebSocket
        store.dispatch(WebSocketAction.Connect)

        // State should change to Connected with CONNECTING status
        val connectingState = store.currentState
        assertIs<WebSocketState.Connected>(connectingState)
        assertEquals(WebSocketState.ConnectionStatus.CONNECTING, connectingState.connectionStatus)

        // Simulate connection success
        mockWebSocket.simulateConnectionEstablished()

        // State should change to Connected with CONNECTED status
        val connectedState = store.currentState
        assertIs<WebSocketState.Connected>(connectedState)
        assertEquals(WebSocketState.ConnectionStatus.CONNECTED, connectedState.connectionStatus)

        // Simulate receiving a message
        val testMessage = WebSocketMessage("test-id", "Test message", 1234567890L) // Using fixed timestamp for test
        mockWebSocket.simulateMessageReceived(testMessage)

        // State should update with the latest message
        val stateWithMessage = store.currentState
        assertIs<WebSocketState.Connected>(stateWithMessage)
        assertEquals(testMessage, stateWithMessage.latestMessage)
        assertEquals(WebSocketState.ConnectionStatus.CONNECTED, stateWithMessage.connectionStatus)

        // Disconnect
        store.dispatch(WebSocketAction.Disconnect)

        // State should first change to DISCONNECTING
        val disconnectingState = store.currentState
        assertIs<WebSocketState.Connected>(disconnectingState)
        assertEquals(WebSocketState.ConnectionStatus.DISCONNECTING, disconnectingState.connectionStatus)

        // After the disconnect event is processed, state should return to Disconnected
        mockWebSocket.simulateConnectionClosed()

        // State should return to Disconnected
        assertIs<WebSocketState.Disconnected>(store.currentState)
    }

    @Test
    fun webSocket_shouldHandleConnectionFailures() = runTest(testDispatcher) {
        // Mock implementation of WebSocket service
        val mockWebSocket = MockWebSocketService()

        // Create store with disconnected state
        val store = createTestStore(mockWebSocket)

        // Connect to WebSocket
        store.dispatch(WebSocketAction.Connect)

        // Verify we're in CONNECTING status
        val connectingState = store.currentState
        assertIs<WebSocketState.Connected>(connectingState)
        assertEquals(WebSocketState.ConnectionStatus.CONNECTING, connectingState.connectionStatus)

        // Simulate connection failure
        val error = Exception("Connection refused")
        mockWebSocket.simulateConnectionError(error)

        // State should change to Error
        val errorState = store.currentState
        assertIs<WebSocketState.Error>(errorState)
        assertEquals(error, errorState.error)
    }

    @Test
    fun webSocket_shouldHandleDisconnectDuringConnecting() = runTest(testDispatcher) {
        // Mock implementation of WebSocket service
        val mockWebSocket = MockWebSocketService()

        // Create store with disconnected state
        val store = createTestStore(mockWebSocket)

        // Connect to WebSocket
        store.dispatch(WebSocketAction.Connect)

        // Verify we're in CONNECTING status
        val connectingState = store.currentState
        assertIs<WebSocketState.Connected>(connectingState)
        assertEquals(WebSocketState.ConnectionStatus.CONNECTING, connectingState.connectionStatus)

        // Disconnect while still connecting
        store.dispatch(WebSocketAction.Disconnect)

        // State should first change to DISCONNECTING
        val disconnectingState = store.currentState
        assertIs<WebSocketState.Connected>(disconnectingState)
        assertEquals(WebSocketState.ConnectionStatus.DISCONNECTING, disconnectingState.connectionStatus)

        // After the disconnect event is processed, state should return to Disconnected
        mockWebSocket.simulateConnectionClosed()

        assertIs<WebSocketState.Disconnected>(store.currentState)
    }
}

// Domain models
private data class WebSocketMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
)

// WebSocket service interfaces
private interface WebSocketService {
    fun connect()
    fun disconnect()
    val connectionEvents: Flow<ConnectionEvent>
    val messages: Flow<WebSocketMessage>

    sealed interface ConnectionEvent {
        data object Connected : ConnectionEvent
        data object Disconnected : ConnectionEvent
        data class Error(val throwable: Throwable) : ConnectionEvent
    }
}

// Mock implementation for testing
private class MockWebSocketService : WebSocketService {
    private val connectionChannel = Channel<WebSocketService.ConnectionEvent>(Channel.BUFFERED)
    private val messageChannel = Channel<WebSocketMessage>(Channel.BUFFERED)

    override val connectionEvents: Flow<WebSocketService.ConnectionEvent> = connectionChannel.receiveAsFlow()
    override val messages: Flow<WebSocketMessage> = messageChannel.receiveAsFlow()

    override fun connect() {
        // Connection logic happens in simulation methods for testing
    }

    override fun disconnect() {
        // In real implementation, this would trigger the actual disconnect
        // The event itself is sent by simulateConnectionClosed() for testing
    }

    fun simulateConnectionEstablished() {
        connectionChannel.trySend(WebSocketService.ConnectionEvent.Connected)
    }

    fun simulateConnectionError(error: Throwable) {
        connectionChannel.trySend(WebSocketService.ConnectionEvent.Error(error))
    }

    fun simulateConnectionClosed() {
        connectionChannel.trySend(WebSocketService.ConnectionEvent.Disconnected)
    }

    fun simulateMessageReceived(message: WebSocketMessage) {
        messageChannel.trySend(message)
    }
}

// Tart State definitions
private sealed interface WebSocketState : State {
    data object Disconnected : WebSocketState

    data class Connected(
        val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
        val latestMessage: WebSocketMessage? = null,
    ) : WebSocketState

    data class Error(val error: Throwable) : WebSocketState

    enum class ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTING
    }
}

// Tart Action definitions
private sealed interface WebSocketAction : Action {
    data object Connect : WebSocketAction
    data object Disconnect : WebSocketAction
}

private fun createTestStore(
    webSocketService: WebSocketService,
): Store<WebSocketState, WebSocketAction, Nothing> {
    return Store {
        initialState(WebSocketState.Disconnected)
        coroutineContext(Dispatchers.Unconfined)

        state<WebSocketState.Disconnected> {
            action<WebSocketAction.Connect> {
                nextState(WebSocketState.Connected(connectionStatus = WebSocketState.ConnectionStatus.CONNECTING))
            }
        }

        state<WebSocketState.Connected> {
            enter {
                // Skip if not in CONNECTING status (early return pattern)
                if (state.connectionStatus != WebSocketState.ConnectionStatus.CONNECTING) {
                    return@enter
                }

                // Initiate connection
                webSocketService.connect()

                // Monitor both connection events and messages in a single state
                launch {
                    webSocketService.connectionEvents.collect { event ->
                        when (event) {
                            is WebSocketService.ConnectionEvent.Connected -> {
                                transaction {
                                    nextState(state.copy(connectionStatus = WebSocketState.ConnectionStatus.CONNECTED))
                                }
                            }

                            is WebSocketService.ConnectionEvent.Error -> {
                                transaction {
                                    nextState(WebSocketState.Error(event.throwable))
                                }
                            }

                            is WebSocketService.ConnectionEvent.Disconnected -> {
                                transaction {
                                    nextState(WebSocketState.Disconnected)
                                }
                            }
                        }
                    }
                }

                launch {
                    webSocketService.messages.collect { message ->
                        transaction {
                            // Only update messages if in CONNECTED status
                            if (state.connectionStatus == WebSocketState.ConnectionStatus.CONNECTED) {
                                nextState(state.copy(latestMessage = message))
                            }
                            // Otherwise ignore messages that arrive before fully connected
                            // or after disconnecting has started
                        }
                    }
                }
            }

            action<WebSocketAction.Disconnect> {
                // First update state to disconnecting
                nextState(state.copy(connectionStatus = WebSocketState.ConnectionStatus.DISCONNECTING))

                // Then initiate disconnect
                webSocketService.disconnect()
                // Actual state change to Disconnected will happen through the event flow
            }
        }

        state<WebSocketState.Error> {
            action<WebSocketAction.Connect> {
                nextState(WebSocketState.Connected(connectionStatus = WebSocketState.ConnectionStatus.CONNECTING))
            }
        }
    }
}
