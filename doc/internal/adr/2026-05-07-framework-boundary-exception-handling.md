# `error {}` に流さない framework boundary の例外処理は当面現状維持とする

- 更新日: 2026-05-07

## 背景

既存の判断として、`error {}` DSL は Store の通常 runtime path における `Exception` の回復経路に限定している。
そのため、plugin hook、observer callback、`StateSaver.save()` / `restore()` など、framework boundary で起きた例外は `error {}` に再投入しない。

この整理は、state machine の回復責務と framework 側の失敗を分離するうえでは自然である。
一方で、plugin や saver の失敗の中には、state transition 自体の整合性を必ずしも壊さず、レポートしつつ続行できそうなものもある。

ただし、この種の例外を一律に「続行可能」とみなすのは粗すぎる。
起動前後の `restore()` や `Plugin.onStart()` のように、失敗時に Store の初期化状態へ直接影響するものもあり、同じ扱いにはできない。
また、`error {}` に流さない例外は現在 `exceptionHandler()` 側で受けるため、設定次第では利用者が failure を見落としやすい、という別の論点もある。

このため、今の時点で runtime 挙動や handler 境界を拡張するかどうかを決めておく必要がある。

## 決定

framework boundary の例外処理は、当面は現状コードを維持する。

- plugin、observer、persistence など `error {}` に流さない例外を、今すぐ一律に「report して続行」へ寄せる変更は採用しない
- `exceptionHandler()` と別に、system-side の例外専用 handler を直ちに追加する変更も採用しない
- 現時点では、「Store DSL 内の recovery path」と「framework boundary の last-resort path」を既存どおり分けたままにする

ただし、将来の拡張候補として次は残す。

- 個々の失敗点ごとに、Store 整合性への影響と observability の要求を見極めたうえで、例外発生後も続行できる箇所だけを限定的に増やす
- `error {}` に流さない system-side の例外について、現行の `exceptionHandler()` とは別の handler を導入し、利用者が business error と framework error を分けて扱えるようにする

これらは方向性としては保持するが、具体的な API や runtime policy は、実例となるユースケースや運用上の不足が揃ってから判断する。

## 補足

- 今回の判断は、「現行実装は完全であり、将来も変えない」という意味ではない。変更の粒度を例外の発生源ごとに分解せず、一括で広げることを避けるための保留である。
- 特に `Plugin.onStart()` と `StateSaver.restore()` は、通常の `onAction` / `onState` / `save()` と違って初期化可否に関わるため、将来見直すとしても別の扱いになる可能性が高い。
- observability の不足と continuation policy の不足は別問題である。将来、継続可否を変えずに reporting だけ分離・強化する案もありうる。

## 関連

- [`error {}` DSL は `Exception` の回復経路に限定する](./2026-05-01-error-dsl-exception-boundary.md)
- [`Plugin` 設計メモ](../notes/2026-05-02-plugin-design.md)
