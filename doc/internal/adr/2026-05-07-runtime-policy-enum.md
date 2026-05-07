# Runtime policy API は enum を維持する

- 更新日: 2026-05-07

## 背景

`PendingActionPolicy` や `PluginExecutionPolicy` のような runtime policy は、現在 `enum` として公開している。

これらを `sealed interface` に寄せておけば、将来の拡張性が上がるのではないか、という論点がある。
一方で、Tart の public policy API は、利用者に独自実装を許す戦略 interface ではなく、Store が解釈する少数の高水準 mode を表すものとして設計している。

## 決定

`PendingActionPolicy`、`PluginExecutionPolicy`、および将来追加する同種の runtime policy は、原則として `enum` を維持する。

`sealed interface` を使うのは、`LaunchControl` のように case ごとに payload を持たせたいとき、または variant の形が単純な named mode を超えるときに限る。

runtime policy 間で表現形式を無理に統一することはしない。
少数の固定 mode を選ぶ policy は `enum`、payload 付き variant や非対称な入力形を持つ policy は `sealed interface` とし、概念の形に合わせて選ぶ。

## 補足

- `enum` は「閉じた少数の mode」を表す型として意味が直感的であり、call site からも用途が読み取りやすい。
- `sealed interface` にしても、外部利用者が独自 policy を実装できるようになるわけではない。Tart では policy を library 側が意味付けするため、単なる named mode の拡張性は `enum` でも足りる。
- `enum` から `sealed interface` への変更は public API / ABI の変更であり、互換性コストがある。
- `tart-core` は JVM target を持ち、Java compilation support も有効にしているため、Java から扱いやすい `enum` の利点も捨てない。
- 将来、policy に `KeepUntil(...)` のような payload 付き variant や、case ごとに異なる入力形が必要になった場合は、その時点で `sealed interface` 化または別型の導入を再検討する。
