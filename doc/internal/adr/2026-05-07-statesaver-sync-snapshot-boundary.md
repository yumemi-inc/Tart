# `StateSaver` は同期 snapshot API に限定する

- 更新日: 2026-05-11

## 背景

Koma の Store は `state: StateFlow<S>` と `currentState: S` を同期 API として公開している。
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
- `save()` だけを `suspend` にしたい（restore は同期で良い）場合は、`StateSaver` の責務を割らずに `Plugin.onState` から `suspend` な write を行う Plugin を別途用意すればよい。`StateSaver` の同期境界はそのまま保てる。

## 非同期 persistence の Plugin による補助案

ADR 本文の決定通り、`StateSaver` は同期に閉じる。一方で DataStore / file I/O / network のように suspend な read/write を伴う persistence を使いたい利用者は、`Loading` state + `enter {}` + `Plugin.onState` を毎回手で組む事になり、ボイラープレートが残る。
このパターンを既存の Plugin API だけで codify する補助案を以下に記録する（決定ではなく設計メモ）。

### 設計

新しい top-level 抽象は追加せず、既存の Plugin（`onStart` / `onState` / `PluginScope.launch` / `dispatch`）だけで構成する。

```kotlin
fun <S : State, A : Action, E : Event> AsyncStatePersistencePlugin(
    load: suspend () -> S?,
    save: suspend (S) -> Unit,
    onLoaded: (S) -> A,
    onLoadFailed: ((Throwable) -> A)? = null,
): Plugin<S, A, E> = Plugin(
    onStart = { _ ->
        launch {
            runCatching { load() }
                .onSuccess { restored -> if (restored != null) dispatch(onLoaded(restored)) }
                .onFailure { e -> onLoadFailed?.let { dispatch(it(e)) } }
        }
    },
    onState = { _, state ->
        save(state)
    },
)
```

### 利用イメージ

```kotlin
Store {
    initialState = MyState.Loading
    plugin(
        AsyncStatePersistencePlugin(
            load = { dataStore.data.first() },
            save = { dataStore.updateData { _ -> it } },
            onLoaded = MyAction::Restored,
        )
    )
    state<MyState.Loading> {
        action<MyAction.Restored> {
            nextState { action.state }
        }
        action<MyAction.RestoreFailed> {
            nextState { MyState.Empty }
        }
    }
    state<MyState.Loaded> {
        // 通常のハンドラ
    }
}
```

### ADR との整合

- `StateSaver` の同期境界は崩さない。Plugin の組み合わせとして提供するだけなので本文の決定と矛盾しない。
- 「重い I/O は `enter {}` / `Plugin.onState` に逃がす」という方針を、利用者が手で組まずに済むようパッケージ化したもの。

### 制約とトレードオフ

- `PluginScope` には state を直接書く API は無く（`dispatch` 経由のみ）、利用者は「復元成功 Action」と必要なら「復元失敗 Action」を定義する必要がある。これは one-way data flow を保つ意図的な制約。
- Koma の DSL では `action {}` は `state {}` 配下にしか書けないため、復元 Action のハンドラは初期 state（典型的には `Loading`）配下にだけ書く。これは「復元 Action は初期 state でだけ意味を持つ」という意図と DSL 上整合する。

### 配置と提供方針

- `koma-core` 本体に DataStore 等の依存は持ち込まない。`load` / `save` を `suspend` ラムダで受ければ汎用に保てる。
- 別モジュール（例: `koma-persistence`）またはサンプルとして提供する案。
- `@ExperimentalKomaApi` で出して、利用実態を見てから本流昇格を判断する。
