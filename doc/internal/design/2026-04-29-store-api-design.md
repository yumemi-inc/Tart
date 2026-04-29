# Store surface の設計メモ

- 更新日: 2026-04-29
- 関連: [Tart の設計原則](./2026-04-23-design-principles.md), [Store の開始タイミング policy 案](../notes/2026-04-23-store-start-policy.md), [`Store{}` DSL に state 非依存の `onStart {}` は追加しない](../adr/2026-04-28-store-onstart-dsl.md)

## 背景

`Store()` の overload、`Store{}` DSL の命名、Store の start semantics のように、Store の surface に関する論点は小さく散らばりやすい。

一方で、そうした論点のすべてを個別の ADR や notes に切り出すと、かえって見通しが悪くなることもある。
PR や会話の中だけに残してしまうと、「なぜ今の表面 API がこの形なのか」が後から読み取りにくい。

ここでは、Store の public API や DSL surface に関する設計上のメモを、節ごとに追加して残していく。
各節は独立した小さな論点を扱ってよく、粒度や性質が完全に揃っている必要はない。
個別に明確な採否を記録したくなった論点や、より大きな検討に発展した論点は、必要に応じて別の ADR や notes に切り出す。

## `Store()` overload

`Store()` の overload は、`initialState` と `CoroutineContext` という 2 つの入力軸を扱いやすくするための API surface として持つ。
組み合わせ上は単純でも、呼び出し側の style や置かれた文脈に応じて自然に書ける余地を残すことを優先する。

overload 数は必要以上に増やさないが、「理論上まとめられるから減らす」ことはしない。
Tart では surface の最小化そのものより、利用者が無理なく宣言を書けることを重く見る。

## `Store{}` DSL の naming

`Store{}` DSL の naming は、`onXxx` や動詞中心の hook 名より、`state {}` `action {}` `event()` のような宣言的な語を優先する。
Tart は「いつ何が起こるか」を命令的に列挙するより、「どの状態で何を扱うか」を記述する DSL として見せたい。

そのため DSL は、timeline や callback sequence を強く想起させる naming より、state machine の構造を前景化する naming を選ぶ。

## Store の start semantics

Store の start は、default では最初の `dispatch()` または `state` の collect を契機に自動で始まる形を維持する。
これは explicit `start()` を必須にするより、利用者の書き忘れや start 前後の扱いの曖昧さを減らしやすいためである。

`start()` 明示開始を default にしないのは、`start()` 忘れだけでなく、「どこで start するか」「start 前の `dispatch()` をどう扱うか」という別の設計負債を持ち込みやすいためである。

`dispatch()` のみを start 契機にすると、初回 state の loading を action 発火に依存させることになる。
逆に `state` collect のみを start 契機にすると、collect 前の `dispatch()` が失われうる。
default はそのどちらにも寄せない。

`event` collect を start 契機に含めないのは仕様である。
Tart では first state の loading は state 観測と結び付くべきであり、`event` の購読はあくまで副作用の購読として位置付ける。

UI の準備が整う前に first state の loading が進む eager start は default にしない。
Store はまず宣言として作られ、必要になった時点で動き出す方が自然である。

現行 default では、`enter {}` 直後に出した one-shot `event` を購読側が取りこぼす可能性はある。
ただし、そのような初回 event に強く依存する設計は例外的であり、必要なら `enter {}` に寄せず、開始用 action を明示して順序を作る方が分かりやすい。

start semantics の option を将来追加する余地までは否定しない。
[Store の開始タイミング policy 案](../notes/2026-04-23-store-start-policy.md) のような `StoreStartPolicy` は、default を変えずに例外的要件へ対応する整理としてはあり得る。
ただしその場合でも、default として優先するのは現在の「最初の `dispatch()` または `state` collect による自動開始」である。
