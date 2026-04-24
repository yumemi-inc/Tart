# Event 用 MutableSharedFlow の設定方針

- 更新日: 2026-04-23
- 関連: 

## 背景

`Store.event` は UI 向けの one-shot event を流す用途で使っている。現在は `MutableSharedFlow()` をそのまま使っており、実質的には `replay = 0`、`extraBufferCapacity = 0`、`onBufferOverflow = BufferOverflow.SUSPEND` になっている。

ここで検討したのは次の 3 点。

- `replay` を増やすべきか、または明示的に設定しておくべきか
- `buffer` や `overflow` を内部で明示的に設定しておくべきか
- それらの挙動を利用者が policy として選べるようにするべきか

## 決定

現時点では、`Store.event` と `MessageHub` の `MutableSharedFlow` について、`replay`、`buffer`、`overflow` は追加で設定しない。

あわせて、`SharedFlow` の生の設定値を利用者に公開して選ばせる API も追加しない。

採用する前提は次のとおり。

- `replay = 0` を維持する
- `extraBufferCapacity = 0` を維持する
- `onBufferOverflow` は明示しない

## 補足

- `replay = 0` は、後から購読を始めた側に過去イベントを再配送しないために適している。UI event の再配信は、画面の再生成や再購読のたびに navigation、toast、snackbar などの one-shot event が再発火する原因になりやすい。
- `replay` を 1 以上にする案も検討対象だったが、`Store.event` の意味を「その場で購読していた側に届く通知」から「直近イベントの再通知があり得る通知」に変えてしまうため採用しない。
- `extraBufferCapacity` は「すでに購読中だが遅い collector がいるときに、producer を少し先行させる」ための設定であり、「購読前のイベントを保持する」ための設定ではない。
- `onBufferOverflow` は buffer を持たせたときにだけ実質的な意味を持つ。`DROP_OLDEST` や `DROP_LATEST` はイベントを黙って捨てる方針なので、汎用の event delivery policy としては強すぎる。
- 現状の `emit` が suspend する挙動は、遅い collector がいるときに backpressure をそのまま受けるが、そのぶんイベントを静かに落とさない。`Store.event` の既定動作としてはこちらを優先する。
- 設定値をそのまま公開すると、利用者に `SharedFlow` 内部仕様の理解を要求しやすく、API 表面積のわりに得られる一貫した意味づけが弱い。特に `replay` は UI event の再配送と密接に結びつくため、生の数値を公開するより高水準の意味で設計すべき項目である。
- 将来、実運用で「遅い event handler により Store 側の処理が詰まる」問題が確認された場合は、まず内部実装として小さい `extraBufferCapacity` を追加する案を再検討する。その場合でも、最初の候補は `SUSPEND` を維持したままの小容量 buffer とする。
- 可読性のために `MutableSharedFlow(replay = 0, extraBufferCapacity = 0)` のように明示する変更はあり得るが、これは挙動変更ではなく意図の明文化として扱う。
- 実利用で event collector の遅延が問題になる具体例が出た場合にのみ、内部 buffer の導入を再評価する。
