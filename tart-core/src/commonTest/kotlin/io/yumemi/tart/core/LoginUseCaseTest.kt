package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
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
// ┌────────────────────────────────────────┐
// │                                        │
// │  Initial ────► Loading ─────► Success  │
// │    ▲  │             │                  │
// │    │  └──────────┐  │                  │
// │    │             │  │                  │
// │    │             ▼  ▼                  │
// │    └─────────── Error ◄──── Any State  │
// │                                        │
// └────────────────────────────────────────┘
//
// Initial: Username/password input screen
// Loading: Login processing
// Success: Login successful
// Error: Login failed (validation or authentication error)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun loginFlow_successCase() = runTest(testDispatcher) {
        // Create Store with a repository that will succeed
        val repository = MockLoginRepository(shouldSucceed = true)
        val store = createLoginStore(repository)

        // Verify initial state
        assertIs<LoginState.Initial>(store.currentState)

        // Start login process
        store.dispatch(LoginAction.Login("user123", "password123"))

        // Login successful, should be in Success state
        assertIs<LoginState.Success>(store.currentState)
        val successState = store.currentState as LoginState.Success
        assertEquals("user123", successState.username)
    }

    @Test
    fun loginFlow_failureCase() = runTest(testDispatcher) {
        // Create Store with a repository that will fail
        val repository = MockLoginRepository(shouldSucceed = false)
        val store = createLoginStore(repository)

        // Verify initial state
        assertIs<LoginState.Initial>(store.currentState)

        // Start login process
        store.dispatch(LoginAction.Login("user123", "wrong_password"))

        // Login fails, should be in Error state
        assertIs<LoginState.Error>(store.currentState)
        val errorState = store.currentState as LoginState.Error
        assertEquals("Authentication failed", errorState.message)
    }

    @Test
    fun errorState_canRetryLogin() = runTest(testDispatcher) {
        // Create Store with a repository that will initially fail
        val repository = MockLoginRepository(shouldSucceed = false)
        val store = createLoginStore(repository)

        // Verify initial state
        assertIs<LoginState.Initial>(store.currentState)

        // First login attempt (will fail)
        store.dispatch(LoginAction.Login("user123", "wrong_password"))

        // Login fails, should be in Error state
        assertIs<LoginState.Error>(store.currentState)

        // Change repository to succeed on next attempt
        repository.shouldSucceed = true

        // Send retry action
        store.dispatch(LoginAction.RetryFromError)
        assertIs<LoginState.Initial>(store.currentState)

        // Try logging in again
        store.dispatch(LoginAction.Login("user123", "correct_password"))

        // Login process is automatically handled by the enter block
        assertIs<LoginState.Success>(store.currentState)
    }
}

// State definitions
private sealed interface LoginState : State {
    data object Initial : LoginState
    data class Loading(val username: String, val password: String) : LoginState
    data class Success(val username: String) : LoginState
    data class Error(val message: String) : LoginState
}

// Action definitions
private sealed interface LoginAction : Action {
    data class Login(val username: String, val password: String) : LoginAction
    data object RetryFromError : LoginAction
}

// Event definitions
private sealed interface LoginEvent : Event {
    data class NavigateToHome(val username: String) : LoginEvent
}

// Login repository interface
private interface LoginRepository {
    suspend fun login(username: String, password: String): Boolean
}

// Mock repository implementation
private class MockLoginRepository(var shouldSucceed: Boolean) : LoginRepository {
    override suspend fun login(username: String, password: String): Boolean {
        return shouldSucceed
    }
}

// Create a Store using the standard Store factory function
private fun createLoginStore(
    repository: LoginRepository,
): Store<LoginState, LoginAction, LoginEvent> {
    return Store(LoginState.Initial) {
        coroutineContext(Dispatchers.Unconfined)
        // Processing for Initial state
        state<LoginState.Initial> {
            action<LoginAction.Login> {
                // Validate input values
                val isValidInput = action.username.isNotBlank() && action.password.isNotBlank()

                nextStateBy {
                    if (isValidInput) {
                        // For valid input, transition to loading state
                        LoginState.Loading(action.username, action.password)
                    } else {
                        // For invalid input, transition to error state
                        LoginState.Error("Username and password must not be empty")
                    }
                }
            }
        }
        // Processing for Loading state
        state<LoginState.Loading> {
            enter {
                // Execute login process in repository
                val loginSuccessful = repository.login(state.username, state.password)

                if (loginSuccessful) {
                    // Process for successful login
                    event(LoginEvent.NavigateToHome(state.username))

                    nextStateBy {
                        // Transition to success state
                        LoginState.Success(state.username)
                    }
                } else {
                    // Process for failed login
                    nextStateBy {
                        // Transition to error state
                        LoginState.Error("Authentication failed")
                    }
                }
            }
        }
        // Processing for Error state
        state<LoginState.Error> {
            action<LoginAction.RetryFromError> {
                nextState(LoginState.Initial)
            }
        }
        // Error handling for all states
        state<LoginState> {
            error<Exception> {
                nextState(LoginState.Error(error.message ?: "Unknown error"))
            }
        }
    }
}
