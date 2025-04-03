package io.yumemi.tart.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * This test case was generated with assistance from Anthropic's Claude AI.
 */

// State Transition Diagram:
//
// ┌───────────────────────────────────────────────┐
// │                                               │
// │  Idle ────► Loading ────► Loaded ───────┐     │
// │   ▲                          ▲          │     │
// │   │                          │          │     │
// │   │                          │          ▼     │
// │  Error ◄──── Any State       │       Editing  │
// │                              │          │     │
// │                              └──────────┘     │
// │                                               │
// └───────────────────────────────────────────────┘
//
// Idle: Initial state, no todos loaded yet
// Loading: Fetching todos
// Loaded: Todos successfully loaded, showing list
// Error: Error occurred during loading or operation
// Editing: Editing a todo item

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun todoApp_initialState() = runTest(testDispatcher) {
        val store = createTodoStore()
        assertIs<TodoState.Idle>(store.currentState)
    }

    @Test
    fun todoApp_loadTodosSuccess() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val store = createTodoStore(repository)

        store.dispatch(TodoAction.LoadTodos)

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        assertEquals(3, loadedState.todos.size)
    }

    @Test
    fun todoApp_loadTodosFailure() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = false)
        val store = createTodoStore(repository)

        store.dispatch(TodoAction.LoadTodos)

        assertIs<TodoState.Error>(store.currentState)
        val errorState = store.currentState as TodoState.Error
        assertEquals("Failed to load todos", errorState.message)
    }

    @Test
    fun todoApp_addTodo() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val store = createTodoStore(repository, TodoState.Loaded(repository.initialTodos))

        val initialCount = (store.currentState as TodoState.Loaded).todos.size
        store.dispatch(TodoAction.AddTodo("Buy groceries"))

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        assertEquals(initialCount + 1, loadedState.todos.size)
        assertEquals("Buy groceries", loadedState.todos.last().title)
        assertFalse(loadedState.todos.last().completed)
    }

    @Test
    fun todoApp_toggleTodoCompletion() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val store = createTodoStore(repository, TodoState.Loaded(repository.initialTodos))

        val todoToToggle = (store.currentState as TodoState.Loaded).todos.first()
        val initialCompletionStatus = todoToToggle.completed

        store.dispatch(TodoAction.ToggleCompletion(todoToToggle.id))

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        val updatedTodo = loadedState.todos.first { it.id == todoToToggle.id }
        assertEquals(!initialCompletionStatus, updatedTodo.completed)
    }

    @Test
    fun todoApp_deleteTodo() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val store = createTodoStore(repository, TodoState.Loaded(repository.initialTodos))

        val todoToDelete = (store.currentState as TodoState.Loaded).todos.first()
        val initialCount = (store.currentState as TodoState.Loaded).todos.size

        store.dispatch(TodoAction.DeleteTodo(todoToDelete.id))

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        assertEquals(initialCount - 1, loadedState.todos.size)
        assertFalse(loadedState.todos.any { it.id == todoToDelete.id })
    }

    @Test
    fun todoApp_startEditingTodo() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val store = createTodoStore(repository, TodoState.Loaded(repository.initialTodos))

        val todoToEdit = (store.currentState as TodoState.Loaded).todos.first()

        store.dispatch(TodoAction.StartEditing(todoToEdit.id))

        assertIs<TodoState.Editing>(store.currentState)
        val editingState = store.currentState as TodoState.Editing
        assertEquals(todoToEdit.id, editingState.editingTodo.id)
        assertEquals(todoToEdit.title, editingState.editingTodo.title)
    }

    @Test
    fun todoApp_saveEditedTodo() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val todoToEdit = repository.initialTodos.first()
        val editedTodo = todoToEdit.copy(title = "Updated task")
        val store = createTodoStore(repository, TodoState.Editing(editedTodo, repository.initialTodos))

        store.dispatch(TodoAction.SaveEdit)

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        val updatedTodo = loadedState.todos.first { it.id == todoToEdit.id }
        assertEquals("Updated task", updatedTodo.title)
    }

    @Test
    fun todoApp_cancelEditing() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = true)
        val todoToEdit = repository.initialTodos.first()
        val editedTodo = todoToEdit.copy(title = "This edit will be discarded")
        val store = createTodoStore(repository, TodoState.Editing(editedTodo, repository.initialTodos))

        store.dispatch(TodoAction.CancelEdit)

        assertIs<TodoState.Loaded>(store.currentState)
        val loadedState = store.currentState as TodoState.Loaded
        val unchangedTodo = loadedState.todos.first { it.id == todoToEdit.id }
        assertEquals(todoToEdit.title, unchangedTodo.title)
    }

    @Test
    fun todoApp_errorRetry() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = false)
        val store = createTodoStore(repository, TodoState.Error("Test error"))

        // Change repository to succeed on retry
        repository.shouldSucceed = true

        store.dispatch(TodoAction.RetryFromError)

        // Should be back to idle, ready to load
        assertIs<TodoState.Idle>(store.currentState)

        // Try loading again
        store.dispatch(TodoAction.LoadTodos)

        // Should succeed this time
        assertIs<TodoState.Loaded>(store.currentState)
    }

    @Test
    fun todoApp_errorHandling() = runTest(testDispatcher) {
        val repository = MockTodoRepository(shouldSucceed = false)
        val store = createTodoStore(repository, TodoState.Loaded(repository.initialTodos))

        // Force an error by trying to add a todo when repository is set to fail
        store.dispatch(TodoAction.AddTodo("This will fail"))

        assertIs<TodoState.Error>(store.currentState)
        val errorState = store.currentState as TodoState.Error
        assertEquals("Failed to add todo", errorState.message)
    }
}

