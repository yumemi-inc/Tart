package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 * Tests the login flow state transitions in a Tart Store.
 */

// State Transition Diagram:
//
// ┌─────────────────────────────────────────────┐
// │                                             │
// │  Initial ───────► Loading ───────► Success  │
// │     ▲              │                  │     │
// │     │              │                  │     │
// │     │              ▼                  │     │
// │     └────-────── Error ◄──────────────┘     │
// │                                             │
// └─────────────────────────────────────────────┘
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

        // Should transition to Loading state
        assertIs<LoginState.Loading>(store.currentState)
        val loadingState = store.currentState as LoginState.Loading
        assertEquals("user123", loadingState.username)
        assertEquals("password123", loadingState.password)

        // Process the login request in the repository
        store.dispatch(LoginAction.ProcessLogin)

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

        // Start login process
        store.dispatch(LoginAction.Login("user123", "wrong_password"))

        // Should transition to Loading state
        assertIs<LoginState.Loading>(store.currentState)

        // Process the login request in the repository
        store.dispatch(LoginAction.ProcessLogin)

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

        // First login attempt (will fail)
        store.dispatch(LoginAction.Login("user123", "wrong_password"))
        assertIs<LoginState.Loading>(store.currentState)

        store.dispatch(LoginAction.ProcessLogin)
        assertIs<LoginState.Error>(store.currentState)

        // Change repository to succeed on next attempt
        repository.shouldSucceed = true

        // Send retry action
        store.dispatch(LoginAction.RetryFromError)
        assertIs<LoginState.Initial>(store.currentState)

        // Try logging in again
        store.dispatch(LoginAction.Login("user123", "correct_password"))
        assertIs<LoginState.Loading>(store.currentState)

        store.dispatch(LoginAction.ProcessLogin)
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
    data object ProcessLogin : LoginAction
    data object RetryFromError : LoginAction
}

// Event definitions
private sealed interface LoginEvent : Event {
    data class NavigateToHome(val username: String) : LoginEvent
    data class ShowError(val message: String) : LoginEvent
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
    return Store(
        initialState = LoginState.Initial,
        coroutineContext = Dispatchers.Unconfined,
        onDispatch = { state, action ->

            suspend fun handleInitialState(state: LoginState.Initial): LoginState = when (action) {
                is LoginAction.Login -> {
                    // Validation check
                    if (action.username.isNotBlank() && action.password.isNotBlank()) {
                        LoginState.Loading(action.username, action.password)
                    } else {
                        emit(LoginEvent.ShowError("Username and password must not be empty"))
                        LoginState.Error("Username and password must not be empty")
                    }
                }

                else -> state
            }

            suspend fun handleLoadingState(state: LoginState.Loading): LoginState = when (action) {
                is LoginAction.ProcessLogin -> {
                    // Execute login process in repository
                    val success = repository.login(state.username, state.password)
                    if (success) {
                        emit(LoginEvent.NavigateToHome(state.username))
                        LoginState.Success(state.username)
                    } else {
                        emit(LoginEvent.ShowError("Authentication failed"))
                        LoginState.Error("Authentication failed")
                    }
                }

                else -> state
            }

            fun handleErrorState(state: LoginState.Error): LoginState = when (action) {
                is LoginAction.RetryFromError -> LoginState.Initial
                else -> state
            }

            when (state) {
                is LoginState.Initial -> handleInitialState(state)
                is LoginState.Loading -> handleLoadingState(state)
                is LoginState.Success -> state
                is LoginState.Error -> handleErrorState(state)
            }
        },
        onError = { state, error ->
            emit(LoginEvent.ShowError(error.message ?: "Unknown error"))
            when (state) {
                is LoginState.Loading -> LoginState.Error(error.message ?: "Unknown error")
                else -> state
            }
        },
    )
}
