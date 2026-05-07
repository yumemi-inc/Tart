# `StateSaver` は同期 snapshot API に限定する

- 更新日: 2026-05-07

## 背景

Tart の Store は `state: StateFlow<S>` と `currentState: S` を同期 API として公開している。
`StateFlow` は初期値ありで構築されるため、その初期値は Store 作成時に同期的に決定される必要がある。
現状の実装では、`stateSaver.restore()` の結果があればそれを `_state` / `StateFlow` の初期値として採用している。
つまり、宣言された `initialState` を一度見せた後で restore 結果に差し替える、という挙動にはしておらず、これを変えると `StateFlow` を扱う利用者も混乱する。

`save()` だけを `suspend` にする案もありうるが、その場合 `StateSaver` が「同期 snapshot adapter」と「非同期 persistence gateway」の両方の責務を持ち、抽象の意味が濁る。

## 決定

`StateSaver` は、Store の同期 snapshot 境界を表す API として維持する。

- `StateSaver.restore()` は同期のままとする
- `StateSaver.save()` も同期のままとする
- `StateSaver` は軽量・即時な snapshot の保存/復元に使う
- `StateSaver` に重い I/O や `suspend` な read/write の責務は持たせない

network / database / file I/O のような重い persistence の為に `suspend` で read/write したい場合は、`StateSaver` ではなく次のように利用者側で別途実装する。

- read:
  `Loading` state を initialState にし、`enter {}` から `suspend` な read を行い、結果に応じて次の state へ遷移する
- write:
  committed state を契機に plugin から `suspend` な write を行う

## 補足

- この判断により、`StateFlow` の初期値と `currentState` の同期性を保てる。
- `enter {}` 自体が `suspend` なので、非同期 read のために `launch {}` は不要だが、startup を待たせずに並行に進めたい場合は `launch {}` を使う。
- `Plugin.onState` 自体が `suspend` なので、非同期 write のために `launch {}` は不要だが、write 完了まで Store の進行を待たせたくない場合は `launch {}` を使う。
