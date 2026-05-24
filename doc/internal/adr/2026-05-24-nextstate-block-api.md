# State 遷移指定 API は `nextState {}` を正規系にする

- 更新日: 2026-05-24

## 背景

2026-04-30 時点では、State 遷移指定 API として `nextState(state)` と `nextStateBy { ... }` の 2 本を維持する判断をしていた。

その後の利用と実装整理を踏まえると、両者はどちらも「handler の結果として採用する next state を 1 つ登録する」という同じ契約を表している。
一方で、public surface が 2 本ある事で、README、KDoc、test、レビュー、会話のどれでも表記が混ざりやすい。

また、Store 実装の本質は「最終的に採用する next state を 1 つ保持する」事であり、実装上の primitive も 1 つで十分である。
このため、public API も 1 つの形に寄せ、その上で既存利用者向けには source compatibility を残す方が自然である。

現在の意味論は次の通りである。

- `nextState { ... }` を呼んでも、その場で `state` が更新されるわけではない
- block の最後の式の値が next state として使われる
- 同一 handler 内で複数回 next state を登録した場合は、最後に登録された値が採用される

## 決定

State 遷移指定 API の正規系は `nextState { ... }` とする。

- README / KDoc / test では `nextState { ... }` を primary form として扱う
- `nextState(state)` と `nextStateBy { ... }` は互換性のため残すが、deprecated alias とする
- core の実装は `nextState(block)` だけを primitive とし、legacy API の委譲は `StoreScope` の default 実装で持つ

block の基本形は次の通りとする。

```kt
nextState { AppState.Loading }

nextState {
    val updated = state.items.map { item ->
        if (item.id == action.id) item.copy(done = true) else item
    }
    state.copy(items = updated)
}
```

block に state receiver や `it` のような引数は渡さない。
現在の state を参照したい場合は、scope が持つ `state` をそのまま使う。

## 補足

- 単純な遷移も複雑な遷移も `nextState { ... }` の 1 形に寄せる事で、public surface を絞れる
- `nextState(state)` は 1 行で素直に読める利点があったが、single-expression block でも同じ内容を十分自然に書ける
- deprecated alias には `ReplaceWith` を付け、既存 call site を段階的に移行しやすくする
- `setState(...)` や `updateState { ... }` のような別名は引き続き採用しない。意味論は「state を即時更新する」のではなく、「handler の結果として next state を 1 つ選ぶ」である

## 関連

- [State 遷移指定 API は `nextState()` と `nextStateBy {}` の 2 本を維持する](./2026-04-30-next-state-dual-api.md)
