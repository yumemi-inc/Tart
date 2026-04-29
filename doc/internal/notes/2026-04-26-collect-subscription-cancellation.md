# collectState / collectEvent の個別購読解除案

- 更新日: 2026-04-29
- 関連: [Store の開始タイミング policy 案](./2026-04-23-store-start-policy.md)

## 背景

現状の `collectState()` / `collectEvent()` は、Store 内部の coroutine scope で collect を開始し、個別の解除手段を返さない。
そのため、callback ベースの購読期間は実質的に Store の寿命と同一になっている。
購読を止める手段は `Store.dispose()` のみであり、その場合は Store 全体が停止する。

この前提は、次のような使い方では自然である。

- 1 つの Store を 1 つの owner が持つ
- owner が不要になったら Store ごと `dispose()` する
- observer の寿命は常に Store と同じ

一方で、次のような使い方では弱い。

- Store は生かしたまま observer だけ付け外ししたい
- 同じ Store を複数箇所が別々に監視する
- `collectState()` や `collectEvent()` を重ねて呼ぶ可能性がある
- `state` と `event` を別々の寿命で扱いたい

また、`Flow` を直接扱いにくいプラットフォーム向け API という前提では、呼び出し側に `CoroutineScope` を要求する案は採りづらい。
検討対象は、callback ベース API のまま個別の購読解除を表現するかどうかである。

## 現在の考え

個別解除を入れるなら、もっとも単純なのは `collectState()` / `collectEvent()` 自体が購読ハンドルを返す形にすること。
既存 API と同名で戻り値を変えるため、これは破壊的変更になる。

```kt
fun collectState(state: (S) -> Unit): Subscription

fun collectEvent(event: (E) -> Unit): Subscription
```

ハンドル型は技術的には `AutoCloseable` でも表現できるが、この文脈で欲しいのは「resource を閉じる」より「購読を解除する」意味である。
そのため、意図の明確さでは専用の `Subscription` 型の方がよい。
加えて、`collectState()` / `collectEvent()` は `Flow` を直接扱いにくいプラットフォーム向け callback API でもある。
Kotlin から見れば `AutoCloseable` 自体は使えるし、実際に `Store` も `AutoCloseable` を実装している。
しかし Swift / Obj-C などの非 Kotlin 呼び出し側まで考えると、`AutoCloseable` をそのまま返す形は API の意図が伝わりにくい。
この文脈では JVM/Android 寄りの既存慣習よりも、「これは購読解除ハンドルである」と分かる専用型を返す方が cross-platform API surface として自然である。

```kt
interface Subscription {
    fun cancel()
}
```

あるいは `Store.dispose()` と語彙を揃えたいなら、次のような形でもよい。

```kt
interface ObservationHandle {
    fun dispose()
}
```

どちらにせよ、重要なのは `AutoCloseable` そのものを返すことではなく、
callback ベース API に対応した Tart 独自の薄いハンドル型として意味を固定することである。

実装方針は単純で、Store 内部 scope に購読ごとの child job を作り、その job を止めるハンドルを返すだけでよい。

- `collectState()` は `state.collect` を開始する child job を作る
- `collectEvent()` は `event.collect` を開始する child job を作る
- `Subscription.cancel()` は対応する child job だけを cancel する
- `Store.dispose()` は従来どおり Store scope ごと cancel するため、既存の購読もすべて止まる

この変更は「個別解除できるようにする」だけであり、Store の start semantics 自体は変えない。
そのため、現状のままなら次が維持される。

- `collectState()` は Store start の trigger になる
- `collectEvent()` は Store start の trigger にならない

したがって、この変更を入れても start semantics の非対称性は別途残る。

また、個別解除が本当に必要かは利用前提次第である。
Store の寿命と observer の寿命が常に一致する設計を前提にするなら、個別解除機能は不要であり、現状の contract を README と API comment に明示するだけでも十分である。
逆に、Store を長寿命に保ちつつ observer を付け外しするユースケースを public API として支えるなら、個別解除は自然な機能になる。

## 未解決事項

- 戻り値ハンドルの名前とメソッドを `Subscription.cancel()` / `ObservationHandle.dispose()` のどちらに寄せるか
- `collectState()` / `collectEvent()` に個別解除を入れる release で、start semantics の非対称性も合わせて見直すか
- callback ベース API に個別解除を入れる前提として、Store を複数 observer が監視するユースケースをどこまで正式に支えるか
- README 上で、購読ハンドルの保持と解除タイミングをどのように説明するか
