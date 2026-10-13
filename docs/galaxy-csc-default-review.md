# Galaxy CSC既定値取得の検討と実装

検討日: 2026-10-13

## 実装結果（最新の仕様）

ユーザーの仕様修正に従い、CSCの復元先は信頼できるシステム既定値を最優先にした。
現在値や保存した変更前状態ではなく、操作時に取得したdefaultを用いる。
取得不能なら、保存済みの変更前状態によるShutUp自身の変更取り消しだけを許可する。
保存済み現在値をメーカー既定値の代わりにはしない。

- `GalaxyCscSetting.kt`: 対象ユーザー・System namespace・完全一致キーのパーサーと小さいIPC結果。
- `ShutterCommand.kt`: 両ストリームを並行して読み、4秒・各8MiBの上限を設けたコマンド取得。
- UserService: 既定値取得IPC（5）と未登録状態の復元用削除IPC（6）。既存のIPC番号は維持。
- `ShutterBackend`: 新しいシステム既定値を優先する復元、既存キーの0/default:1への対応。
- `ShutterJournalFile`: 形式2で変更前0・1・未登録を保存、形式1との読み取り互換を維持。
- UI・タイル・通知: システム既定値との違いを共通状態で扱い、復元元の由来に応じた文言を表示。
- AutoMaintainControllerの直列化・キャンセル確認・手動復元によるarmed解除は維持。

現在値0・既定値1は、変更記録がなくても明示的に既定値へ復元できる。
その復元前にも変更前0を確実に保存し、書き込み後の現在値を確認してから記録を消す。
現在値0・既定値0は不要なCSC書き込みを行わない。
CSCが未登録・書き込み不可なら既定値の情報だけでCSC方式を選ばない。
非Samsung端末とAudioPolicyの既定値・現在値が両方NONEの端末の既存動作を維持する。

129件のJVMテストが成功。パーサー11件、実際の子プロセスを使う取得処理6件、
既存・新規バックエンド53件を含む。DebugとAndroidテストAPKのビルド、Lintも成功。
API 35の専用エミュレーターでは52件完了・失敗0件。
51件成功、Shizuku実接続が必要な1件はスキップ。Releaseビルドも成功。
Galaxy実機でShizukuから取得・変更する検証は未実施。

以降は実装前の検討記録である。コード未変更・保存形式の制約などの記述は当時の状態を示す。
現在の実装・復元優先順位には上記の最新仕様を適用する。

## 結論

他のツールによる変更後や再インストール後も端末の既定値を判断する目的には、
CSC既定値の読み取り機能を追加する価値がある。
現在のCSC解除処理は変更前の値へ戻す方式であり、解除そのものには既定値取得を必須にしない。

追加する場合は、既定値の読み取りを独立した任意の能力として実装する。
取得不能な端末でも現在の方式選択・解除・保存済み情報による復元を維持する。
今回の報告だけを根拠に復元先を変更したり、既存の変更記録を置き換えたりしない。
この検討ではアプリのコード、IPC、保存形式、UIを変更していない。

## ユーザー提供の実機結果

### 日本版 Galaxy A53 5G / SC-53C / Android 16 / One UI 8

ADB shellの`dumpsys settings`について、次の出力が提供された。

```text
_id:904 name:csc_pref_camera_forced_shuttersound_key pkg:com.samsung.sec.android.application.csc value:1 default:1 defaultSystemSet:true isValuePreservedInRestore:true
```

`settings get system csc_pref_camera_forced_shuttersound_key`も1を返したとの報告。
この端末・この時点では、現在値とシステム由来の既定値の両方をADBから取得できた。
これはユーザーの実機検証結果であり、この作業環境から再検証した結果ではない。

`pkg`は現在値の書き込み元であり、既定値の由来を示す`defaultSystemSet`とは別。
変更後にpkgがshellへ変わっても既定値は残り得るため、CSCアプリのパッケージ名を固定条件にしない。
`isValuePreservedInRestore`はSettingsProvider側の復元時保持属性であり、
ShutUpの変更前記録の存在や、アンインストール後の復元を保証する情報とは扱わない。

