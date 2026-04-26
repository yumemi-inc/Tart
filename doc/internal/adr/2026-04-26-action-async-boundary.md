# `action` の async 境界は明示のまま維持する

- 更新日: 2026-04-26
- 関連: [Tart の設計原則](../design/2026-04-23-design-principles.md), [非 `launch` 処理には cancellation API を入れない](./2026-04-26-non-launch-cancellation.md)

## 背景

`action {}` は現状、Store の直列 pipeline 上で実行される handler であり、必要に応じて `action { launch { ... } }` により state scope にぶら下がる非同期処理を開始できる。

このとき、次の 2 案を検討した。

- `action {}` を default で `action { launch { ... } }` 相当にし、利用者の `dispatch()` を常に並行に処理しやすくする案
- `action {}` と `action { launch { ... } }` の二段構えは維持しつつ、`action {}` 直下では外部 I/O や長い suspend を書かせない方向へ寄せる案

前者は Store を塞ぎにくくするが、後者は async 境界を明示したまま保ちやすい。
一方で、後者を型で強制するには既存 API の breaking change が必要になる可能性がある。

## 決定

`action` の async 境界は、当面、明示の `launch {}` によって表す形を維持する。

- `action {}` を default で async / parallel handler にはしない
- `action {}` と `action { launch { ... } }` の二段構えは維持する
- 通常の `action {}` は、短く終わる直列・原子的な store work として扱う
- 外部 I/O、長い待ち時間、cancellation が必要な仕事、state に所有させたい継続処理は `launch {}` に出す前提で整理する
- ただし現時点では、`action {}` を non-suspend に変える breaking change は入れず、まずは設計指針とドキュメントでこの運用を明確にする

## 補足

- `action {}` を default async にすると、state transition の順序、`dispatchAndWait()` の完了単位、middleware の前後関係、`PendingActionPolicy` の意味が読みづらくなる。
- Tart では「action は処理開始のきっかけであり、継続中の仕事の所有者は state」という整理を取っている。そのため、継続する仕事の入口を `launch {}` として明示する方が設計全体と整合する。
- 一方で、`action {}` 直下で長い suspend や外部 I/O を許すと、Store 全体を塞ぎやすい。この問題は実際に起こりうるが、解決策として default async 化を採るのは副作用が大きい。
- `action {}` を non-suspend にする案は思想としては筋がよいが、現状の `event()` を含む DSL 形状と衝突しやすく、導入コストに対して runtime 上の利益は限定的である。
- `launch {}` を安易に増やすと、複数 async job 間の整合性、古い結果の採用防止、event 発火順の読みやすさなどを利用者が管理する負担が増える。したがって、`launch {}` は必要な箇所でだけ明示的に使う escape hatch として残す。
