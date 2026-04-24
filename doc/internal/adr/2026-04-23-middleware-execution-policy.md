# Middleware 実行ポリシーは並行を標準にする

- 更新日: 2026-04-23
- 関連: 

## 背景

Tart では複数の `Middleware` を登録できる。
このとき、各 lifecycle hook を registration order で直列に流すか、並行に流すかは仕様として明確にしておきたい。

`Middleware` は logging、message bridge、監視、補助的な dispatch など、Store の本体ロジックから関心事を分離するための拡張ポイントとして使う。
そのため、複数の `Middleware` が互いの副作用や実行順に依存し始めると、設計意図から外れやすい。

## 決定

`MiddlewareExecutionPolicy` のデフォルトは `CONCURRENT` とする。

複数の `Middleware` を使う場合でも、それぞれは原則として互いに疎結合であるべきで、他の `Middleware` の完了や副作用を前提に設計しない、という考え方を基本にする。
ただし `IN_REGISTRATION_ORDER` も正式な option として残す。

## 補足

- 並行実行を標準にする理由は performance だけではない。より重要なのは、`Middleware` 同士が関与し合う設計を後押ししないことにある。
- もし `Middleware A` が `Middleware B` の結果を前提にしないと正しく動かないなら、それらは別 middleware ではなく、1 つの責務としてまとめるか、Store 本体の state/action 設計で表現したほうがよい。
- registration order を強い契約として前提にすると、`middleware(...)` の並び順が実質的な仕様になり、変更耐性が落ちやすい。
- 並行実行であれば、「各 `Middleware` は独立した観測者・拡張として振る舞う」という期待に揃えやすい。
- Store は各 hook で全 middleware の完了を待つ。そのため、`CONCURRENT` でも fire-and-forget にはならず、完了待ちは維持される。
- 一部の統合事情、移行事情、あるいは処理順を明示したいケースでは直列実行も自然な選択になり得るため、`IN_REGISTRATION_ORDER` も選択肢として残す。
