# Plugin 設計メモ

- 更新日: 2026-05-06

## 背景

Tart にはすでに `Middleware` があるが、hook 数が多く、`before/after` の internal lifecycle がそのまま public API に出ている。

一方で、logging、analytics、message bridge、autosave、sync のような用途では、必ずしも `Middleware` と同じ粒度や性質の hook は要らない。
そのため、`Middleware` とは別に、より小さい surface を持つ `Plugin` を追加する方向で整理したい。

今回の論点は次のとおり。

- `Plugin` は何のための拡張点か
- どの hook を公開するか

## 現在の考え

### `Plugin` の役割

`Plugin` は Store の本体 pipeline を横取りするためのものではなく、**観測と外部連携のための拡張点**として扱う。

- logging や analytics の記録
- message bus / websocket / push などの購読開始
- state 更新後の保存や同期
- 補助的な action dispatch

逆に、現在処理中の action を差し替える、握りつぶす、`nextState` を横から書き換える、といった interception は `Plugin` の責務にしない。

### surface

現時点の `Plugin` surface は次を基本にする。

```kt
interface Plugin<S : State, A : Action, E : Event> {
    suspend fun onStart(scope: PluginScope<S, A>, state: S) {}
    suspend fun onAction(scope: PluginScope<S, A>, state: S, action: A) {}
    suspend fun onState(scope: PluginScope<S, A>, prevState: S, state: S) {}
    suspend fun onEvent(scope: PluginScope<S, A>, state: S, event: E) {}
}

interface PluginScope<S : State, A : Action> {
    fun launch(
        dispatcher: CoroutineDispatcher? = null,
        block: suspend LaunchScope<S, A>.() -> Unit,
    )

    interface LaunchScope<S : State, A : Action> {
        val currentState: S
        fun dispatch(action: A)
    }
}
```

hook の位相は、単に `before` / `after` を機械的に揃えるのではなく、**Store 境界のどちら側を観測するか**で揃える。

- `onStart`
  - Store start 時
  - 初回 `enter {}` の前
- `onAction`
  - action handler 開始前
  - dispatch 試行を確実に拾う
- `onState`
  - state commit / save / observer 通知の後
- `onEvent`
  - event emit / observer 通知の後

この整理では、

- `onAction`
  - Store への **入力** を観測する hook なので事前
- `onState`
  - Store から確定した state という **出力** を観測する hook なので事後
- `onEvent`
  - Store から emit された event という **出力** を観測する hook なので事後

となる。

`onStart` は input/output のどちらにも属さないため、別種の lifecycle hook として扱う。
state type change を見たい場合は、`onState` の中で `prevState::class != state::class` を見れば足りるので、専用 hook は持たない。

### `PluginScope`

`PluginScope` 自体は全 hook で使えるようにする。
ただし、hook 本体では観測に寄せ、追加の権限は `launch {}` の中に閉じ込める。

scope に入れるのは次の最小セットで十分である。

- `launch { ... }`
  - Store-scoped な background work を開始する
- `launch {}` 内の `LaunchScope.currentState`
  - 遅れて動く処理が最新の committed state snapshot を読む
- `launch {}` 内の `LaunchScope.dispatch(action)`
  - background work から補助的な action を enqueue する

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
            dispatch(...)
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

### execution policy は持つ

`Plugin` には `PluginExecutionPolicy` を持たせる。
default は `Concurrent` とし、必要なときだけ `InRegistrationOrder` を選べるようにする。

この方針の理由は、`Plugin` も `Middleware` と同じく、複数登録されても互いに独立した外側の拡張として作られることが多いためである。
特に現在の `Plugin` は観測ベースであり、

- `onAction` は入力 hook
- `onState` / `onEvent` は出力 hook

という整理になっている。
この性質なら、plugin は原則として順序非依存で書けるはずであり、default を `Concurrent` にする考え方と相性がよい。

一方で、移行期の互換性や、特定の setup で順序を意識したいケースまで完全に否定する必要もない。
そのため、escape hatch として `InRegistrationOrder` を残す。

ただし、`InRegistrationOrder` は通常系ではなく例外的な選択肢として扱う。
reusable な plugin は、基本的に `Concurrent` 前提でも安全に動くことを目指す。

また、plugin 内の重い仕事や long-running work は、hook 自体の execution policy に頼るより `scope.launch { ... }` に逃がす方が素直である。
そのため execution policy が制御するのは、あくまで **hook 呼び出し自体の順序** に限られる。
background work の完了順や、そこで起こした `dispatch()` の interleave までは保証しない。

`Middleware` の default を並行実行にする考え方は、「middleware 同士の順序依存を前提にしない」ためのものである（[Middleware 実行ポリシーは並行を標準にする](../adr/2026-04-23-middleware-execution-policy.md)）。
`Plugin` でも同じく、「plugin はまず順序非依存であるべき」と考えて `Concurrent` を標準にする。

もし将来、利用者が本当に欲しいのが「この plugin を先に走らせたい」「この plugin 群は後段で見たい」という制御であるなら、`Concurrent` / `InRegistrationOrder` の二択より、priority や phase のような明示的な概念の方が本質に近い。
そのため execution policy を導入しても、将来の順序制御までこれで十分だとはみなさない。

## 未解決事項

- `Plugin` を今後 `Middleware` の代替へ寄せていくか、並行して残し続けるかは未決定。
- README やサンプルで、plugin の long-running work と cleanup を `launch { try/finally }` ベースでどこまで明示するかは未決定。
- plugin の execution policy を将来も `Concurrent` / `InRegistrationOrder` の二択に留めるか、priority / phase のような別概念を追加するかは未決定。
- 将来、Store lifetime の終了理由そのものを観測したい要件が出た場合、`onClose` ではなく root `Job` completion ベースの別 hook を検討する余地はある。

## 関連

- [Middleware 実行ポリシーは並行を標準にする](../adr/2026-04-23-middleware-execution-policy.md)