### US版 Galaxy S24 / Android 16 / One UI 8.0

提供された結果は、現在値が`null`、設定一覧にもキーがなく、
`dumpsys settings`は`Can't find service: settings`。
キーの未登録とdump機能の取得失敗は別の情報として扱う。
dumpの失敗から既定値が0または1であることは推定しない。
この報告をUS版Galaxy全体の仕様とは扱わない。

### 未検証の範囲

- ShutUpのShizuku UserServiceから同じ情報を取得できるか。
- CSCを書き換えた後も`default`と`defaultSystemSet`が維持されるか。
- 再起動、Shizukuの終了、アプリの再インストール、OS更新の影響。
- 対象ユーザーのSYSTEM設定であることを含めた、複数ユーザーでの取得。

検討時に`adb devices -l`を確認したが、この作業環境に接続中の端末はなかった。

## 現在の実装との整合性

### 現在値と復元情報

`ShizukuAudioPolicy`はUserServiceの`getGalaxyShutter(userId)`経由で
現在値を取得し、文字列`null`はキー未登録相当として扱う。
`ShutterBackend.inspect()`は、CSCが1で変更権限がある場合にCSC方式を選ぶ。
CSCが0・未登録・未知・読み取り失敗の場合は、独立したAudioPolicyを確認する。
CSCが未登録の場合、キーを新規作成しない。

端末のAudioPolicyの既定値と現在値が両方FORCE_NONEの場合は、
SamsungでもCSCを読まずに変更不要とする。この既存仕様は維持する。

日本版A53の報告値1は現在のCSC選択条件と一致する。
ただし現在のコードは`default`を取得しておらず、1をシステム既定値として確認しているわけではない。

### 復元記録の制約

`ShutterChange.original`はIntで、`ShutterJournalFile`の現行形式はCSCの復元先に1だけを認める。
現在のCSC変更経路は1から0への変更だけなので、この制約は現行経路と一致する。
既存のUIもCSCについては「変更前に戻す」「変更前の設定」と表示している。

この保存形式では、CSCの変更前の0や未登録状態を明示的に保存することはできない。
ただし現行コードはCSCが0や未登録の状態を変更しないため、これらを壊す変更は行わない。
既定値取得を追加するだけで、未登録のキーを作成する経路を追加する必要はない。

将来、変更前が0・未登録でもCSCを書き換える操作を許可する場合は、
変更前の状態を既定値とは別に保存する形式と、既存形式からの移行が必要。
未登録状態へ戻すにはキーを削除し、再読み取りで未登録になったことを確認する。
取得失敗を未登録として保存したり、文字列nullをそのまま書き込んだりしない。

## 推奨する追加構成

### 読み取り経路

- 既存のShizuku UserServiceに対象キー専用の読み取りIPCを追加する。
- コマンドは引数配列で`/system/bin/dumpsys settings`を実行する。
  任意のコマンド、キー、シェル文字列をIPCで受け取らない。
- アプリのUIDから求めた対象ユーザーのSYSTEM SETTINGSブロックだけを読む。
- キー名は完全一致で判定する。別ユーザー・別namespaceの同名キーは採用しない。
- `value`、`default`、`defaultSystemSet`を別のフィールドとして解釈する。
- `defaultSystemSet:true`かつ`default`が厳密に0または1の場合だけ、
  システム既定値として採用する。追加の属性や属性順序の違いには対応する。
- サービス欠落、権限不足、タイムアウト、形式不一致、途中で切れた出力、
  同一対象の重複・矛盾がある場合は取得不能として扱う。
- 生のdump全文をアプリへ返したり、ログや保存ファイルへ残したりしない。
- IPCの既存番号を維持し、新しい取得処理の追加時にはUserServiceの改訂番号も更新する。

