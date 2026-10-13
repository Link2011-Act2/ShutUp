# Samsung Galaxy対応

## CSCのシステム既定値取得（2026-10-13）

Shizuku UserServiceに、対象ユーザーのSystem設定からCSCの既定値を取得するIPCを追加した。
`/system/bin/dumpsys settings`を引数配列で起動し、stdout/stderrを並行して読む。
実行時間は4秒、出力は各ストリーム8MiB以内に制限し、失敗・キャンセル時も子プロセスを終了する。
終了コードの異常、stderr、サービス欠落、形式不一致、対象行の重複は取得不能として扱う。
dump全文をIPCやログに渡さず、対象キーの存在・現在値・既定値・システム由来フラグだけを返す。

`GalaxyCscDump`は対象ユーザーの`SYSTEM SETTINGS`ブロックと完全一致するキーだけを解析する。
`defaultSystemSet:true`かつ`default:0`または`default:1`を信頼できる復元先として採用する。
現在値、キー未登録、取得不能を混同しない。パッケージ名や地域コードから既定値を推測しない。

CSCの復元では新しく取得したシステム既定値を最優先にする。
他ツールで現在値が0にされていても、既存の書き込み可能なキーと既定値1が確認できれば、
ホーム・タイル・復元操作から1へ戻せる。読み取りや解除操作だけで1を書き込むことはない。
既定値が分かっただけで未登録キーを作成したり、旧AudioPolicyの変更記録をCSCへ切り替えたりしない。

取得不能の場合も現在値1のCSC解除は引き続き使える。
保存済みの変更前状態はShutUp自身の変更を取り消すためだけに使い、メーカー既定値とは表示しない。
この場合は「変更前に戻す」、取得できた場合は「既定値に戻す」と既存操作の文言を合わせる。
復元結果の文言も、その操作で実際に使った復元先の由来に合わせる。

保存形式2ではCSCの変更前0・1・未登録（absent）を保存できる。
既存の形式1はそのまま読み取り、最初に保存した変更前状態は上書きしない。
未登録状態へのフォールバック復元では対象キーだけを削除し、未登録の再読み取りを確認する。
通常の方式選択では未登録キーを変更しないため、新たなキー作成経路は追加していない。

追加の設計・実装情報は[CSC既定値取得の記録](galaxy-csc-default-review.md)を参照。

## 実装

ホーム、クイック設定、通知、自動維持は既存の `ShutterSound` と
`AutoMaintainController` を通る。方式選択と設定値の解釈は `ShutterBackend`
に集約した。CSCの生値 `0 / 1` はバックエンド内で扱い、既存UIとの境界でのみ
共通の解除状態へ変換する。方式選択用の画面やダイアログは追加していない。

- 端末の標準値と現在の音声ポリシーがともに `FORCE_NONE` なら、CSCを読まず変更不要とする。
- Samsung製でCSCが `1`、かつShizukuに設定変更権限があればCSCを優先する。
- CSCが `0` でも、既存キー・変更権限・信頼できる既定値を確認できればCSCを選択できる。
  既定値0と現在値0が一致し、AudioPolicyに独立した強制が残る場合は従来方式を使う。
  解除操作ではCSCが0なら書き込まず、必要な独立したAudioPolicyの解除だけを行う。
- CSCの欠落、未知の値、読み取り失敗、権限不足はAudioPolicyへフォールバックする。
- CSC書き込み後に元の値のままだと確認できた場合だけ、初回の解除をAudioPolicyへフォールバックする。
- 書き込み結果が不明な場合や、すでに方式が固定されている場合は、別方式へ切り替えない。
- 書き込みのタイムアウトやIPC切断は、直後の読み取りが元の値でもフォールバックしない。
  SettingsProvider内の処理が遅れて完了する可能性があるため、変更記録を保持する。

`ShutterService` に対象キー専用の読み書きIPCを追加した。
UserService内で引数配列から `/system/bin/cmd settings --user <userId>` を起動する。
`settings`のシェルラッパーを経由せず、終了対象の子プロセスを一つにする。
シェル文字列の組み立てや、任意のコマンド・設定キーを受け取るIPCは使用しない。
ユーザーIDはShutUpのUIDから取得し、別のAndroidユーザーの設定を暗黙に変更しない。
実行時間はコマンドごとに制限し、終了時は子プロセスも終了する。
IPCの改訂番号をUserServiceのバージョンに含め、旧サービスの再利用を防ぐ。

## 権限と対応状況の確認

[AOSP SettingsProvider](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java)
ではSystem設定の変更権限をチェックし、system/root/shellは通常アプリの
ターゲットSDKによる制限から除外される。
[Shizuku UserService](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java)
はADB起動時にshell UIDで動く。通常アプリ側の `ContentResolver` を使わず、
UserServiceから実行する構成とした。

CSC用の事前チェックはShizukuの `WRITE_SECURE_SETTINGS`、AudioPolicy用は
従来の `MODIFY_AUDIO_ROUTING` を使う。端末固有の制限は書き込み後の読み取りでも確認する。
アプリのManifest権限、minSdk、targetSdkは変更していない。rootを必須にしていない。

