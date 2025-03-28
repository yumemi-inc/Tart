# <img src="doc/logo.png" style="height: 1em; margin-bottom: -0.2em;"> Tart

![Maven Central](https://img.shields.io/maven-central/v/io.yumemi.tart/tart-core)
![License](https://img.shields.io/github/license/yumemi-inc/Tart)
[![Java CI with Gradle](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml/badge.svg)](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml)

Tart is a state management framework for Kotlin Multiplatform.

- Data flow is one-way, making it easy to understand.
- Since the state remains unchanged during processing, there is no need to worry about side effects.
- Code becomes declarative.
- Works on multiple platforms (currently on Android and iOS).
  - Enables code sharing and consistent logic implementation across platforms.

The architecture is inspired by [Flux](https://facebookarchive.github.io/flux/) and is as follows:

<div align="center">
  <img src="doc/architecture.png" width=60% />
</div>
</br>

The core functionality of the *Store* can be represented by the following function:

```kt
(State, Action) -> State
```

In this framework, based on the above function, we only need to be concerned with the relationship between *State* and *Action*.

## Installation

```kt
implementation("io.yumemi.tart:tart-core:<latest-release>")
```

## Usage

### Basic

Take a simple counter application as an example.

First, prepare classes for *State*, *Action*, and *Event*.

```kt
data class CounterState(val count: Int) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
}

sealed interface CounterEvent : Event {} // currently empty
```

Create a *Store* instance using the `Store()` function with an initial *State*.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    initialState = CounterState(count = 0),
)
```

Define how the *State* is changed by *Action* by providing the `onDispatch` parameter.
This is a `(State, Action) -> State` function.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    initialState = CounterState(count = 0),
    onDispatch = { state, action ->
        when (action) {
            is CounterAction.Increment -> {
                state.copy(count = state.count + 1)
            }

            is CounterAction.Decrement -> {
                if (0 < state.count) {
                    state.copy(count = state.count - 1)
                } else {
                    state // do not change State
                }
            }
        }
    }
)
```

The *Store* preparation is now complete.
Keep the store instance in the ViewModel etc.

Issue an *Action* from the UI using the Store's `dispatch()`.

```kt
// example in Compose
Button(
    onClick = { store.dispatch(CounterAction.Increment) },
) {
    Text(text = "increment")
}
```

The new *State* will be reflected in the Store's `.state` (StateFlow), so draw it to the UI.

### Notify event to UI

Prepare classes for *Event*.

```kt
sealed interface CounterEvent : Event {
    data class ShowToast(val message: String) : CounterEvent
    data object NavigateToNextScreen : CounterEvent
}
```

In the `onDispatch` parameter, issue an *Event* using the `emit()`.

```kt
is CounterAction.Decrement -> {
    if (0 < state.count) {
        state.copy(count = state.count - 1)
    } else {
        emit(CounterEvent.ShowToast("Can not Decrement.")) // issue event
        state
    }
}
```

Subscribe to the Store's `.event` (Flow) on the UI, and process it.

### Access to Repository, UseCase, etc.

Keep Repository, UseCase, etc. accessible from your store creation scope and use it from the `onDispatch` parameter.

```kt
class CounterStoreFactory( // instantiate with DI library etc.
    private val counterRepository: CounterRepository,
) {
    fun create(): Store<CounterState, CounterAction, CounterEvent> = Store(
        initialState = CounterState(count = 0),
        onDispatch = { state, action ->
            when (action) {
                CounterAction.Load -> {
                    val count = counterRepository.get() // load
                    state.copy(count = count)
                }

                is CounterAction.Increment -> {
                    val count = state.count + 1
                    state.copy(count = count).apply {
                        counterRepository.set(count) // save
                    }
                }

                // ...
```

<details>
<summary>TIPS: Creating Extension Functions</summary>

Processing other than changing the *State* may be defined using extension functions for *State* or *Action*.

```kt
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
) {
    fun create(): Store<CounterState, CounterAction, CounterEvent> = Store(
        initialState = CounterState(count = 0),
        onDispatch = { state, action ->
            when (action) {
                CounterAction.Load -> {
                    val count = action.loadCount() // call extension function
                    state.copy(count = count)
                }

                // ...
    )

    // describe what to do for this Action
    private suspend fun CounterAction.Load.loadCount(): Int {
        return counterRepository.get()
    }
}
```

In any case, the `onDispatch` parameter is a simple function that returns a new *State* from the current *State* and *Action*, so you can design the code as you like.
</details>

### Multiple states and transitions

In the previous examples, the *State* was single.
However, if there are multiple *States*, for example a UI during data loading, prepare multiple *States*.

```kt
sealed interface CounterState : State {
    data object Loading: CounterState 
    data class Main(val count: Int): CounterState
}
```

```kt
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
) {
    fun create(): Store<CounterState, CounterAction, CounterEvent> = Store(
        initialState = CounterState.Loading, // start from loading
        onDispatch = { state, action ->
            when (state) {
                CounterState.Loading -> when (action) {
                    CounterAction.Load -> {
                        val count = counterRepository.get()
                        CounterState.Main(count = count) // transition to Main state
                    }

                    else -> state
                }

                is CounterState.Main -> when (action) {
                    is CounterAction.Increment -> {
                        // ...
```

In this example, the `CounterAction.Load` action needs to be issued from the UI when the application starts.
Otherwise, if you want to do something at the start of the *State*, use the `onEnter` parameter (similarly, you can use the `onExit` parameter if necessary).

```kt
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
) {
    fun create(): Store<CounterState, CounterAction, CounterEvent> = Store(
        initialState = CounterState.Loading, // start from loading
        onEnter = { state ->
            when (state) {
                CounterState.Loading -> {
                    val count = counterRepository.get()
                    CounterState.Main(count = count) // transition to Main state
                }

                else -> state
            }
        },
        onDispatch = { state, action ->
            when (state) {
                is CounterState.Main -> when (action) {
                    is CounterAction.Increment -> {
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

If you prepare a *State* for error display and handle the error in the `onEnter` parameter, it will be as follows:

```kt
sealed interface CounterState : State {
    // ...
    data class Error(val error: Throwable) : CounterState
}
```

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    onEnter = { state ->
        when (state) {
            CounterState.Loading -> {
                try {
                    val count = counterRepository.get()
                    CounterState.Main(count = count)
                } catch (t: Throwable) {
                    CounterState.Error(error = t)
                }
            }

            else -> state
        }
    }
)
```

This is fine, but you can also handle errors using the `onError` parameter.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    onEnter = { state ->
        when (state) {
            CounterState.Loading -> {
                // no error handling code
                val count = counterRepository.get()
                CounterState.Main(count = count)
            }

            else -> state
        }
    },
    onError = { state, error ->
        // you can also branch using state and error inputs if necessary
        CounterState.Error(error = error)
    }
)
```

Errors can be caught not only in the `onEnter` but also in the `onDispatch` and `onExit` parameters.
In other words, your business logic errors can be handled in the `onError` parameter.

On the other hand, uncaught errors in the entire Store (such as system errors) can be handled with the `exceptionHandler` parameter:

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    exceptionHandler = ...
)
```

### Specifying coroutineContext

The Store operates using Coroutines, and the default CoroutineContext is `EmptyCoroutineContext + Dispatchers.Default`.
Specify it when you want to match the Store's Coroutines lifecycle with another context or change the thread on which it operates.

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    coroutineContext = ...
)
```

### State Persistence

You can prepare a [StateSaver](tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StateSaver.kt) and provide the `stateSaver` parameter to automatically handle state persistence:

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    stateSaver = ...
)
```

