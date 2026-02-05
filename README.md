<img src="doc/logo.png" width="350">
<details>
<summary>Logo Usage (CC0 1.0)</summary>

The Tart logo (doc/logo.png, doc/logo-full.png) is licensed under CC0 1.0 Universal.
You may use it freely for any purpose without attribution.
See doc/LOGO_LICENSE for details.
</details>

![Maven Central](https://img.shields.io/maven-central/v/io.yumemi.tart/tart-core)
![License](https://img.shields.io/github/license/yumemi-inc/Tart)
[![Java CI with Gradle](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml/badge.svg)](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml)

> [!IMPORTANT]
> We are considering moving this repository to a different organization.
> As part of that move, the library group name may change.

Tart is a state management framework for Kotlin Multiplatform.

- Data flow is one-way, making it easy to understand.
- Since the state remains unchanged during processing, there is no need to worry about side effects.
- Code becomes declarative.
- Writing test code is straightforward and easy.
- Works on multiple platforms.
  - Enables code sharing and consistent logic implementation across platforms.

The architecture is inspired by [Flux](https://facebookarchive.github.io/flux/) and is as follows:

<div align="center">
  <img src="doc/architecture.png" width=60% />
</div>
</br>

## Installation

```kt
implementation("io.yumemi.tart:tart-core:<latest-release>")
```

## Usage

### Basic

Take a simple counter application as an example.
First, prepare classes for *State* and *Action*.

```kt
data class CounterState(val count: Int) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
}
```

Create a *Store* instance using the `Store{}` DSL with an initial *State*.

```kt
val store: Store<CounterState, CounterAction, Nothing> = Store(CounterState(count = 0)) {}

// or, use initialState specification
val store: Store<CounterState, CounterAction, Nothing> = Store {

    initialState(CounterState(count = 0))
}
```

Define how the *State* is changed by *Action* by using the `state{}` and `action{}` blocks.
Specify the resulting *State* using the `nextState()` specification.
If no `nextState()` is specified, the current state remains unchanged.
For complex state updates with conditional logic, you can use `nextStateBy{}` with a block that computes and returns the new state.

```kt
val store: Store<CounterState, CounterAction, Nothing> = Store(CounterState(count = 0)) {

    state<CounterState> {

        action<CounterAction.Increment> {
            nextState(state.copy(count = state.count + 1))
        }

        action<CounterAction.Decrement> {
            if (0 < state.count) {
                nextState(state.copy(count = state.count - 1))
            } else {
                // do not change State
            }
        }
    }
}
```

The *Store* preparation is now complete.
Keep the store instance in the ViewModel etc.

Issue an *Action* from the UI using the Store's `dispatch()` method.

```kt
// example in Compose
Button(
    onClick = { store.dispatch(CounterAction.Increment) },
) {
    Text(text = "increment")
}
```

The new *State* will be reflected in the Store's `.state` (StateFlow) property, so draw it to the UI.

### Notify event to UI

Prepare classes for *Event*, and specify it as the third type parameter of *Store*.

```kt
sealed interface CounterEvent : Event {
    data class ShowToast(val message: String) : CounterEvent
    data object NavigateToNextScreen : CounterEvent
}
```

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(CounterState(count = 0)) {
    // ...
}
```

In a `action{}` block, specify that Event using the `event()` specification.

```kt
action<CounterAction.Decrement> {
    if (0 < state.count) {
        nextState(state.copy(count = state.count - 1))
    } else {
        event(CounterEvent.ShowToast("Can not Decrement.")) // issue event
    }
}
```

Subscribe to the Store's `.event` (Flow) property on the UI, and process it.

### Access to Repository, UseCase, etc.

Keep Repository, UseCase, etc. accessible from your store creation scope and use them in the `action{}` block.

```kt
fun CounterStore(
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store(CounterState(count = 0)) {

    state<CounterState> {

        action<CounterAction.Load> {
            val count = counterRepository.get() // load
            nextState(state.copy(count = count))
        }

        action<CounterAction.Increment> {
            val count = state.count + 1
            counterRepository.set(count) // save
            nextState(state.copy(count = count))
        }

        // ...
```

<details>
<summary>TIPS: Define functions as needed</summary>

Processing other than changing the *State* may be defined as functions, as they tend to become complex and lengthy.

```kt
fun CounterStore(
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store(CounterState(count = 0)) {

    // define as a function
    suspend fun loadCount(): Int {
        return counterRepository.get()
    }

    state<CounterState> {

        action<CounterAction.Load> {
            nextState(state.copy(count = loadCount())) // call the function
        }

        // ...
```

You may also define them as extension functions of *State* or *Action*.
</details>

### Multiple states and transitions

In the previous examples, the *State* was single.
However, if there are multiple *States*, for example a UI during data loading, prepare multiple *States*.

```kt
sealed interface CounterState : State {
    data object Loading : CounterState 
    data class Main(val count: Int) : CounterState
}
```

```kt
fun CounterStore(
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store(CounterState.Loading) {

    state<CounterState.Loading> { // for Loading state
        action<CounterAction.Load> {
            val count = counterRepository.get()
            nextState(CounterState.Main(count = count)) // transition to Main state
        }
    }

    state<CounterState.Main> { // for Main state
        action<CounterAction.Increment> {
            // ...
```

In this example, the `CounterAction.Load` action needs to be issued from the UI when the application starts.
Otherwise, if you want to do something at the start of the *State*, use the `enter{}` block (similarly, you can use the `exit{}` block if necessary).

```kt
fun CounterStore(
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store(CounterState.Loading) {

    state<CounterState.Loading> {
        enter {
            val count = counterRepository.get()
            nextState(CounterState.Main(count = count)) // transition to Main state
        }
    }

    state<CounterState.Main> {
        action<CounterAction.Increment> {
            // ...
```

The state diagram is as follows:

<div align="center">
  <img src="https://raw.githubusercontent.com/yumemi-inc/Tart/main/doc/diagram.png" width=25% />
</div>
</br>

This framework's architecture can be easily visualized using state diagrams.
It would be a good idea to document it and share it with your development team.

### Error handling

If you prepare a *State* for error display and handle the error in the `enter{}` block, it will be as follows:

```kt
sealed interface CounterState : State {
    // ...
    data class Error(val error: Exception) : CounterState
}
```

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    state<CounterState.Loading> {
        enter {
            try {
                val count = counterRepository.get()
                nextState(CounterState.Main(count = count))
            } catch (e: Exception) {
                nextState(CounterState.Error(error = e))
            }
        }
    }
}
```

This is fine, but you can also handle errors using the `error{}` block.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    state<CounterState.Loading> {

        enter {
            // no error handling code
            val count = counterRepository.get()
            nextState(CounterState.Main(count = count))
        }

        // more specific exceptions should be placed first
        error<IllegalStateException> {
            // ...
            nextState(CounterState.Error(error = error))
        }

        // more general exception handlers should come last
        // you can also catch just 'Exception' and branch based on the type of the error property inside the block
        error<Exception> { // catches any remaining exceptions
            // ...
            nextState(CounterState.Error(error = error))
        }
    }
}
```

Errors can be caught not only in the `enter{}` block but also in the `action{}` and `exit{}` blocks.
In other words, your business logic errors can be handled in the `error{}` block.

On the other hand, uncaught errors in the entire Store (such as system errors) can be handled with the `exceptionHandler()` specification:

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    exceptionHandler(...)
}
```

### Collecting Flows

You can use the `launch{}` specification in the `enter{}` block to collect flows and dispatch *Actions*.
This is useful for connecting external data streams to your *Store*:

```kt
state<MyState.Active> {
    enter {
        // launch a coroutine that lives as long as this state is active
        launch {
            // collect from an external data source
            dataRepository.observeData().collect { newData ->
                // update state with the new data in a transaction
                transaction {
                    nextState(state.copy(data = newData))
                }
            }
        }
    }
}
```

This pattern allows your *Store* to automatically react to external data changes, such as database updates, user preferences changes, or network events.
The flow collection will be automatically cancelled when the *State* changes to a different *State*, making it easy to manage resources and subscriptions.

### Specifying coroutineContext

The Store operates using Coroutines, and the default CoroutineContext is `EmptyCoroutineContext + Dispatchers.Default`.
Specify it when you want to match the Store's Coroutines lifecycle with another context or change the thread on which it operates.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    coroutineContext(...)
}
```

If you do not specify a context that is automatically disposed like ViewModel's `viewModelScope` or Compose's `rememberCoroutineScope()`, call Store's `.dispose()` method explicitly when the *Store* is no longer needed.
Then, processing of all Coroutines will stop.

#### Specifying CoroutineDispatchers

You can specify the execution thread (CoroutineDispatchers) in `enter{}`, `exit{}`, `action{}`, `error{}`, and `launch{}` blocks, allowing you to locally control which thread each specific operation runs on.

```kt
enter(Dispatchers.Default) {
    // work on CPU thread..

    launch(Dispatchers.IO) {
        // This code runs on IO thread
        val updates = dataRepository.observeUpdates()
        updates.collect { newData ->
            // ...
        }
    }
}
```

Alternatively, you can use Coroutines' `withContext()`.

```kt
enter {
    withContext(Dispatchers.Default) {
        // work on CPU thread..

        withContext(Dispatchers.IO) {
            // This code runs on IO thread
            launch {
                val updates = dataRepository.observeUpdates()
                updates.collect { newData ->
                    // ...
                }
            }
        }
    }
}
```

### State Persistence

You can prepare a [StateSaver](tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StateSaver.kt) to automatically handle *State* persistence:

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    stateSaver(...)
}
```

### For iOS

Coroutines like Store's `.state` (StateFlow) property and `.event` (Flow) property cannot be used on iOS, so use the `.collectState()` and `.collectEvent()` methods.
If the *State* or *Event* changes, you will be notified through these callbacks.

## Compose

<details>
<summary>contents</summary>

You can use Store's `.state` (StateFlow), `.event` (Flow), and `.dispatch()` directly, but we provide a mechanism for Compose.

```kt
implementation("io.yumemi.tart:tart-compose:<latest-release>")
```

Create an instance of the `ViewStore` from a *Store* using the `rememberViewStore()` function.
For example, if you have a *Store* in ViewModels, it would look like this:

```kt
fun CounterStore(
    coroutineContext: CoroutineContext,
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    coroutineContext(coroutineContext)
}
```

```kt
@HiltViewModel
class CounterViewModel @Inject constructor(
    counterRepository: CounterRepository,
) : ViewModel() {

    val store = CounterStore(
        coroutineContext = viewModelScope.coroutineContext,
        counterRepository = counterRepository,
    )
}
```

```kt
@Composable
fun CounterScreen(
    // create an instance of ViewStore
    viewStore: ViewStore<CounterState, CounterAction, CounterEvent> = rememberViewStore {
        hiltViewModel<CounterViewModel>().store
    },
) {
    // ...

    // pass the ViewStore instance to lower components if necessary
    YourComposable(
        viewStore = viewStore,
    )
}
```

Alternatively, you can use the `Store{}` DSL directly in the ViewModel as follows, but note that in this case you need to write tests for `CounterViewModel`.

```kt
@HiltViewModel
class MainViewModel @Inject constructor(
    counterRepository: CounterRepository,
) : ViewModel() {

    val store: Store<CounterState, CounterAction, CounterEvent> = Store {
        // ...

        coroutineContext(viewModelScope.coroutineContext)
    }
}
```

You can create a `ViewStore` instance without using ViewModel as shown below:

```kt
fun CounterStore(
    coroutineContext: CoroutineContext,
    stateSaver: StateSaver<CounterState>,
    counterRepository: CounterRepository,
): Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    coroutineContext(coroutineContext)
    stateSaver(stateSaver)
}
```

```kt
@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, CounterAction, CounterEvent> = rememberViewStore {
        CounterStore(
            coroutineContext = rememberCoroutineScope().coroutineContext, // or, specify the autoDispose option in rememberViewStore {}
            stateSaver = rememberStateSaver(), // state persistence during screen rotation, etc.
            counterRepository = CounterRepositoryImpl(),
        )
    },
) {
    // ...
}
```

If you inject instances such as Repository using a DI library, it is useful to create a class like the following.

```kt
class CounterStoreContainer(
    private val counterRepository: CounterRepository,
) {
    fun build(coroutineContext: CoroutineContext, stateSaver: StateSaver<CounterState>): Store<CounterState, CounterAction, CounterEvent> = Store {
        // ...

        coroutineContext(coroutineContext)
        stateSaver(stateSaver)
    }
}
```

### Rendering using State

If the *State* is single, just use ViewStore's `.state` property.

```kt
Text(
    text = viewStore.state.count.toString(),
)
```

If there are multiple *States*, use `.render()` method with the target *State*.

```kt
viewStore.render<CounterState.Main> {
    Text(
        text = state.count.toString(),
    )
}
```

When drawing the UI, if it does not match the target *State*, the `.render()` will not be executed.
Therefore, you can define components for each *State* side by side.

```kt
viewStore.render<CounterState.Loading> {
    Text(
        text = "loading..",
    )
}

viewStore.render<CounterState.Main> {
    Text(
        text = state.count.toString(),
    )
}
```

If you use lower components in the `render()` block, pass its instance.

```kt
viewStore.render<CounterState.Main> {
    YourComposable(
        viewStore = this, // ViewStore instance for CounterState.Main
    )
}
```

```kt
@Composable
fun YourComposable(
    // Main state is confirmed
    viewStore: ViewStore<CounterState.Main, CounterAction, CounterEvent>,
) {
    Text(
        text = viewStore.state.count.toString()
    )
}
```

### Dispatch Action

Use ViewStore's `.dispatch()` with the target *Action*.

```kt
Button(
    onClick = { viewStore.dispatch(CounterAction.Increment) },
) {
    Text(
        text = "increment"
    )
}
```

### Event handling

Use ViewStore's `.handle()` with the target *Event*.

```kt
viewStore.handle<CounterEvent.ShowToast> { event ->
    // do something..
}
```

In the above example, you can also subscribe to the parent *Event* type.

```kt
viewStore.handle<CounterEvent> { event ->
    when (event) {
        is CounterEvent.ShowToast -> // do something..
        is CounterEvent.GoBack -> // do something..
        // ...
```

### Mock for preview and testing

Create an instance of `ViewStore` directly with the target *State*.

```kt
@Preview
@Composable
fun LoadingPreview() {
    MyApplicationTheme {
        CounterScreen(
            viewStore = ViewStore {
                CounterState.Loading
            },
        )
    }
}
```

Therefore, if you prepare only the *State*, it is possible to develop the UI.
</details>

## Middleware

<details>
<summary>contents</summary>

You can create extensions that work with the *Store*.
To do this, create a class that implements the `Middleware` interface and override the necessary methods.

```kt
class YourMiddleware<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    override suspend fun afterStateChange(state: S, prevState: S) {
        // do something..
    }
}
```

Apply the created Middleware as follows:

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    // add Middleware instance
    middleware(YourMiddleware())

    // or, implement Middleware directly here
    middleware(
        object : Middleware<CounterState, CounterAction, CounterEvent> {
            override suspend fun afterStateChange(state: CounterState, prevState: CounterState) {
                // do something..
            }
        },
    )

    // add multiple Middlewares
    middlewares(..., ...)
}
```

Note that *State* is read-only in Middleware.

Each Middleware method is a suspending function, so it can be run synchronously (not asynchronously) with the *Store*.
However, since it will interrupt the *Store* process, you should prepare a new CoroutineScope for long processes.

In the next section, we will introduce pre-prepared Middleware.
The source code is the `:tart-logging` and `:tart-message` modules in this repository, so you can use it as a reference for your Middleware implementation.

### Logging

Middleware for logging Store operations.

```kt
implementation("io.yumemi.tart:tart-logging:<latest-release>")
```

Apply the `simpleLogging()` middleware factory function to your *Store* to log all actions, events, state changes, and errors.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store {
    // ...

    middleware(simpleLogging())
}
```

You can create custom logging middleware by extending the [LoggingMiddleware](tart-logging/src/commonMain/kotlin/io/yumemi/tart/logging/LoggingMiddleware.kt) class if you need more control over the logging behavior.

```kt
middleware(
    object : LoggingMiddleware<CounterState, CounterAction, CounterEvent>(
        logger = YourLogger() // specify your logger
    ) {
        // override methods
        override suspend fun beforeStateEnter(state: CounterState) {
            log(...)
        }
    },
)
```

### Message

Middleware for sending messages between *Stores*.

```kt
implementation("io.yumemi.tart:tart-message:<latest-release>")
```

First, prepare classes for messages.

```kt
sealed interface MainMessage : Message {
    data object LoggedOut : MainMessage
    data class CommentLiked(val commentId: Int) : MainMessage
}
```

Apply the `receiveMessages()` middleware factory function to the *Store* that receives messages.

```kt
val myPageStore: Store<MyPageState, MyPageAction, MyPageEvent> = Store {
    // ...

    middleware(
        receiveMessages { message ->
            when (message) {
                MainMessage.LoggedOut -> dispatch(MyPageAction.doLogoutProcess)
                // ...
            }
        }
    )
}
```

Define the `message()` specification at any point in the *Store* that sends messages.

```kt
val mainStore: Store<MainState, MainAction, MainEvent> = Store {
    // ...

    state<MainState.LoggedIn> { // leave the logged-in state
        exit {
            message(MainMessage.LoggedOut)
        }
    }
}
```
</details>

## Testing Store

Tart's architecture makes writing unit tests for your *Store* straightforward.
For test examples, see the [commonTest](tart-core/src/commonTest/kotlin/io/yumemi/tart/core) directory in the `:tart-core` module.

## Acknowledgments

I used [Flux](https://facebookarchive.github.io/flux/) and [UI layer](https://developer.android.com/topic/architecture/ui-layer) as a reference for the design, and [Macaron](https://github.com/fika-tech/Macaron) for the implementation.
