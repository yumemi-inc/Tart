# `Store{}` DSL に Android ライフサイクル hook を導入しない

- 更新日: 2026-05-10

## 背景

Android の `onResume` / `onPause`（または `onStart` / `onStop`）といった Activity / Fragment のライフサイクルを、`Store{}` DSL から直接観測できるようにする案を検討した。

DSL で実現するなら、たとえば次のような形になる。

```kt
state<Hoge> {
    onResume {
        ...
    }
}
```

ここで、すでに別経路として、UI 側または Plugin 側で lifecycle を購読し、`onResume` を action として `dispatch()` する書き方ができる。

```kt
state<Hoge> {
    action<Fuga> {
        ...
    }
}
```

`Fuga` を「`onResume` を表す action」として定義すれば、DSL 内で `onResume {}` と書く場合と、利便性において大差はない。

それにもかかわらず、専用 hook を `Store{}` DSL に置くと、`Store` 自身が Android のライフサイクル source を観測することになる。これは現状の `Store{}` の責務（state、action、event を中心とした state machine）に対しては大きすぎる。

加えて、Tart は Kotlin Multiplatform を前提としたライブラリであり、`Store{}` DSL は Android に限定されない共通 API として設計している。`onResume` / `onPause` のような Android 固有の概念をコア DSL に持ち込むこと自体、ライブラリの位置づけと整合しない。

## 決定

`Store{}` DSL に、Android のライフサイクル（`onResume` / `onPause` / `onStart` / `onStop` など）を直接観測する hook は追加しない。

- ライフサイクルを Store に反映したい場合は、UI 側または Plugin 側で lifecycle を購読し、対応する action を `dispatch()` して、通常の `state<...> { action<...> { ... } }` で処理する。
- `Store{}` DSL からは Android API に依存しない。Android 固有のライフサイクル観測ロジックを Store 本体に持ち込まない。

## 補足

- 専用 hook を入れた場合と、action として `dispatch()` する場合とで、利用側の表現力に大きな差はない。よって、わざわざライフサイクルを監視する仕組みを `Store{}` DSL 内部に抱える価値が薄い。
- ライフサイクル監視そのものは、Activity / Fragment や Plugin など、外部の lifecycle source を知っている層に閉じる方が自然である。`Store{}` DSL までその責務を引き上げると、Store が「状態遷移の主体」だけでなく「ライフサイクル observer」も兼ねることになり、責務境界が広がりすぎる。
- Tart はマルチプラットフォームを前提としたライブラリであり、コア DSL に特定プラットフォーム固有の概念（ここでは Android の `onResume` / `onPause` 等）を持ち込むと、ライブラリ全体の中立性が崩れる。プラットフォーム固有の事情は、利用側または Plugin / Middleware など Store の外側で吸収する。
- 一方で、全プラットフォームに共通するライフサイクル相当の概念が抽象化できる場合には、`Store{}` DSL に導入することを改めて検討してよい。今回の却下理由は「Android 固有である」ことに依存しているため、プラットフォーム中立な形でモデル化できれば、その前提は崩れる。

## 関連

- [`Store{}` DSL に state 非依存の `onStart {}` は追加しない](./2026-04-28-store-onstart-dsl.md)
- [Middleware には直接 state 更新 API を入れない](./2026-04-26-middleware-dispatch-only.md)