### For iOS

Coroutines like Store's `.state` (StateFlow) and `.event` (Flow) cannot be used on iOS, so use the `.collectState()` and `.collectEvent()`.
If the *State* or *Event* changes, you will be notified through these callbacks.

### Disposal of Coroutines

If you do not specify a context that is automatically disposed like ViewModel's `viewModelScope` or Compose's `rememberCoroutineScope()` in the constructor of the *Store*, call Store's `.dispose()` explicitly when the *Store* is no longer needed.
Then, processing of all Coroutines will stop.

## Compose

<details>
<summary>contents</summary>

You can use Store's `.state` (StateFlow), `.event` (Flow), and `.dispatch()` directly, but we provide a mechanism for Compose.

```kt
implementation("io.yumemi.tart:tart-compose:<latest-release>")
```

Create an instance of the `ViewStore` from a *Store* instance using the `rememberViewStore()`.
For example, if you have a *Store* in ViewModels, it would look like this:

```kt
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
) {
    fun create(): Store<CounterState, CounterAction, CounterEvent> = Store(
        // ...
    )
}
```

```kt
@HiltViewModel
class CounterViewModel @Inject constructor(
    counterStoreFactory: CounterStoreFactory,
) : ViewModel() {

    val store = counterStoreFactory.create()

    override fun onCleared() {
        store.dispose()
    }
}
```

