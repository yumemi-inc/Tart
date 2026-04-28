# Store DSL に top-level `onStart` は追加しない

- 更新日: 2026-04-28
- 関連: [Tart の設計原則](../design/2026-04-23-design-principles.md), [Middleware には直接 state 更新 API を入れない](./2026-04-26-middleware-dispatch-only.md), [Store の開始タイミング policy 案](../notes/2026-04-23-store-start-policy.md)

## 背景

`enter { launch { ... } }` は state scope にぶら下がるため、state を抜けると自動で cancel される。
そのため、state に関わらず外部 `Flow` や callback bridge を継続購読したい用途では、`state {}` と同列の top-level `onStart {}` を `Store {}` DSL に追加する案を検討した。

イメージは次のようなものである。

```kt
val store = Store<AppState, AppAction, Nothing> {
    onStart {
        launch {
            repository.observe().collect { value ->
                dispatch(AppAction.ExternalValueArrived(value))
            }
        }
    }

    state<AppState.Ready> {
        action<AppAction.ExternalValueArrived> {
            nextState(state.copy(value = action.value))
        }
    }
}
```

この案は、Store-scoped な購読を DSL 上で見つけやすくする利点がある。
一方で、同じことは現状でも `Middleware(onStart = { ... })` で表現できる。

```kt
val store = Store<AppState, AppAction, Nothing> {
    middleware(
        Middleware(
            onStart = {
                launch {
                    repository.observe().collect { value ->
                        dispatch(AppAction.ExternalValueArrived(value))
                    }
                }
            },
        ),
    )

    state<AppState.Ready> {
        action<AppAction.ExternalValueArrived> {
            nextState(state.copy(value = action.value))
        }
    }
}
```

一方で、もし top-level `onStart` に `nextState()` や `transaction {}` のような state 更新 capability まで持たせるなら、これはもはや middleware では代替できない。
その場合は「購読の書き味をよくする sugar」ではなく、Store-scoped かつ state write 可能な新しい拡張点を追加する話になる。

つまり、ここで判断したいのは「Store-scoped な購読が必要かどうか」ではなく、「それを表すために core DSL へ専用の top-level `onStart` を追加するべきかどうか」である。

## 決定

現時点では、`Store {}` DSL に専用の top-level `onStart {}` は追加しない。

- state に依存しない購読や callback bridge は、引き続き `Middleware(onStart = { ... })` で表現する。
- `Store { onStart { ... } }` は現状の `Middleware(onStart = { ... })` で代替できるため、専用 DSL を増やすだけの必要性が不足しているとみなす。
- state 更新 capability を持つ top-level `onStart` までは導入しない。その種の API は middleware 代替ではなく、別の責務と制約を持つ新機能として扱うべきである。
- 将来見直すのは、「middleware で代替可能か」ではなく、「middleware では表現しづらい具体的な問題が継続して出るか」で判断する。

## 補足

- この判断は、Store-scoped な購読自体を否定するものではない。現状の拡張点として `Middleware` が十分機能しているため、専用 DSL を追加しないだけである。
- top-level `onStart` を追加するとしても、実装上は `middleware(Middleware(onStart = ...))` を足す sugar として扱うのが自然である。
- その場合、`clearMiddlewares()` や `replaceMiddlewares(...)` の対象に含めるのか、`onStart` だけ例外扱いするのかを説明する必要がある。後者にすると、見た目は軽い sugar でも挙動説明だけ重くなる。
- 逆に、top-level `onStart` から直接 state を更新できるようにすると、これは `Middleware(onStart = { ... })` の sugar ではなくなる。既存の「middleware は state を直接更新しない」という整理ともずれるため、追加するなら別 ADR で扱う方がよい。
- 専用の `onStart` を追加しても、Store の開始タイミングそのものは変わらない。現状の `onStart` は Store start 時に走るのであり、Store 作成直後に eager 実行されるわけではない。この論点は start policy の問題として別に扱う。
- discoverability やサンプル不足が主な課題であれば、まずは README や test の例を増やして補う方が API 追加より軽い。
