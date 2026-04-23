# Store の開始タイミング policy 案

- 状態: メモ
- 更新日: 2026-04-23
- 反映状況: 未反映
- 関連: [StoreImpl.kt](../../tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StoreImpl.kt)、[StoreBuilder.kt](../../tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StoreBuilder.kt)、[Store.kt](../../tart-core/src/commonMain/kotlin/io/yumemi/tart/core/Store.kt)、[StoreInternalApi.kt](../../tart-core/src/commonMain/kotlin/io/yumemi/tart/core/StoreInternalApi.kt)、[StoreBaseTest.kt](../../tart-core/src/commonTest/kotlin/io/yumemi/tart/core/StoreBaseTest.kt)、[StoreObserverTest.kt](../../tart-core/src/commonTest/kotlin/io/yumemi/tart/core/StoreObserverTest.kt)、[ViewStore.kt](../../tart-compose/src/commonMain/kotlin/io/yumemi/tart/compose/ViewStore.kt)

## 背景

現状の Store は、最初の `dispatch()` または `state.collect` / `collectState()` をきっかけに起動する。

起動時には `initializeIfNeeded()` が呼ばれ、middleware の `onStart` を含む start 時の処理が実行される。

このため、利用者が `state` を collect しただけでも start に紐づく副作用が走る。
Compose の `rememberViewStore()` は内部で `store.state.collectAsState()` を呼ぶため、画面が Store を購読した時点で Store が起動しうる。

一方で、次の挙動は start 前でも成立している。

- `currentState` は読める
- `stateSaver.restore()` により復元された state も start 前に見える
- `attachObserver()` は start 前にだけ許可される
- `collectEvent()` は現状 start のトリガーではない

この組み合わせを見ると、検討対象は個別の handler 単位ではなく、「Store をいつ start とみなすか」の方が自然である。

## 結論

初期化ポリシーを追加するなら、個別の DSL handler ではなく Store の開始タイミング全体を制御する policy として導入する。

名前は仮に `StoreStartPolicy` とし、まずは次の 3 つを候補にする。

- `ON_FIRST_DISPATCH_OR_STATE_COLLECTION`
  - 現状互換の default
  - 最初の `dispatch()` または `state` の collect で start する
- `ON_FIRST_DISPATCH`
  - `dispatch()` でのみ start する
  - `state` の collect は start のトリガーにしない
- `MANUAL`
  - 自動 start しない
  - 利用者が明示的に `start()` を呼んだときだけ start する

設定 API は既存の policy と同じ流儀で `StoreBuilder` / `StoreOverridesBuilder` に追加するのが自然である。

```kt
fun startPolicy(policy: StoreStartPolicy)
```

`MANUAL` を成立させるために、明示的に start する API が別途必要になる。

## 補足

- `PendingActionPolicy` や `MiddlewareExecutionPolicy` と同じく、enum ベースの高水準 policy にした方が API の意味を保ちやすい。`Boolean` や生の trigger 群を公開すると、組み合わせは増えるが利用者から見た意味が弱くなる。
- policy が制御する対象は startup processing 全体とする。ここを分けると start の概念が二重化し、middleware・observer・テストの整合が崩れやすい。
- `ON_FIRST_STATE_COLLECTION` のような collect 専用 policy は v1 では不要。`dispatch()` したのに start しない挙動は直感に反しやすい。
- 未採用候補として `EAGER` も考えられる。これは「`dispatch()` や `state` の collect を待たず、Store 作成直後に start する」という意味になる。v1 では見送るのがよい。`attachObserver()` が start 前のみ許可という現行前提と衝突しやすい。
- `ON_FIRST_DISPATCH` では、`state` を collect しても start しない。その場合でも `StateFlow` としては current snapshot を読めるので、UI が「現在値の監視」と「副作用の開始」を分離しやすくなる。
- `MANUAL` で start 前に `dispatch()` された場合は、暗黙 start や silently ignore ではなく、例外で失敗させる方がバグを早く見つけやすい。

## 未解決事項

- 明示 start API を `Store` interface に載せるか、core 内の extension として追加するかは未決定。interface 追加は fake 実装への影響があり、extension は Tart 実装依存であることをどう見せるかを考える必要がある。
- `collectEvent()` を start trigger に含めるかは未決定。現状は含まれていないが、利用者視点では `event` 監視も start と結び付くと期待される可能性がある。
- `MANUAL` で start 前に `state` を collect した場合の README 上の説明を明確にする必要がある。current snapshot は流れるが、start に紐づく副作用はまだ走らない、という説明になる見込み。
- `rememberViewStore()` 利用時に `ON_FIRST_DISPATCH` や `MANUAL` を選んだときのサンプルを README / Compose 側テストに追加する必要がある。
