# Plugin 設計メモ

- 更新日: 2026-05-02

## 背景

Tart にはすでに `Middleware` があるが、hook 数が多く、`before/after` の internal lifecycle がそのまま public API に出ている。

一方で、logging、analytics、message bridge、autosave、sync のような用途では、必ずしも `Middleware` と同じ粒度や性質の hook は要らない。
必要なのは interception より、Store の開始、action、state 更新、event、recoverable error を外側から観測し、必要なら補助的な非同期処理や追加 dispatch を行える拡張点である。

そのため、`Middleware` とは別に、より小さい surface を持つ `Plugin` を追加する方向で整理したい。

今回の論点は次のとおり。

- `Plugin` は何のための拡張点か
- どの hook を公開するか
- `close` hook を持つか
- `Middleware` のような execution policy を持つか

## 現在の考え

### `Plugin` の役割

`Plugin` は Store の本体 pipeline を横取りするためのものではなく、**観測と外部連携のための拡張点**として扱う。

- logging や analytics の記録
- message bus / websocket / push などの購読開始
- state 更新後の保存や同期
- recoverable error の報告
- 補助的な action dispatch

逆に、現在処理中の action を差し替える、握りつぶす、`nextState` を横から書き換える、といった interception は `Plugin` の責務にしない。

### surface

現時点の `Plugin` surface は次を基本にする。

```kt
interface Plugin<S : State, A : Action, E : Event> {
    suspend fun onStart(scope: PluginScope<S, A>, state: S) {}
    suspend fun onAction(scope: PluginScope<S, A>, state: S, action: A) {}
    suspend fun onStateChanged(scope: PluginScope<S, A>, prevState: S, state: S) {}
    suspend fun onEvent(scope: PluginScope<S, A>, state: S, event: E) {}
    suspend fun onError(scope: PluginScope<S, A>, state: S, error: Exception) {}
}

interface PluginScope<S : State, A : Action> {
    val currentState: S
    fun dispatch(action: A)
    fun launch(
        dispatcher: CoroutineDispatcher? = null,
        block: suspend CoroutineScope.() -> Unit,
    )
}
```

hook の位相は次のように揃える。

- `onStart`
  - Store start 時
  - 初回 `enter {}` の前
- `onAction`
  - action handler 開始前
- `onStateChanged`
  - state commit と save の後
- `onEvent`
  - event emit 前
- `onError`
  - recoverable error の handler 実行前

`onAction` / `onEvent` / `onError` は開始地点に寄せ、`onStateChanged` だけは更新後の hook とする。
state type change を見たい場合は、`onStateChanged` の中で `prevState::class != state::class` を見れば足りるので、専用 hook は持たない。

名前を `onState` ではなく `onStateChanged` にしているのは、`StoreObserver.onState(state)` と契約が異なるためである。
observer の `onState` は「Store が state snapshot を observer に見せる」通知であり、attach 時の設定によっては start 前の current state を即時に受け取ることもある。
一方 plugin 側で欲しいのは「新しい state が commit され、保存まで済んだ」という更新通知であり、`prevState` も必要になる。
同じ `onState` という名前にすると、snapshot 観測と committed update 観測の違いが見えにくくなるため、plugin 側は `onStateChanged` として分ける。

`onError` の型は `Throwable` ではなく `Exception` とする。
Tart の recoverable error path は `Exception` に限定されており、`CancellationException` やそれ以外の `Throwable` は state machine の error handling に流さないためである（[Error DSL の対象は `Exception` を境界にする](../adr/2026-05-01-error-dsl-exception-boundary.md)）。

### `PluginScope`

`PluginScope` は全 hook で使えるようにする。
`onStart` だけに scope があると、後続 hook で使うために plugin 側が scope を保持する必要があり、不自然である。

scope に入れるのは次の最小セットで十分である。

- `currentState`
  - 最新の committed state snapshot を読む
- `dispatch(action)`
  - 補助的な action を enqueue する
- `launch { ... }`
  - Store-scoped な background work を開始する

