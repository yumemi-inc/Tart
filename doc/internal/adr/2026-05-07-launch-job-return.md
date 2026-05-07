# `launch` から `Job` は返さない

- 更新日: 2026-05-07

## 背景

Tart の `enter { launch { ... } }`、`action { launch { ... } }`、`PluginScope.launch { ... }` は、いずれも内部では coroutine の child `Job` を作っている。

このとき、公開 API でも `launch()` からその `Job` を返し、呼び出し側が `cancel()` や `join()` を直接行えるようにするべきかを検討した。

`Job` を返せば coroutine primitive としては自然に見える一方で、Tart の `launch` は単なる汎用 coroutine 起動ではなく、state scope や store root scope に所有される仕事の開始点として意味づけられている。
ここで判断したいのは、公開 DSL でもそのまま `Job` を露出するべきかどうかである。

## 決定

公開 DSL の `launch()` からは `Job` を返さない。

- `EnterScope.launch` と `ActionScope.launch` は、引き続き state-owned な background work の開始 API として扱う。
- `PluginScope.launch` も、引き続き store-owned な background work の開始 API として扱う。
- launched work の coordination や cancellation は、`ActionScope.cancelLaunch(lane)` や state exit / store close のような高水準の lifecycle に寄せる。
- `Job.cancel()` / `Job.join()` のような低水準操作を、公開 DSL の標準操作にはしない。
- 将来、個別ハンドルが必要なユースケースがたまった場合でも、まずは `Job` そのものではなく、用途を限定した専用 handle を検討する。

## 補足

- Tart では「action は処理開始のきっかけであり、継続中の仕事の所有者は state」という整理を取っている。`ActionScope.launch` が返した `Job` を呼び出し側で保持させると、その仕事を action caller が所有しているように見え、ownership の読み取りがぶれやすい。
- `ActionScope.launch` にはすでに `LaunchControl.CancelPrevious(...)`、`LaunchControl.DropIfRunning(...)`、`cancelLaunch(lane)` があり、tracked launch の coordination は lane 単位の高水準 API として表現している。ここに生の `Job` を追加すると、「lane で止めるべきか」「保持していた job を直接止めるべきか」が二重化する。
- 特に `LaunchControl.DropIfRunning(...)` は、新しい launch 要求が無視される場合がある。このとき `launch()` の戻り値を `Job` にすると、「今回の呼び出しで何が返るのか」を別途決める必要があり、API 意味論が余計に重くなる。
- `EnterScope.launch` の仕事は state exit で自動停止し、`PluginScope.launch` の仕事は store root scope の終了で自動停止する。これらは Tart 側が lifecycle を所有しているため、公開 surface でもその ownership を保った方が自然である。
- launched work 内の recoverable な `Exception` は `error {}` の回復経路に流す設計であり、利用者に見せたい境界は job failure そのものより state machine の recovery path である。`Job` を前面に出すと、失敗モデルも coroutine primitive 寄りに読まれやすくなる。
- ただし、「Store は生かしたまま特定の background work だけを owner が明示停止したい」といった要件が将来増える可能性までは否定しない。その場合は `Job` をそのまま返すより、「何を止めるための handle なのか」が分かる専用型の方が Tart の API surface と整合しやすい。