現在の`ShutterService.galaxySettings()`は、終了を待ってから標準出力を読む。
短いget/putの出力を想定した処理なので、量の多いdumpにはそのまま使わない。
dumpでは標準出力をプロセス実行中に読み取り、タイムアウト・サイズ上限・子プロセスの終了を管理する。
JavaのProcessドキュメントも、出力の読み取りが遅れるとバッファの制約で停止し得ると説明している。

### 状態と復元の区別

- 現在値: 値0、値1、キー未登録、取得不能を区別する。
- システム既定値: 確認できた0または1と、取得不能を区別する。
- 変更前の状態: 変更の取り消し・途中失敗の復旧に使う。システム既定値の証拠にはしない。
- 確認済みのシステム既定値: 「既定値に戻す」操作の判定に使う。

既存の`restoreValue`は変更前の値を兼ねているため、そこへdefaultを代入するだけの変更はしない。
既存の変更記録は維持し、方式の固定・復元情報の保存順序・書き込み後の検証・
手動復元を優先するAutoMaintainControllerの直列化を引き継ぐ。

現在値0・既定値1であっても、ShutUpが変更したとは特定できない。
状態確認や解除操作だけで1へ書き戻したり、自動維持を有効にしたりしない。
記録がない状態からの既定値復元を追加する場合は、明示的なユーザー操作として設計する。
以前からのAudioPolicy解除を、CSCの既定値が見つかっただけでCSCへ切り替えない。

### 取得失敗時の扱い

既定値取得が失敗しただけで、正常に使えるCSC解除を無効にする必要はない。
現在値1と変更権限が確認できる場合は、既存どおり変更前の1を保存して解除できる。
ただしその1を「取得したシステム既定値」と表示しない。

CSCの現在値自体が未登録・未知・取得不能なら、初回選択時は既存のAudioPolicyへフォールバックする。
CSCで変更済みの記録がある場合は、読み取り失敗を理由に方式を切り替えず記録を保持する。
dump取得だけでは設定に書き込まないので、解除書き込みの不確定な結果との扱いも分離する。

## 追加する場合に必要な検証

- 提供されたA53の行を含む、対象ユーザーのSYSTEMブロックからvalue/defaultを別々に読む。
- value:0 / default:1、value:0 / default:0、defaultSystemSet:false、default欠落。
- キー未登録、未知の値、権限エラー、Can't find service、途中切断、重複行。
- 複数ユーザー、別namespaceの同名キー、属性順序、追加属性。
- 大きい出力、タイムアウト、キャンセル時の子プロセス終了。
- 非Samsung・標準で解除不要の端末では新しい取得処理を呼ばない。
- メタデータ取得失敗時も、現在のCSC解除・保存済み情報の復元が使える。
- 既存の保存形式・AudioPolicy移行・手動復元と自動維持の競合防止を維持する。
- 実機のShizuku ADBモードで、変更前・変更後・復元後にvalue/defaultを比較する。

今回はコードの確認と設計検討のみを行った。既存テストの該当ケースは確認したが、
ビルドやテストの再実行、端末へのインストール・設定変更は行っていない。

## 参照

- `app/src/main/java/jp/linkserver/shutter/ShutterBackend.kt`
- `app/src/main/java/jp/linkserver/shutter/ShutterJournalFile.kt`
- `app/src/main/java/jp/linkserver/shutter/ShutterService.kt`
- `app/src/main/java/jp/linkserver/shutter/ShizukuAudioPolicy.kt`
- `app/src/main/java/jp/linkserver/shutter/ShutterViewModel.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/HomeScreen.kt`
- `app/src/test/java/jp/linkserver/shutter/ShutterBackendTest.kt`
- [AOSP SettingsProvider: dump](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java#L907)
- [AOSP SettingsService: DUMP権限](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/packages/SettingsProvider/src/com/android/providers/settings/SettingsService.java#L49)
- [AOSP SettingsState: 既定値と保存情報](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/packages/SettingsProvider/src/com/android/providers/settings/SettingsState.java#L1653)
- [Shizuku UserService API](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java)
- [Java Process: 出力バッファ](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html)