`transaction`、`nextState`、`emit`、`cancelLaunch` のような権限は持たせない。
そこまで入れると plugin が hidden handler や interceptor に寄り、責務が重くなりすぎる。

### cleanup は `launch { try/finally }` に寄せる

`Plugin` に `onClose` を入れる案も考えられるが、現状では採らない。

理由は、`Store.close()` だけが Store の終了経路ではないためである。
Store の root scope は親 `Job` にぶら下がっているため、親 scope 側の cancellation でも終了しうる。
`onClose` を `Store.close()` からだけ呼ぶと、「明示 close」は拾えても、「Store の lifetime が終わった」ことまでは拾えない。

そのため plugin 側の cleanup は、明示的な close hook ではなく、`PluginScope.launch` で開始した coroutine の `finally` に寄せる方が自然である。

```kt
override suspend fun onStart(scope: PluginScope<S, A>, state: S) {
    scope.launch {
        val subscription = bus.subscribe { message ->
            scope.dispatch(...)
        }

        try {
            awaitCancellation()
        } finally {
            subscription.dispose()
        }
    }
}
```

この形なら、`Store.close()` でも親 scope cancel でも同じ cleanup が走る。
cleanup の所有者が、その仕事自身の lifetime にぶら下がる点も分かりやすい。

### execution policy は持たない

`Plugin` には `MiddlewareExecutionPolicy` のような execution policy は、少なくとも現時点では持たせない。

`Plugin` も `Middleware` と同じく、複数登録されても互いに独立した外側の拡張として作られることが多い。
その意味では、「独立した plugin なら並行実行でもよい」という考え方自体は自然である。

一方で、そこからすぐに `PluginExecutionPolicy` を public API として足す必要があるとは限らない。
`Plugin` は `dispatch()` や `launch()` を全 hook で使えるため、`Middleware` より一段“動ける”拡張でもある。
ここで policy を公開すると、順序、追加 dispatch の見え方、例外時のふるまいなど、利用者が考えるべき面が増えやすい。

そのため、現時点では **「Plugin は独立した拡張として設計する」ことと、「execution policy を公開する」ことは分けて考える**。
独立した plugin どうしが並行でも成立する、という設計思想は持ちつつも、surface としては余計な option を増やさない方を優先する。

実装上は、当面 registration order で順に流してもよい。
ただしこれは `Plugin` の本質的な価値ではなく、API を安定させるまでの単純な固定挙動として捉える。

もし将来 plugin の実行順を surface として扱うなら、`middleware(...)` / `plugin(...)` の宣言順そのものを強い契約にするより、priority のような明示的な概念で表した方がよい。
宣言順は setup の見た目に依存しやすく、後から並べ替えたときに意図せず挙動が変わりやすいためである。

また、plugin 内の重い仕事や long-running work は、hook 自体の execution policy に頼るより `scope.launch { ... }` に逃がす方が素直である。

`Middleware` の default を並行実行にする考え方は、「middleware 同士の順序依存を前提にしない」ためのものである（[Middleware 実行ポリシーは並行を標準にする](../adr/2026-04-23-middleware-execution-policy.md)）。
`Plugin` にもその発想は一部当てはまるが、現時点では policy まで公開して揃える必要はない。

## 未解決事項

- `Plugin` を今後 `Middleware` の代替へ寄せていくか、並行して残し続けるかは未決定。
- README やサンプルで、plugin の long-running work と cleanup を `launch { try/finally }` ベースでどこまで明示するかは未決定。
- plugin hook の実行順を将来も registration order に固定するか、内部的に並行化する余地を残すかは未決定。
- plugin の実行順を公開仕様にする場合、registration order ではなく priority のような形で表すかは未決定。
- 将来、Store lifetime の終了理由そのものを観測したい要件が出た場合、`onClose` ではなく root `Job` completion ベースの別 hook を検討する余地はある。

## 関連

- [Middleware 実行ポリシーは並行を標準にする](../adr/2026-04-23-middleware-execution-policy.md)
- [Error DSL の対象は `Exception` を境界にする](../adr/2026-05-01-error-dsl-exception-boundary.md)
