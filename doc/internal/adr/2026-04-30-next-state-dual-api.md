# State 遷移指定 API は `nextState()` と `nextStateBy {}` の 2 本を維持する

- 更新日: 2026-04-30
- 関連: [Tart の設計原則](../design/2026-04-23-design-principles.md)

## 背景

Store DSL では、handler の結果として採用する次の state を指定する API をどう表現するかを整理したい。

検討対象は主に次の点である。

- `nextState(state)` と `nextStateBy { ... }` の 2 本を維持するか、1 本に寄せるか
- `nextStateBy {}` の block に state receiver を渡すか
- `nextStateBy {}` の block に `it` などの引数を渡すか
- `nextState` 系ではなく `setState` / `updateState` のような名前へ寄せるか
- `newState = ...` や `state.update { ... }` のような書き方へ寄せるか

この判断では、呼び出し側の読みやすさだけでなく、実際の意味論との整合も重要になる。

現在の Store 実装は、handler の途中で state を逐次更新しているわけではない。
各 handler は内部で「最終的に採用する next state」を 1 つ保持し、handler 終了時にその値を採用する。
そのため、`nextState(...)` や `nextStateBy { ... }` を同じ handler 内で複数回呼んだ場合も、途中の state が順に反映されるのではなく、最後に指定された値が採用される。

この意味論を踏まえると、即時反映や累積更新を強く連想させる名前や block 形は避けた方がよい。

## 決定

State 遷移指定 API は、引き続き次の 2 本を維持する。

- `nextState(state)`
- `nextStateBy { ... }`

それぞれの役割は次のとおりとする。

- `nextState(state)` は、すでに次の state 値が手元にある場合や、1 行で素直に書ける遷移に使う。
- `nextStateBy { ... }` は、中間変数や分岐を書きながら次の state を組み立てる場合に使う。

`nextStateBy {}` の block には state receiver を渡さない。
また、`it` のような引数も基本形にはしない。
block 内で現在の state を参照したい場合は、scope が持つ `state` をそのまま使う。

```kt
nextState(AppState.Loading)

nextStateBy {
    val updated = state.items.map { item ->
        if (item.id == action.id) item.copy(done = true) else item
    }
    state.copy(items = updated)
}
```

`nextState { ... }` のような block 版への統一は行わない。
また、`setState(...)` や `updateState { ... }` への改名も行わない。
`newState = ...` や `state.update { ... }` のような別表現も採用しない。

複数回の state 指定が起きた場合の挙動は、現状どおり後勝ちとする。
つまり、同一 handler 内で複数回 `nextState(...)` / `nextStateBy { ... }` を呼んだ場合は、最後に指定された値だけが採用される。

## 補足

- `nextStateBy {}` の `by` は、「ここは別の DSL scope ではなく、その場で次の state を Kotlin のコードで組み立てる場所」という意図を表す。`nextState {}` だと `state {}` や `action {}` と同じ見た目になり、設定用 DSL block のように見えやすい。
- state receiver を渡す案は、`copy(...)` を短く書ける利点はあるが、`action`、`error`、`event()`、`clearPendingActions()` など外側 scope のメンバとの境界が曖昧になりやすい。Tart のように scope が多い DSL では、暗黙の `this` を増やさない方が読みやすい。
- `it` や明示引数を使う案もあるが、`state.copy(...)` の方が「何を元に次の state を作っているか」が直接読み取りやすい。block の役割は計算であり、引数経由の短縮を優先しない。
- `setState(...)` は即時反映に、`updateState { ... }` は逐次的または累積的な更新に読まれやすい。しかし実際の意味論は「handler の結果として採用する next state を 1 つ選ぶ」であり、これらの名前は実態より強い期待を生みやすい。
- `newState = ...` は「普通の Kotlin の代入」に見える利点はあるが、公開 DSL としては mutable な結果 slot をそのまま見せることになる。`newState` を途中で読めるのか、複数回代入した場合はどうなるのか、未代入は何を意味するのかといった契約が API ににじみやすいため採用しない。
- `state.update { ... }` は、現在の `state` をその場で mutate する、あるいは `MutableStateFlow.update` のように現在値へ順次更新を積む API に見えやすい。しかし Tart の `state` は scope が持つ現在 state の snapshot 値であり、意味論は「state 自体を更新する」のではなく「handler の結果として next state を選ぶ」である。このため `state.update { ... }` は実際の挙動よりも可変オブジェクト操作に近く読まれやすく、採用しない。
- 後勝ちは積極的に活用させたい機能ではないが、分岐や早期 return を含む handler で最終結果を自然に決められる fallback semantics としては妥当である。必要なら README / KDoc / test でこの挙動を明記する。
- `先勝ち` も採用しない。通常の Kotlin コードでは、上から順に読んで後ろの代入や指定が最終結果になると受け取られやすい。`nextState(A)` の後に `nextState(B)` が書かれていても `A` が採用される設計は、後ろの記述が見えているのに効かないため、`後勝ち` よりも驚きが大きい。もし複数指定をより厳しく扱いたいなら、無言の `先勝ち` にするより、重複指定をエラーとして検知する方向の方が自然である。
- `state<S2>` / `action<A2>` の handler 解決は registration order による先勝ちである一方、選ばれた handler の中での `nextState(...)` / `nextStateBy { ... }` は後勝ちになる。この組み合わせは初見では少し引っかかりうるが、両者は別レイヤーの挙動である。前者は「どの rule を採用するか」という routing であり、後者は「採用された rule の結果を何にするか」という result selection である。同じ勝ち方に無理に揃えるより、routing は先勝ち、結果指定は後勝ちとして、それぞれの層で自然な振る舞いを採る方が分かりやすい。
