# Middleware には直接 state 更新 API を入れない

- 更新日: 2026-04-26
- 関連: [Tart の設計原則](../design/2026-04-23-design-principles.md), [Middleware 実行ポリシーは並行を標準にする](./2026-04-23-middleware-execution-policy.md)

## 背景

現在の `MiddlewareScope` は `dispatch()` と `launch()` だけを公開しており、middleware 自体は state を直接変更できない。

現状のイメージは次のとおりである。

```kt
interface MiddlewareScope<A : Action> {
    fun dispatch(action: A)
    fun launch(
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        block: suspend CoroutineScope.() -> Unit,
    )
}
```

middleware は外部入力を受けたら action を `dispatch()` し、その action を state machine 側で処理する。

```kt
override suspend fun onStart(middlewareScope: MiddlewareScope<AppAction>, state: AppState) {
    middlewareScope.launch {
        repository.observe().collect { value ->
            middlewareScope.dispatch(AppAction.ExternalValueArrived(value))
        }
    }
}
```

ここで検討したのは、`MiddlewareScope` の `launch {}` の中から `transaction {}` を使えるようにし、middleware が外部 stream や callback bridge を受けながら直接 state を更新できるようにする案である。

検討案のイメージは次のようなものである。

```kt
interface MiddlewareScope<S : State, A : Action, E : Event> {
    fun dispatch(action: A)
    fun launch(
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        block: suspend LaunchScope<S, E>.() -> Unit,
    )

    interface LaunchScope<S : State, E : Event> : StoreScope {
        val isActive: Boolean
        suspend fun event(event: E)
        suspend fun transaction(
            coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
            block: suspend TransactionScope<S, E>.() -> Unit,
        )
    }
}
```

```kt
override suspend fun onStart(
    middlewareScope: MiddlewareScope<AppState, AppAction, AppEvent>,
    state: AppState,
) {
    middlewareScope.launch {
        repository.observe().collect { value ->
            transaction {
                val current = this.state as? AppState.Ready ?: return@transaction
                nextState(current.copy(value = value))
            }
        }
    }
}
```

この案は、外部入力を action に変換するだけのために中継 action を増やさずに済む、という利点がある。

一方で、直接 state 更新 capability を載せると、middleware は外部入力の橋渡しや監視だけでなく、Store の state を更新する writer の役割まで担うことになる。
また middleware は default で並行実行されるため、複数 middleware が直接 state writer として振る舞い始めると、責務の境界や更新経路の見通しが弱くなりやすい。

## 決定

`Middleware` には、直接 state を更新する API は追加しない。

- `MiddlewareScope` は引き続き `dispatch()` と `launch()` のみを公開する。
- middleware から state を変えたい場合は、引き続き action を `dispatch()` して state machine 側で処理する。
- `launch { transaction { ... } }` のような state 更新 API は `MiddlewareScope` には導入しない。

## 補足

- この判断により、state の書き込み経路は引き続き state/action handler を中心に保てる。middleware は外部入力の橋渡しや監視、補助的な dispatch の責務に留める。
- もし `MiddlewareScope` に直接 state 更新 capability を載せると、middleware は observer や bridge ではなく、Store の追加 writer に近い存在になる。これは現状の設計上の位置づけより一段重い責務である。
- middleware の default が並行実行である以上、複数 middleware が直接 state 更新を行う設計は、順序依存や責務分担を読みづらくしやすい。更新が互いに依存するなら、1 つの middleware にまとめるか、Store 本体の state/action 設計で表現する方が自然である。
- 外部 stream や callback bridge を、action を介さずに Store へ反映したい要求が将来強くなった場合は、middleware 拡張としてではなく、別の API として改めて検討する。
