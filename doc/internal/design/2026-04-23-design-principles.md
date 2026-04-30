# Tart の設計原則

- 更新日: 2026-04-23

## 背景

`PendingActionPolicy` や `MiddlewareExecutionPolicy` のような個別 policy の検討メモは増えてきたが、それだけだと「なぜその方向の仕様になるのか」が後から読み取りにくい。

ここでは、Tart の個別仕様の背景にある設計上の軸を整理する。
この文書は、個別の policy や API の判断理由を、上位の設計原則として明文化するためのものである。

## 方針

Tart の設計上の基本方針は次のとおり。

- Tart は「この action が来たらどうするか」より、「今どの状態で、その状態では何が起こるか」を中心に組み立てる state machine である。
- action は状態遷移や処理開始のきっかけであり、長く生きる仕事の寿命は state が持つ。
- Store の生成と副作用の開始は分ける。Store はまず宣言として作られ、start 後に副作用が走る。
- middleware は Store 本体の pipeline ではなく、外側にある独立した拡張点として扱う。
- overrides で変えてよいのは環境設定であり、状態遷移構造そのものではない。
- 業務上の失敗は state machine の中で扱えるが、fatal な失敗や system failure は外に逃がす。

## 補足

- `state::class` が変わったときに enter/exit、state scope の切り替え、pending action の扱いが効くのは、「値の差分」より「状態相の切り替わり」を重く扱うためである。
- `action { launch { ... } }` が action 単位ではなく state scope にぶら下がるのは、「action はトリガーであり、継続中の仕事の所有者は state である」という整理による。
- Store が lazy start で、start 前でも `currentState` や restore 済み state snapshot を読めるのは、「宣言としての Store」と「副作用が走り始めた Store」を分けるためである。
- middleware の default が並行実行なのは、middleware 同士の順序依存を強い設計前提にしないためである。
- `overrides` が state/action handler を触れず、設定だけを上書きできるのは、「その Store がどんな state machine か」は identity に近く、テストや debug 用に変える対象ではないという線引きによる。
- `error{}` と `exceptionHandler` が分かれていて、`Error` や cancellation を state 遷移の材料にしないのは、業務エラーと実行系の失敗を分けるためである。

## 関連

- [PendingActionPolicy 拡張案の却下](../adr/2026-04-22-pending-action-policy.md)
- [Middleware 実行ポリシーは並行を標準にする](../adr/2026-04-23-middleware-execution-policy.md)