```kt
@AndroidEntryPoint
class CounterActivity : ComponentActivity() {

    private val counterViewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // create an instance of ViewStore
            val viewStore = rememberViewStore(counterViewModel.store)

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // pass the ViewStore instance to lower components if necessary
                    YourComposable(
                        viewStore = viewStore,
                    )
            // ... 
```

You can create a `ViewStore` instance without using ViewModel as shown below:

```kt
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
) {
    fun create(stateSaver: StateSaver<CounterState>): Store<CounterState, CounterAction, CounterEvent> = Store(
        // ...
        stateSaver = stateSaver,
    )
}
```

```kt
@AndroidEntryPoint
class CounterActivity : ComponentActivity() {

    @Inject
    lateinit var counterStoreFactory: CounterStoreFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewStore = rememberViewStore(
                counterStoreFactory.create(
                    stateSaver = rememberStateSaver(), // state persistence during screen rotation, etc.
                )
            )

            // ... 
```

### Rendering using State

If the *State* is single, just use ViewStore's `.state`.

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
        YourComposable(
            viewStore = ViewStore(
                state = CounterState.Loading,
            ),
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
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    middlewares = listOf(
        // add Middleware instance to List
        YourMiddleware(),
        // or, implement Middleware directly here
        object : Middleware<CounterState, CounterAction, CounterEvent> {
            override suspend fun afterStateChange(state: CounterState, prevState: CounterState) {
                // do something..
            }
        },
    )
)
```

Note that *State* is read-only in Middleware.

Each Middleware method is a suspending function, so it can be run synchronously (not asynchronously) with the *Store*.
However, since it will interrupt the *Store* process, you should prepare a new CoroutineScope for long processes.

In the next section, we will introduce pre-prepared Middleware.
The source code is the `:tart-logging` and `:tart-message` modules in this repository, so you can use it as a reference for your Middleware implementation.

### Logging

Middleware that outputs logs for debugging and analysis.

```kt
implementation("io.yumemi.tart:tart-logging:<latest-release>")
```

```kt
val store: Store<CounterState, CounterAction, CounterEvent> = Store(
    // ...
    middlewares = listOf(
        LoggingMiddleware(),
    )
)
```

The implementation of the `LoggingMiddleware` is [here](tart-logging/src/commonMain/kotlin/io/yumemi/tart/logging/LoggingMiddleware.kt), change the arguments or override
methods as necessary.
If you want to change the logger, prepare a class that implements the `Logger` interface.

```kt
middlewares = listOf(
    object : LoggingMiddleware<CounterState, CounterAction, CounterEvent>(
        logger = YourLogger() // change logger
    ) {
        // override other methods
        override suspend fun beforeStateEnter(state: CounterState) {
            // ...
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

Apply the `MessageMiddleware` to the *Store* that receives messages.

```kt
val myPageStore: Store<MyPageState, MyPageAction, MyPageEvent> = Store(
    // ...
    middlewares = listOf(
        MessageMiddleware { message ->
            when (message) {
                MainMessage.LoggedOut -> dispatch(MyPageAction.doLogoutProcess)
                // ...
```

Call the `send()` at any point in the *Store* that sends messages.

```kt
val mainStore: Store<MainState, MainAction, MainEvent> = Store(
    // ...
    onExit = { state ->
        when (state) {
            is MainState.LoggedIn -> { // leave the logged-in state
                send(MainMessage.LoggedOut)
            }
            // ...
```
</details>

## Acknowledgments

I used [Flux](https://facebookarchive.github.io/flux/) and [UI layer](https://developer.android.com/topic/architecture/ui-layer) as a reference for the design, and [Macaron](https://github.com/fika-tech/Macaron) for the implementation.
