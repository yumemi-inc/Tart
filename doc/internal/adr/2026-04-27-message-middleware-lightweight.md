# MessageMiddleware は簡易な built-in に留める

- 更新日: 2026-04-30
- 関連: [Event 用 MutableSharedFlow の設定方針](./2026-04-23-event-sharedflow-policy.md)

## 背景

`tart-message` の `MessageMiddleware` は、Store 間で簡易な message をやり取りするための built-in である。
現在の実装は、process 内で共有される `MessageHub` と、`replay = 0` の `MutableSharedFlow` を前提にしている。

このため、少なくとも次の 2 点は性質として残る。

- `MessageHub` は global なので、複数 Store や複数 feature が同じ bus を共有する
- receiver Store が start する前に送られた message は保持されず、後から再配送されない

ここで検討したのは、これらの課題を `MessageMiddleware` 自体で吸収するべきかどうかである。
たとえば scope 付き channel、receiver 単位の分離、message retention、購読開始同期、明示的な配送 policy の追加などを導入すれば、上記の課題をいくらか緩和できる。

しかし、その方向に進むと `MessageMiddleware` は「簡単な built-in message bridge」ではなく、Store 間連携のための汎用メッセージ基盤に近づく。
それは API 表面積と責務を大きくし、用途ごとの前提差を framework 側に背負い込みやすい。

## 決定

`MessageMiddleware` は簡易な built-in に留める。

そのため、次の課題は `MessageMiddleware` 自体ではケアしない。

- global bus であることによる Store 間の分離不足
- receiver Store の start 前に送られた message が保持されないこと

これらの性質が困るユースケースでは、`MessageMiddleware` を拡張して吸収するのではなく、利用側で別の Store 間連携手段を設計する。
候補は次のようなものを想定する。

- `Middleware` を使って外部 stream や callback bridge を購読し、各 Store に必要な action を `dispatch()` する
- 複数 Store が共有する repository や data source を用意し、message ではなく共有状態や stream を介して連携する
- domain ごとに scope、lifecycle、replay、buffering を明示した専用の連携機構を別途用意する

## 補足

- `MessageMiddleware` は「軽量で、すぐ使える built-in」であることを優先する。強い配送保証や分離保証まで標準搭載する対象とはみなさない。
- Store 間連携の要求は、feature 間通知、shared session、background sync、cross-screen coordination などで意味合いが大きく異なる。これらを 1 つの built-in message bus で汎用的に解決しようとすると、かえって前提が曖昧になりやすい。
- 強い保証が必要な場合は、利用側が「誰と誰を、どの寿命で、どの再送 policy でつなぐか」を明示した専用設計を持つほうが自然である。
- 今後 `tart-message` の説明を補う場合も、方向性は「制約の明文化」を優先し、汎用メッセージ基盤への拡張は前提にしない。
