# `ViewStore.render` / `handle` の PascalCase 置き換えは採用しない

- 更新日: 2026-04-30

## 背景

`ViewStore.render` / `ViewStore.handle` は `@Composable` であり、Compose の naming guideline にそのまま寄せるなら、`Unit` を返す public composable として PascalCase の名前にしたくなる。

このため、次の 2 方向を検討した。

- トップレベル関数として `StateContent(viewStore) {}` / `EventHandler(viewStore) {}`
- `ViewStore` のメンバ関数として `viewStore.StateContent {}` / `viewStore.EventHandler {}`

ただし、どちらも現在の `viewStore.render {}` / `viewStore.handle {}` が持つ DSL としての自然さを崩す懸念があった。

## 決定

`ViewStore.render` / `ViewStore.handle` を PascalCase の別 API に置き換える案は採用しない。

現時点では、既存の lowerCamelCase API を維持する。

- `viewStore.render<...> { ... }`
- `viewStore.handle<...> { ... }`

## 補足

- トップレベル関数案は、型引数の見た目が不格好になりやすい。`StateContent<MainState>(viewStore)` のように素直に書けないことがあり、`_` を含む型引数補完や、追加の引数設計を考えないと呼び出しが整いにくい。
- `viewStore.StateContent {}` / `viewStore.EventHandler {}` は Compose の naming guideline には寄せやすいが、明示 receiver を持つメンバ呼び出しとしては不自然に見える。`viewStore.Some()` の PascalCase は、トップレベル composable や暗黙 receiver DSL とは違い、型名やプロパティ名のような見え方になりやすい。
- そのため今回は「Compose guideline への整合」より、「`ViewStore` DSL としての自然さ」を優先する。
- 既存 API には `@Suppress("ComposableNaming")` が必要だが、このコストは上記の不自然さを受け入れるより小さいと判断する。
- 将来、トップレベルでもメンバでもない、より自然な API 形が見つかった場合はあらためて検討してよい。
