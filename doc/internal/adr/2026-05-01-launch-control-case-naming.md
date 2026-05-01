# `LaunchControl` API のデザイン

- 更新日: 2026-05-01

## 背景

`LaunchControl` は `action { launch { ... } }` に対して、tracked lane 上で launched job をどう調停するかを表す API である。

この control は、dispatch 自体を捨てるわけではないが、dispatch をきっかけに始まる launched work を開始しない、あるいは前回の launched work を取り消すことがある。
そのため便利な一方で、意図せず使うと「一見すると action が無視されたように見える」振る舞いにつながりうる。

利用者が混乱なく利用できる API であることが求められる。

## 決定

`LaunchControl` は、次の case を持つ API として公開する。

- `LaunchControl.Concurrent` (デフォルト。通常は省略される。)
- `LaunchControl.CancelPrevious`
- `LaunchControl.DropIfRunning`

canonical な書き方は、`launch(control = LaunchControl.CancelPrevious(...)) { ... }` および `launch(control = LaunchControl.DropIfRunning(...)) { ... }` とする。

## 補足

- `CancelPrevious` は「前回の tracked launch を止めて次を始める」、`DropIfRunning` は「tracked launch が動作中なら新しい launch を始めない」という挙動が call site から直接読める。
- `launchCancelPrevious()` / `launchDropIfRunning()` のような API 群へ置き換えも検討したが、利用者が覚えるべき `launchXxx` 構文が増えるので採用しない。
- `launchCancelPrevious()` / `launchDropIfRunning()` を置き換えでなく alias として追加することも検討したが、同じ制御に複数の入口を用意すると、README、レビュー、会話、検索のどれでも表記が混ざり、認知負荷が上がる。そのため public surface は 1 つの書き方に絞る。
- `launch(control = LaunchControl.CancelPrevious(searchLane)) { ... }` のような少し長めの表記でも、コード中で視認しやすい方が、コードレビュー中に見落としづらい。
