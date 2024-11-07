# Tart

Tart is a Flux framework for Kotlin Multiplatform.

- Data flow is one-way, making it easy to understand.
- Since the state during processing is unchanged, there is no need to be aware of side effects.
- Code becomes declarative.
- Works on multiple platforms.

I used [Flux](https://facebookarchive.github.io/flux/) and [UI layer](https://developer.android.com/topic/architecture/ui-layer) as a reference for the design, and [Macaron](https://github.com/fika-tech/Macaron) for the implementation.

## Installation

```kotlin
implementation("io.yumemi.tart:tart-core:<latest-release>")
```

## Usage

**Under preparation..**

### For iOS

Store's `.state(StateFlow)` and `.event(Flow)` cannot be used, so use `.collectState()` and `.collectEvent()`. If the State and Event change, you will be notified with a callback.

### Disposal of Coroutines

If you are not using an automatically destroyed scope like Android's ViewModelScope, call the `.dispose()` method on the Store.

## Compose

You may use `.state(StateFlow)`, `.event(Flow)`, `.dispatch()`, etc. provided by the Store, but we provide a mechanism for Compose.

```kotlin
implementation("io.yumemi.tart:tart-compose:<latest-release>")
```

Create an instance of the `ViewStore` from a Store instance using the `ViewStore#create()` method.
For example, if you have a Store in your ViewModel, it would look like this:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // create ViewStore instance
            val viewStore = ViewStore.create(mainViewModel.store)

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // pass as an argument to Composable component
                    YourComposableComponent(
                        viewStore = viewStore,
                    )
// ... 
```

### Rendering using State

Use `ViewStore.state` value.

```kotlin
Text(
    text = state.test,
)
```

Use `ViewStore.render()` method with target State.

```kotlin
viewStore.render<YourState.Stable> {
    YourComposableComponent()
}
```

If it does not match the current State, the `{ }` block will not be executed.
Therefore, you can define views for each State side by side.

```kotlin
viewStore.render<YourState.Loading> {
    YourComposableComponent_A()
}

viewStore.render<YourState.Stable> {
    YourComposableComponent_B()
}
```

State properties can be accessed with `this` scope.

```kotlin
viewStore.render<YourState.Stable> {
    YourComposableComponent(url = this.url) // this. can be omitted
}
```

### Dispatch Action

Use `ViewStore.dispatch()` method with target Action.

```kotlin
Button(
    onClick = { viewStore.dispatch(MainAction.ClickButton) },
) {
// ...
```

### Event handling

Use `ViewStore.handle()` method with target State.

```kotlin
viewStore.handle<MainEvent.ShowToast> { event ->
    // do something..
}
```

You can also subscribe to parent Event types.

```kotlin
viewStore.handle<MainEvent> { event ->
    when (event) {
        is MainEvent.ShowToast -> // do something..
        is MainEvent.GoBack -> // do something..
        // ...
    }
```

### Mock for preview and testing

Use `ViewStore#mock()` method with target State.

```kotlin
@Preview
@Composable
fun LoadingPreview() {
    MyApplicationTheme {
        YourComposableComponent(
            viewStore = ViewStore.mock(
                state = MainState.Loading,
            ),
        )
    }
}
```

Therefore, by defining only the State, it is possible to develop the UI even before implementing the Store.

## Middleware

You can create extensions that work with the Store.
To do this, create a class that implements the `Middleware` interface and override the necessary methods.

```kotlin
class YourMiddleware<S : State, A : Action, E : Event> : Middleware<S, A, E> {
    override suspend fun afterStateChange(state: S, prevState: S) {
        // do something..
    }
}
```

Apply Middleware to Store as follows:

```kotlin
class MainStore(
    // ...
) : Store.Base<MainState, MainAction, MainEvent>(
    // ...
) {
    override val middlewares: List<Middleware<MainState, MainAction, MainEvent>> = listOf(
        // add Middleware instance to List
        YourMiddleware(),
        // or, implement here
        object : Middleware<MainState, MainAction, MainEvent> {
            override suspend fun afterStateChange(state: MainState, prevState: MainState) {
                // do something..
            }
        },
    )

// ...
```

Since each method of Middleware is a suspending function, it operates in synchronization with Store, so you can create an extension that is completely synchronized with Store.
However, since it will interrupt the Store process, you should prepare a new CoroutineScope for long processes.

Also note that State is read-only in Middleware.

In the next section, we will introduce pre-prepared Middleware.
The source code is the `:tart-logging` and `:tart-message` modules in this repository, so you can use it as a reference for your Middleware implementation.

### Logging

Middleware that outputs logs for debugging and analysis.

```kotlin
implementation("io.yumemi.tart:tart-logging:<latest-release>")
```

```kotlin
override val middlewares: List<Middleware<MainState, MainAction, MainEvent>> = listOf(
    LoggingMiddleware(),
)
```

The implementation of the `LoggingMiddleware` is [here](/blob/main/tart-logging/src/commonMain/kotlin/io/yumemi/tart/logging/LoggingMiddleware.kt), change the arguments or override
the class as necessary.
If you want to change the logger, prepare a class that implements the `Logger` interface.

```kotlin
override val middlewares: List<Middleware<MainState, MainAction, MainEvent>> = listOf(
    object : LoggingMiddleware<MainState, MainAction, MainEvent>(
        logger = YourLogger()
    ) {
        override suspend fun beforeStateEnter(state: MainState) {
            // do something..
        }
    },
)
```

### Message

Middleware for sending messages between Stores.

```kotlin
implementation("io.yumemi.tart:tart-message:<latest-release>")
```

Prepare a class with a `Message` interface.

```kotlin
interface MainMessage : Message {
    data object LogoutCompleted : MainMessage
    data class CommentLiked(val commentId: Int) : MainMessage
    // ...
}

```

Apply `MessageSendMiddleware` to the Store that sends messages.

```kotlin
override val middlewares: List<Middleware<MainState, MainAction, MainEvent>> = listOf(
    object : MessageSendMiddleware<MainState, MainAction, MainEvent>() {
        override suspend fun onEvent(event: MainEvent, send: SendFun, store: Store<MainState, MainAction, MainEvent>) {
            when (event) {
                is MainEvent.NofityLogout -> send(MainMessage.LogoutCompleted)
                // ...
            }
        }
    },
)
```

Apply `MessageReceiveMiddleware` to the Store that receives messages.

```kotlin
override val middlewares: List<Middleware<SubState, SubAction, SubEvent>> = listOf(
    object : MessageReceiveMiddleware<SubState, SubAction, SubEvent>() {
        override suspend fun receive(message: Message, store: Store<SubState, SubAction, SubEvent>) {
            when (message) {
                is MainEvent.LogoutCompleted -> store.dispatch(SubAction.doLogout)
                // ...
            }
        }
    },
)
```

## Prevent large `onDispatch()` method bodies

Since the processing for Store is concentrated in the `onDispatch()` method, its body tends to be large.
Therefore, delegate the processing to another function as necessary.

```kotlin
override suspend fun onDispatch(state: MainState, action: MainAction, emit: EmitFun<MainEvent>): MainState {
    return when (state) {
        is MainState.StateA -> reduceStateA(state, action, emit)
        is MainState.StateB -> reduceStateB(state, action, emit)
        // ...
    }
}

private fun reduceStateA(state: MainState.StateA, action: MainAction, emit: EmitFun<MainEvent>): MainState {
    // do something..
}

private fun reduceStateB(state: MainState.StateB, action: MainAction, emit: EmitFun<MainEvent>): MainState {
    // do something..
}
```

The above is an example of delegation for each state, but common processing can also be delegated as usual.
Of course, delegated processes can also access Store instance fields such as Repository and UseCase.

Alternatively, you can delegate to a class like this:

```kotlin
class StateA_Reducer(
    private val userRepository: UserRepository, // inject if necessary
) {
    suspend fun reduce(state: MainState.StateA, action: MainAction, emit: EmitFun<MainEvent>): MainState {
        return when (action) {
            is MainAction.ActionA -> reduceActionA(state, action, emit)
            is MainAction.ActionB -> reduceActionB(state, action, emit)
            // ...
        }
    }

    private suspend fun reduceActionA(state: MainState.StateA, action: MainAction.ActionA, emit: EmitFun<MainEvent>): MainState {
        // do something..
    }

    private suspend fun reduceActionB(state: MainState.StateA, action: MainAction.ActionB, emit: EmitFun<MainEvent>): MainState {
        // do something..
    }

    // ...
}
```

```kotlin
override suspend fun onDispatch(state: MainState, action: MainAction, emit: EmitFun<MainEvent>): MainState {
    return when (state) {
        is MainState.StateA -> stateA_Reducer.reduce(state, action, emit)
        is MainState.StateB -> stateB_Reducer.reduce(state, action, emit)
        // ...
    }
}
```

Or, you can delegate to Object.

```kotlin
override suspend fun onDispatch(state: MainState, action: MainAction, emit: EmitFun<MainEvent>): MainState {
    return when (state) {
        is MainState.StateA -> StateA_Reducer.reduce(state, action, emit)
        is MainState.StateB -> StateB_Reducer.reduce(state, action, emit)
        // ...
    }
}

object StateA_Reducer {
    fun reduce(state: MainState.StateA, action: MainAction, emit: EmitFun<MainEvent>): MainState {
        return when (action) {
            is MainAction.ActionA -> reduceActionA(state, action, emit)
            is MainAction.ActionB -> reduceActionB(state, action, emit)
            // ...
        }
    }

    private fun reduceActionA(state: MainState.StateA, action: MainAction.ActionA, emit: EmitFun<MainEvent>): MainState {
        // do something..
    }

    private fun reduceActionB(state: MainState.StateA, action: MainAction.ActionB, emit: EmitFun<MainEvent>): MainState {
        // do something..
    }
}

// ...
```

After all, the `onDispatch()` method is a function that simply returns new State from current State and Action, so you can freely define the logic inside.
Either way, the State is immutable and the processing is one-way, so the code is declarative and simple.

