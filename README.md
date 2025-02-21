# <img src="doc/logo.png" style="height: 1em; margin-bottom: -0.2em;"> Tart

![Maven Central](https://img.shields.io/maven-central/v/io.yumemi.tart/tart-core)
![License](https://img.shields.io/github/license/yumemi-inc/Tart)
[![Java CI with Gradle](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml/badge.svg)](https://github.com/yumemi-inc/Tart/actions/workflows/gradle.yml)

Tart is a state management framework for Kotlin Multiplatform.

- Data flow is one-way, making it easy to understand.
- Since the state remains unchanged during processing, there is no need to worry about side effects.
- Code becomes declarative.
- Works on multiple platforms (currently on Android and iOS).

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
    data class Set(val count: Int) : CounterAction
    data object Increment : CounterAction
    data object Decrement : CounterAction
}

sealed interface CounterEvent : Event {} // currently empty
```

Create a *Store* class from `Store.Base` with an initial *State*.

```kt
class CounterStore : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState(count = 0),
)
```

Override `onDispatch()` and define how the *State* is changed by *Action*.
This is a `(State, Action) -> State` function.

```kt
class CounterStore : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState(count = 0),
) {
    override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState = when (action) {
        is CounterAction.Set -> {
            state.copy(count = action.count)
        }

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
```

The *Store* preparation is now complete.
Instantiate the `CounterStore` class and keep it in the ViewModel etc.

Issue an *Action* from the UI using the Store's `dispatch()`.

```kt
// example in Compose
Button(
    onClick = { counterStore.dispatch(CounterAction.Increment) },
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

In the `dispatch()` method body, issue an *Event* using the `emit()`.

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

Keep Repository, UseCase, etc. in the instance field of *Store* and use it from `dispatch()` method body.

```kt
class CounterStore(
    private val counterRepository: CounterRepository, // inject to Store
) : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState(count = 0),
) {
    override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState = when (action) {
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

> [!TIP]
> Processing other than changing the *State* may be defined using extension functions for *State* or *Action*.
>
> ```kt
> override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState = when (action) {
>     CounterAction.Load -> {
>         val count = action.loadCount() // call extension function
>         state.copy(count = count)
>     }
> 
>     // ...
> }
> 
> // describe what to do for this Action
> private suspend fun CounterAction.Load.loadCount(): Int {
>     return counterRepository.get()
> }
> ```
>
> In any case, the `onDispatch()` is a simple method that simply returns a new *State* from the current *State* and *Action*, so you can design the code as you like.

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
class CounterStore(
    private val counterRepository: CounterRepository,
) : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState.Loading, // start from loading
) {
    override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState = when (state) {
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
Otherwise, if you want to do something at the start of the *State*, override the `onEnter()` (similarly, you can override the `onExit()` if necessary).

```kt
override suspend fun onEnter(state: CounterState): CounterState = when (state) {
    CounterState.Loading -> {
        val count = counterRepository.get()
        CounterState.Main(count = count) // transition to Main state
    }

    else -> state
}

override suspend fun onDispatch(state: CounterState, action: CounterAction): CounterState = when (state) {
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

If you prepare a *State* for error display and handle the error in the `onEnter()`, it will be as follows:

```kt
sealed interface CounterState : State {
    // ...
    data class Error(val error: Throwable) : CounterState
}
```

```kt
override suspend fun onEnter(state: CounterState): CounterState = when (state) {
    CounterState.Loading -> {
        try {
            val count = counterRepository.get()
            CounterState.Main(count = count)
        } catch (t: Throwable) {
            CounterState.Error(error = t)
        }
    }

    else -> state
```

This is fine, but you can also handle errors by overriding the `onError()`.

```kt
override suspend fun onEnter(state: CounterState): CounterState = when (state) {
    CounterState.Loading -> {
        // no error handling code
        val count = counterRepository.get()
        CounterState.Main(count = count)
    }

    else -> state
}

override suspend fun onError(state: CounterState, error: Throwable): CounterState {
    // you can also branch using state and error inputs if necessary
    return CounterState.Error(error = error)
}
```

Errors can be caught not only in the `onEnter()` but also in the `onDispatch()` and `onExit()`.

### Constructor arguments when creating a *Store*

#### initialState [required]

Specify the first *State*.

```kt
class CounterStore : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState.Loading,
)
```

Also, specify it when restoring the *State* saved to ViewModel's SavedStateHandle etc.
On the other hand, to save the *State*, it is convenient to obtain the latest *State* using the [collectState()](#for-ios).

#### coroutineContext [option]

If omitted, only the `Dispatcher.Default` thread will be used without inheriting the context.
Specify it when you want to match the Store's Coroutines lifecycle with another context or change the thread on which it operates.

#### onError [option]

This callback can handle uncaught errors.
For example, logging can be done as follows:

```kt
class CounterStore(
    logger: YourLogger,
) : Store.Base<CounterState, CounterAction, CounterEvent>(
    initialState = CounterState.Loading,
    onError = { logger.log(it) },
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
@HiltViewModel
class CounterViewModel @Inject constructor(
    counterRepository: CounterRepository,
) : ViewModel() {
    val store = CounterStore(
        counterRepository = counterRepository,
    )

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
                    // pass the ViewStore instance to lower components
                    YourComposable(
                        viewStore = viewStore,
                    )
            // ... 
```

You can create a `ViewStore` instance without using ViewModel as shown below, but note that *States* must implement `Parcelable` or `Serializable` because they are used internally for [rememberSaveable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/package-summary.html).

```kt
@AndroidEntryPoint
class CounterActivity : ComponentActivity() {
    @Inject
    lateinit var counterRepository: CounterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // create an instance of ViewStore
            val viewStore = rememberViewStoreSaveable { savedState: CounterState? ->
                CounterStore(
                    counterRepository = counterRepository,
                    initialState = savedState ?: CounterState.Loading,
                )
            }

            // ... 
```

Alternatively, you can prepare a [StateSaver](tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StateSaver.kt) and handle the persistence yourself.

<details>
<summary>TIPS: Preparing a Store Factory class</summary>

Like Repository and UseCase, if there are many dependencies that need to be passed to the *Store* constructor, it is better to prepare a factory class as follows:

```kt
// provide with DI libraries
class CounterStoreFactory(
    private val counterRepository: CounterRepository,
    private val userRepository: UserRepository,
    private val logger: YourLogger,
) {
    fun create(initialState: CounterState): CounterStore {
        return CounterStore(
            counterRepository = counterRepository,
            userRepository = userRepository,
            logger = logger,
            initialState = initialState,
        )
    }
}

// ...

@AndroidEntryPoint
class CounterActivity : ComponentActivity() {
    @Inject
    lateinit var counterStoreFactory: CounterStoreFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // create an instance of ViewStore
            val viewStore = rememberViewStoreSaveable { savedState: CounterState? ->
`               counterStoreFactory.create(
                    initialState = savedState ?: CounterState.Loading,
                )
            }

            // ... 
```
</details>

### Rendering using State

If the *State* is single, just use ViewStore's `.state`.

```kt
Text(
    text = viewStore.state.count.toString(),
)
```

If there are multiple *States*, use `.render()` method with target *State*.

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

// ...

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

Use ViewStore's `.dispatch()` with target *Action*.

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

Use ViewStore's `.handle()` with target *Event*.

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
    }
```

### Mock for preview and testing

Create an instance of ViewStore using the `mock()` with target *State*.
You can statically create a ViewStore instance without a *Store* instance.

```kt
@Preview
@Composable
fun LoadingPreview() {
    MyApplicationTheme {
        YourComposable(
            viewStore = ViewStore.mock(
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
class MainStore(
    // ...
) : Store.Base<CounterState, CounterAction, CounterEvent>(
    // ...
) {
    override val middlewares: List<Middleware<CounterState, CounterAction, CounterEvent>> = listOf(
        // add Middleware instance to List
        YourMiddleware(),
        // or, implement Middleware directly here
        object : Middleware<CounterState, CounterAction, CounterEvent> {
            override suspend fun afterStateChange(state: CounterState, prevState: CounterState) {
                // do something..
            }
        },
    )

    // ...
```

You can also list a Middleware instance created with DI Libraries.

Each Middleware method is a suspending function, so it can be run synchronously (not asynchronously) with the *Store*.
However, since it will interrupt the *Store* process, you should prepare a new CoroutineScope for long processes.

Note that *State* is read-only in Middleware.

In the next section, we will introduce pre-prepared Middleware.
The source code is the `:tart-logging` and `:tart-message` modules in this repository, so you can use it as a reference for your Middleware implementation.

### Logging

Middleware that outputs logs for debugging and analysis.

```kt
implementation("io.yumemi.tart:tart-logging:<latest-release>")
```

```kt
override val middlewares: List<Middleware<CounterState, CounterAction, CounterEvent>> = listOf(
    LoggingMiddleware(),
)
```

The implementation of the `LoggingMiddleware` is [here](tart-logging/src/commonMain/kotlin/io/yumemi/tart/logging/LoggingMiddleware.kt), change the arguments or override
methods as necessary.
If you want to change the logger, prepare a class that implements the `Logger` interface.

```kt
override val middlewares: List<Middleware<CounterState, CounterAction, CounterEvent>> = listOf(
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
override val middlewares: List<Middleware<MyPageState, MyPageAction, MyPageEvent>> = listOf(
    object : MessageMiddleware<MyPageState, MyPageAction, MyPageEvent>() {
        override suspend fun receive(message: Message, dispatch: (action: MyPageAction) -> Unit) {
            when (message) {
                is MainMessage.LoggedOut -> dispatch(MyPageAction.doLogoutProcess)
                // ...
            }
        }
    },
)
```

Call the `send()` at any point in the *Store* that sends messages.

```kt
override suspend fun onExit(state: MainState) = when (state) {
    is MainState.LoggedIn -> { // leave the logged-in state
        send(MainMessage.LoggedOut)
    }

    // ...
```
</details>

## Acknowledgments

I used [Flux](https://facebookarchive.github.io/flux/) and [UI layer](https://developer.android.com/topic/architecture/ui-layer) as a reference for the design, and [Macaron](https://github.com/fika-tech/Macaron) for the implementation.
