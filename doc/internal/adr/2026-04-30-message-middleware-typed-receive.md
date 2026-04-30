# `receiveMessages<MainMessage>` のような型絞り込み API は追加しない

- 更新日: 2026-04-30
- 関連: [MessageMiddleware は簡易な built-in に留める](./2026-04-27-message-middleware-lightweight.md)

## 背景

`tart-message` の `receiveMessages()` に、次のような message 型指定を追加する案を検討した。

- `receiveMessages<MainMessage> { ... }`
- `receiveMessages { message: MainMessage -> ... }`

狙いは、`Message` 全体を受けて `when` や `is` で振り分ける代わりに、受信側で任意の message 型だけを自然に購読できるようにすることである。

ただし `tart-message` は別モジュールであり、`StoreBuilder` に専用 DSL を追加して `Store { receiveMessages<Hoge> { ... } }` のような形に寄せることはできない。
そのため、検討対象になるのは top-level の middleware factory である `receiveMessages()` に型指定 API を足す方向になる。

## 決定

`receiveMessages<MainMessage>` や `receiveMessages { message: MainMessage -> ... }` のような型絞り込み API は追加しない。

現時点では、既存の `receiveMessages { message -> ... }` を維持する。

## 補足

- `receiveMessages()` は `Middleware<S, A, E>` を返す generic factory であり、message 型だけを追加すると `M` と `S / A / E` の複数型引数を同時に扱うことになる。
- Kotlin では「先頭の型引数だけ明示して残りを自然に省略する」形が取りづらく、`receiveMessages<MainMessage>` の見た目を素直に成立させにくい。API 形によっては `_` を含む型引数補完に寄りやすく、呼び出しが不格好になりやすい。
- `receiveMessages { message: MainMessage -> ... }` の形なら型引数の見た目は避けやすいが、今度は「lambda 引数型で購読対象を切り替える API」になる。現在の `receiveMessages { message -> ... }` よりも契約が見えづらく、単なる引数注釈以上の意味を持つ書き方になるため、API としてやや回りくどい。
- `StoreBuilder` 側に member DSL を生やせれば別の見せ方もありうるが、`tart-message` が別モジュールである以上、その方向は採れない。
- そのため今回は、「型で絞れること」よりも「top-level factory として無理のない呼び出し形」を優先し、既存 API を維持する。
- 必要な絞り込みは、引き続き `receiveMessages { message -> when (message) { ... } }` や `if (message is MainMessage) { ... }` で表現する。