// Todo item data class
private data class Todo(
    val id: String,
    val title: String,
    val completed: Boolean = false,
)

// State definitions
private sealed interface TodoState : State {
    data object Idle : TodoState
    data object Loading : TodoState
    data class Loaded(val todos: List<Todo>) : TodoState
    data class Editing(val editingTodo: Todo, val allTodos: List<Todo>) : TodoState
    data class Error(val message: String) : TodoState
}

// Action definitions
private sealed interface TodoAction : Action {
    data object LoadTodos : TodoAction
    data class AddTodo(val title: String) : TodoAction
    data class ToggleCompletion(val todoId: String) : TodoAction
    data class DeleteTodo(val todoId: String) : TodoAction
    data class StartEditing(val todoId: String) : TodoAction
    data class UpdateEditingTitle(val newTitle: String) : TodoAction
    data object SaveEdit : TodoAction
    data object CancelEdit : TodoAction
    data object RetryFromError : TodoAction
}

// Simple ID counter for tests
private var idCounter = 0

// Helper function to generate IDs in a platform-independent way
private fun generateId(): String {
    val random = Random.nextInt(10000, 99999)
    idCounter++
    return "${abs(random)}_${idCounter}"
}

// Repository interface
private interface TodoRepository {
    suspend fun loadTodos(): List<Todo>
    suspend fun addTodo(todo: Todo): Todo
    suspend fun updateTodo(todo: Todo): Todo
    suspend fun deleteTodo(todoId: String): Boolean
}

// Mock repository implementation
private class MockTodoRepository(var shouldSucceed: Boolean) : TodoRepository {
    val initialTodos = listOf(
        Todo("1", "Complete project", false),
        Todo("2", "Write tests", true),
        Todo("3", "Review code", false),
    )

    private val _todos = initialTodos.toMutableList()

    override suspend fun loadTodos(): List<Todo> {
        if (!shouldSucceed) throw Exception("Failed to load todos")
        return _todos.toList()
    }

    override suspend fun addTodo(todo: Todo): Todo {
        if (!shouldSucceed) throw Exception("Failed to add todo")
        _todos.add(todo)
        return todo
    }

    override suspend fun updateTodo(todo: Todo): Todo {
        if (!shouldSucceed) throw Exception("Failed to update todo")
        val index = _todos.indexOfFirst { it.id == todo.id }
        if (index >= 0) {
            _todos[index] = todo
        }
        return todo
    }

    override suspend fun deleteTodo(todoId: String): Boolean {
        if (!shouldSucceed) throw Exception("Failed to delete todo")
        val initialSize = _todos.size
        _todos.removeAll { it.id == todoId }
        return _todos.size < initialSize
    }
}

// Store factory
private fun createTodoStore(
    repository: TodoRepository = MockTodoRepository(true),
    initialState: TodoState = TodoState.Idle,
): Store<TodoState, TodoAction, Nothing> {
    return Store(initialState) {
        coroutineContext(Dispatchers.Unconfined)

        // Idle state handling
        state<TodoState.Idle> {
            action<TodoAction.LoadTodos> {
                TodoState.Loading
            }
        }

        // Loading state handling
        state<TodoState.Loading> {
            enter {
                val todos = repository.loadTodos()
                TodoState.Loaded(todos)
            }
        }

        // Loaded state handling
        state<TodoState.Loaded> {
            action<TodoAction.AddTodo> {
                val newTodo = Todo(
                    id = generateId(),
                    title = action.title,
                    completed = false,
                )
                val savedTodo = repository.addTodo(newTodo)
                state.copy(todos = state.todos + savedTodo)
            }

            action<TodoAction.ToggleCompletion> {
                val todoToUpdate = state.todos.first { it.id == action.todoId }
                val updatedTodo = todoToUpdate.copy(completed = !todoToUpdate.completed)
                val savedTodo = repository.updateTodo(updatedTodo)
                state.copy(
                    todos = state.todos.map {
                        if (it.id == action.todoId) savedTodo else it
                    },
                )
            }

            action<TodoAction.DeleteTodo> {
                val success = repository.deleteTodo(action.todoId)
                if (success) {
                    state.copy(todos = state.todos.filter { it.id != action.todoId })
                } else {
                    state
                }
            }

            action<TodoAction.StartEditing> {
                val todoToEdit = state.todos.first { it.id == action.todoId }
                TodoState.Editing(todoToEdit, state.todos)
            }
        }

        // Editing state handling
        state<TodoState.Editing> {
            action<TodoAction.UpdateEditingTitle> {
                state.copy(editingTodo = state.editingTodo.copy(title = action.newTitle))
            }

            action<TodoAction.SaveEdit> {
                val updatedTodo = repository.updateTodo(state.editingTodo)
                TodoState.Loaded(
                    state.allTodos.map {
                        if (it.id == updatedTodo.id) updatedTodo else it
                    },
                )
            }

            action<TodoAction.CancelEdit> {
                TodoState.Loaded(state.allTodos)
            }
        }

        // Error state handling
        state<TodoState.Error> {
            action<TodoAction.RetryFromError> {
                TodoState.Idle
            }
        }

        // Global error handling
        state<TodoState> {
            error {
                TodoState.Error(error.message ?: "Unknown error")
            }
        }
    }
}
