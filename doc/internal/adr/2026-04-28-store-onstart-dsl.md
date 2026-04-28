# `Store{}` DSL に state 非依存の `onStart {}` は追加しない

- 更新日: 2026-04-28
- 関連: [Tart の設計原則](../design/2026-04-23-design-principles.md), [Store の開始タイミング policy 案](../notes/2026-04-23-store-start-policy.md), [Middleware には直接 state 更新 API を入れない](./2026-04-26-middleware-dispatch-only.md)

## 背景

現状の `Store{}` DSL では、`enter {}` から `launch {}` を使って外部 resource の `Flow` や callback bridge を購読できる。

ただし、この購読は current state の runtime に属する。
そのため、state が遷移すると、その state に紐づく runtime は終了し、`enter {}` で始めた購読も止まる。

ここで検討したのは、state 遷移に左右されず Store start 時に 1 回だけ動く `onStart {}` を `Store{}` DSL に追加する案である。
これがあれば、state ごとの寿命ではなく Store 全体の寿命に紐づく購読を、`Store{}` DSL 内で直接書ける。

一方で、この `onStart {}` に state 更新を許すべきかどうかは現時点で決めきれない。
`Middleware` に直接 state 更新 API を入れないとした判断と同様に、state machine の外側に見える start hook へ state 書き込み capability を載せると、責務境界が曖昧になりやすい。

逆に、state 更新を許さず、外部入力を受けて action を `dispatch()` するだけの hook として扱うなら、`Middleware(onStart = { ... })` で代替できる。
その場合、専用の `onStart {}` を `Store{}` DSL に増やしても、実質的には middleware の別名を増やすに留まる。

## 決定

現時点では、state 遷移に依存しない `onStart {}` を `Store{}` DSL に追加しない。

- `enter {}` で扱う購読は、引き続き active state の runtime に属するものとして扱う。
- state 遷移に左右されない購読や監視が必要な場合は、`Middleware(onStart = { ... })` を使って外部入力を受け、必要な action を `dispatch()` する。
- state 非依存の start hook に state 更新を許すかどうかが整理できるまでは、`Store{}` DSL に専用 API は増やさない。

## 補足

- 今回見送る主因は、「state 更新を許す hook にするのか」「dispatch 専用 hook にするのか」が未確定なまま API を増やしたくないためである。
- もし将来、代替としても state を更新しない版の `onStart {}` を用意するなら、実装上は `Middleware(onStart = { ... })` のシンタックスシュガーにするのがもっとも簡単である。
- ただしその場合は、`overrides {}` 内の `clearMiddlewares()` や `replaceMiddlewares()` とどう整合させるかを先に決める必要がある。`Store{}` DSL の `onStart {}` が middleware の一部として消えるのか、別枠で残るのかが曖昧だと、override semantics が読みづらくなる。
- もしこの相互作用が不自然になる、または実装上の扱いが複雑になるなら、シンタックスシュガー案は採らず、`StoreImpl` に専用の入口を用意して扱いを明示した方がよい。
