# `clearPendingActions()` は store work / transaction に閉じる

- 更新日: 2026-04-27
- 関連: [PendingActionPolicy 拡張案の却下](./2026-04-22-pending-action-policy.md), [非 `launch` 処理には cancellation API を入れない](./2026-04-26-non-launch-cancellation.md), [`action` の async 境界は明示のまま維持する](./2026-04-26-action-async-boundary.md)

## 背景

`clearPendingActions()` は、現在実行中の store work 自体を止める API ではなく、その後ろに待機している dispatch を捨てる API である。

一方で、公開面としては `enter {}`、`action {}`、`exit {}`、`error {}`、および launched coroutine 内の `transaction {}` から呼べる。
このため、次の 2 点を整理しておきたい。

- `clearPendingActions()` をさらに狭い場所に限定すべきか
- `launch {}` 本体からも呼べるようにすべきか

`clearPendingActions()` は queue 制御であり、state-owned な非同期仕事そのものの cancellation とは役割が異なる。
この境界が API surface から読めるかどうかが重要になる。

## 決定

`clearPendingActions()` は、引き続き store の直列 pipeline 上で実行される scope と、そこへ明示的に戻る `transaction {}` からのみ呼べる API として扱う。

- `enter {}`、`action {}`、`exit {}`、`error {}` では公開を維持する
- launched coroutine 内では `transaction {}` からのみ呼べるようにし、`launch {}` 本体には公開しない
- middleware やその他の非 store-work 文脈には広げない

また、利用上の重心は `action {}` と launched coroutine 内の `transaction {}` に置く。
`enter {}`、`exit {}`、`error {}` での利用は escape hatch として許容するが、常用の中心には置かない。

## 補足

- `clearPendingActions()` が意味を持つのは、「いま何が current store work で、その後ろに何が pending か」が直列 pipeline 上で定まっているときである。
- `launch {}` 本体は state に所有される非同期処理であり、store の直列 pipeline そのものではない。ここで queue を直接掃除できるようにすると、遅延や I/O の後の任意の時点で pending action を破棄できてしまい、挙動を追いにくくなる。
- `launch {}` 本体で必要なのは queue 制御よりも、state-owned job の寿命制御である。そちらは `cancelLaunch(key)` のような action-launch cancellation で扱う方が役割分離として自然である。
- launched coroutine から `transaction {}` に入った時点では、処理は再び store の直列 pipeline に戻る。そのため、その瞬間に「この結果を採用するなら、古い pending action は不要」と判断して `clearPendingActions()` を呼ぶのは意味が通る。
- `enter {}`、`exit {}`、`error {}` も技術的には store work であり、そこで pending action を捨てる意味はある。したがって公開面から完全に外す必要まではない。
- ただし、可読性の観点では `action {}` と `transaction {}` の方が「何を確定させた結果として queue を切るのか」を読み取りやすい。README や KDoc では、この利用の重心を明示した方がよい。
