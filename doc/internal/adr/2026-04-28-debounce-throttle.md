# `debounce` / `throttle` は Store の built-in として入れない

- 更新日: 2026-04-30

## 背景

`dispatch()` に対して `debounce` や `throttle` のような時間窓ベースの制御を、Store の built-in として用意するかを検討した。

`LaunchControl.CancelPrevious` や `LaunchControl.DropIfRunning` も、active job の有無や起動順で振る舞いが変わるため、一見すると `debounce` / `throttle` と近い論点に見える。
ただし、これらが扱うのは key 付き lane に紐づいた launched job をどう調停するかであり、意味は job coordination の中に閉じている。

一方 `debounce` / `throttle` は、時間窓の中で複数回の入力をどうまとめるか、どれを落とすか、いつ実行するかを決める control であり、job 単位の制御より「入力の採否や実行タイミングを決める仕組み」としての意味合いが強い。
そのため、「どの dispatch が通るのか」「どの dispatch が失われるのか」が、state や action 定義だけでは追えず、時間経過まで含めて考えないと分からなくなりやすい。

また、Tart の Store は、action の処理順序や state transition の見通しを重視している。
そこへ時間窓によって dispatch の採否や実行タイミングが変わる制御を built-in で持ち込むと、見かけ上は単純な dispatch でも、内部では遅延、間引き、破棄が起こりうるため、API の読みやすさと説明しやすさが落ちる。

## 決定

現時点では、`debounce` / `throttle` は Store の built-in として追加しない。

- `dispatch()` 自体に時間窓ベースの採用・破棄制御は持ち込まない。
- Store 側で「一定時間内の dispatch をまとめる」「短時間の連続 dispatch を捨てる」といった機能は標準搭載しない。
- そうした制御が必要な場合は、現段階では UI 側で制御してから `dispatch()` する立場を取る。

## 補足

- 見送る理由の中心は、単に時間要素を含む制御だからではなく、`debounce` / `throttle` が tracked job の coordination というより、時間窓内で入力の採否や実行タイミングを決める仕組みとして振る舞うためである。
- `LaunchControl.CancelPrevious` / `DropIfRunning` は、「この lane の前回 launched job を止めて次を始める」「この lane に active job がある間は新しい launched job を始めない」という意味で、job に紐づく control としてまだ読みやすい。
- `debounce` / `throttle` により dispatch が失われても、それが仕様なのか不具合なのかを利用者が判断しづらくなりやすい。
- これらの制御が必要になる場面は、検索入力、連打防止、スクロール連動など、UI 起点のイベント整形であることが多い。そのため現段階では、Store の責務として吸収するより、UI 側でイベントを整えてから `dispatch()` する方が自然である。
- 将来もし同種の要求が増えても、まずは利用場面ごとの意図と失われてよい dispatch の条件を整理すべきであり、汎用の built-in を先に増やす判断は採らない。

## 関連

- [`action` の async 境界は明示のまま維持する](./2026-04-26-action-async-boundary.md)