[公開されているGalaxy向け実装](https://github.com/AwHsR15/SilentCamera/blob/main/README.en.md)
でも同じキーをShizuku経由で変更する方式を採用している。ただし、これを全Galaxyの
動作保証とは扱わない。設定の解除確認と、純正カメラが実際に無音になることは別である。

## 復元と移行

変更前の値と使用方式を `noBackupFilesDir/shutter-change-v1` に保存する。
`AtomicFile`、ファイル同期、コミット後の読み取り検証を終えてから端末設定を変更する。
このディレクトリはクラウドバックアップと端末移行の対象外である。

記録は解除の再実行でも上書きしない。書き込み直前に終了した場合も、書き込み直後に
終了した場合も、次回は記録した方式で実際の設定を読む。復元後の値を確認するまで
記録は削除しない。未知の設定値や破損した記録は、新しい方式への切り替えで回避しない。

旧版の自動維持の解除意図と、インストール／更新時刻を初回移行の手がかりにする。
更新前のAudioPolicy解除が見つかった場合は同方式の復元先を保存する。
旧版の自動維持が有効な場合は、再起動で音声ポリシーが戻っていてもAudioPolicyを維持する。
明示的な復元後に記録を消し、次の解除では自動選択する。
新規利用でAudioPolicyだけが解除されている場合は、CSCの強制を見落とさない。

非Samsung端末でAudioPolicyの現在値が `FORCE_NONE`、既定値が
`FORCE_SYSTEM_ENFORCED` の場合は、変更記録がなくても既定値への復元を可能にする。
移行済みの印があっても状態確認時にAudioPolicyの復元情報を補完する。
これによりホームの復元ボタン、通知、タイルで同じ解除状態を扱い、
復元操作で既定値に戻せる。状態確認時には復元情報の保存のみを行う。
SamsungでCSCが最初から `0` の場合も、読み取りや解除だけでCSCを1へ変更しない。
信頼できるシステム既定値1が確認できた場合の明示的な復元は可能になった。

手動復元は既存の意図保存と競合防止を通じて自動維持を先に停止する。
自動維持はCSC `1 → 0`、または音声ポリシー `11 → 0` が必要なときだけ書く。
設定値がすでに解除済みの場合は書かない。
方式内部の読み取りや保存中にWorkerが停止しても、書き込み直前に
キャンセルを再確認するため、遅れて解除を書き込まない。

復元情報はアプリのデータ削除やアンインストールでは失われる。
永続する設定はアプリ削除前に復元する必要があり、その案内をホームと通知に表示する。

## 変更ファイル

| 範囲 | ファイル |
| --- | --- |
| 方式選択・共通状態・復元記録 | `app/src/main/java/jp/linkserver/shutter/ShutterBackend.kt`, `ShutterJournalFile.kt` |
| Shizuku・IPC | `app/src/main/aidl/jp/linkserver/shutter/IShutterService.aidl`, `app/src/main/java/jp/linkserver/shutter/ShutterService.kt`, `ShizukuAudioPolicy.kt` |
| 共通操作・状態・エラー | `app/src/main/java/jp/linkserver/shutter/ShutterSound.kt`, `ShutterViewModel.kt`, `AudioPolicyException.kt` |
| 自動維持 | `app/src/main/java/jp/linkserver/shutter/maintain/AutoMaintainController.kt`, `AutoMaintainRuntime.kt` |
| クイック設定・通知 | `app/src/main/java/jp/linkserver/shutter/ShutterTileService.kt`, `Notifications.kt` |
| 必要な説明・復元表示 | `app/src/main/java/jp/linkserver/shutter/ui/HomeScreen.kt`, `UninstallReminderDialog.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ja/strings.xml` |
| JVMテスト | `app/src/test/java/jp/linkserver/shutter/ShutterBackendTest.kt`, `app/src/test/java/jp/linkserver/shutter/maintain/PolicyQueryTest.kt` |
| Androidテスト | `app/src/androidTest/java/jp/linkserver/shutter/ShutterJournalFileTest.kt`, `app/src/androidTest/java/jp/linkserver/shutter/ui/GalaxyHomeTest.kt`, `HomeScreenTest.kt` |
| 実装・検証記録 | `docs/galaxy-support.md` |

追加ファイル・変更ファイル（CSC既定値取得）:

| 範囲 | ファイル |
| --- | --- |
| パーサー・メタデータ | `app/src/main/java/jp/linkserver/shutter/GalaxyCscSetting.kt`（新規） |
| 出力・タイムアウト管理 | `app/src/main/java/jp/linkserver/shutter/ShutterCommand.kt`（新規） |
| IPC・Shizuku | `IShutterService.aidl`, `ShutterService.kt`, `ShizukuAudioPolicy.kt` |
| 方式選択・復元・保存 | `ShutterBackend.kt`, `ShutterJournalFile.kt` |
| 共通状態・UI・タイル | `ShutterViewModel.kt`, `ShutterTileService.kt`, `ui/HomeScreen.kt`, `ui/UninstallReminderDialog.kt` |
| 日本語・英語 | `app/src/main/res/values-ja/strings.xml`, `app/src/main/res/values/strings.xml` |
| 新規JVMテスト | `GalaxyCscDumpTest.kt`, `ShutterCommandTest.kt`, `DumpCommandTestProcess.java` |
| 追加・調整したテスト | `ShutterBackendTest.kt`, `ShutterJournalFileTest.kt`, `ui/GalaxyHomeTest.kt`, `ui/HomeScreenTest.kt` |
| 検討・実装記録 | `docs/galaxy-csc-default-review.md` |

2026-10-13の検証: `:app:testDebugUnitTest`は129件成功、失敗・エラー0件。
Debug APK・AndroidテストAPK・Release APKのビルド成功。Lintはエラー0件、警告41件。
出力は`app/build/csc-default-validation.txt`。
API 35の専用エミュレーターで52件のAndroidテストが完了し、失敗0件。
51件成功、Shizukuの実接続を必要とする1件はスキップ。
新旧の復元記録と未登録状態の保存、他ツールの解除状態からの復元ボタン、
既定値取得時とフォールバック時の文言、既存の自動維持・復元ダイアログも確認した。
Androidテストログは`app/build/csc-default-instrumentation.txt`、
Releaseビルドログは`app/build/csc-default-release.txt`に保存した。
Git差分チェックも問題なし。README、Manifest、targetSdk、ユーザー設定は変更していない。

## 検証

2026-10-12にJDK 21とAndroid API 35の専用エミュレーターで検証した。

| 検証 | 結果 |
| --- | --- |
| `:app:testDebugUnitTest` | 97件成功、失敗0件（新規バックエンドテスト38件を含む） |
| `:app:assembleDebug` | 成功 |
| `:app:assembleDebugAndroidTest` | 成功 |
| `:app:assembleRelease` | 成功 |
| `:app:lintDebug` | エラー0件、警告41件 |
| Android instrumentation（最終Debug APK、API 35） | 47件成功、Shizuku実接続が必要な1件はスキップ |
| `git diff --check` | 問題なし |

Androidテストでは、復元記録のファイル保存・再生成・破損時の停止、
AtomicFileの中断復旧、Galaxy状態での共通ホーム操作と永続性の説明、
変更記録が残る場合のアンインストール案内、既存UIと自動維持のテストを実行した。
非Galaxyの解除済み・移行済み・復元記録なしの状態では、実際のバックエンドと
画面状態変換を使い、ホームの復元ボタンが有効になって既定値へ戻せることを確認した。
エミュレーターではShizukuを起動していないため、実接続とAudioPolicyの実書き込みを
確認する `ShizukuAudioPermissionTest` はスキップした。

ユニットテストは、方式選択、CSC初期値0、標準値／現在値がともに解除済みの端末、
CSCとAudioPolicyの不一致、既存解除の移行、復元先の保持、途中終了、保存失敗、
書き込み／再読み取り失敗、タイムアウト、手動復元との競合、Worker停止、
共通操作経路での状態とタイル切り替えを検証する。

エミュレーターのshellでは `WRITE_SECURE_SETTINGS` と `MODIFY_AUDIO_ROUTING`
の許可を確認した。実装で使用する `cmd settings --user 0 get system` による
対象キーの読み取りは `null` で、Galaxy固有キーの不在を確認した。
エミュレーター上の結果は、Samsung製端末の権限やカメラの動作保証とは扱わない。

非Galaxy端末で解除済み・復元記録なしの条件は、修正前に2件の回帰テストで
失敗を再現し、修正後に成功を確認した。初回移行前と移行済みの両方、
タイルからの復元と自動維持の停止も検証する。

実行ログは `app/build/audio-restore-validation.txt` と
`app/build/audio-restore-instrumentation.txt`、修正前の再現ログは
`app/build/audio-restore-reproduction.txt`、Lintの詳細は
`app/build/reports/lint-results-debug.html` に保存した。
APKは `app/build/outputs/apk/debug/app-debug.apk` と
`app/build/outputs/apk/release/app-release.apk` に出力した。

ShutUpからGalaxy実機への操作は未検証。
ユーザー提供の日本版A53（SC-53C / Android 16 / One UI 8）のADB結果では、
現在値1・既定値1・defaultSystemSet:trueを取得できた。
US版S24ではキー未登録とdumpサービスの取得失敗が報告されている。
これらを全Galaxyの動作保証とは扱わない。以下は実機で確認する必要がある。

- 対象の機種、地域設定、One UIでCSCキーが利用できること。
- ShizukuのADBモードから読み書きでき、純正カメラの消音に反映されること。
- サイレント／バイブ／システム音量による撮影音の変化。
- 再起動、Shizuku終了、OS更新後の保持やリセット、自動維持と復元。
- サブユーザー／仕事用プロファイルでカメラが参照する設定と、その端末での対応範囲。
- ShutUpのUserServiceからのdump取得、書き込み・復元後のdefault/defaultSystemSetの保持。
