# Samsung Galaxy対応

## 実装

ホーム、クイック設定、通知、自動維持は既存の `ShutterSound` と
`AutoMaintainController` を通る。方式選択と設定値の解釈は `ShutterBackend`
に集約した。CSCの生値 `0 / 1` はバックエンド内で扱い、既存UIとの境界でのみ
共通の解除状態へ変換する。方式選択用の画面やダイアログは追加していない。

- 端末の標準値と現在の音声ポリシーがともに `FORCE_NONE` なら、CSCを読まず変更不要とする。
- Samsung製でCSCが `1`、かつShizukuに設定変更権限があればCSCを優先する。
- CSCが `0` の場合はCSCを書かず、独立したAudioPolicyの強制があれば従来方式を使う。
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
| Androidテスト | `app/src/androidTest/java/jp/linkserver/shutter/ShutterJournalFileTest.kt`, `app/src/androidTest/java/jp/linkserver/shutter/ui/GalaxyHomeTest.kt` |
| 実装・検証記録 | `docs/galaxy-support.md` |

## 検証

2026-10-12にJDK 21とAndroid API 35の専用エミュレーターで検証した。

| 検証 | 結果 |
| --- | --- |
| `:app:testDebugUnitTest` | 95件成功、失敗0件（新規バックエンドテスト36件を含む） |
| `:app:assembleDebug` | 成功 |
| `:app:assembleDebugAndroidTest` | 成功 |
| `:app:assembleRelease` | 成功 |
| `:app:lintDebug` | エラー0件、警告41件 |
| Android instrumentation（最終Debug APK、API 35） | 46件成功、Shizuku実接続が必要な1件はスキップ |
| `git diff --check` | 問題なし |

Androidテストでは、復元記録のファイル保存・再生成・破損時の停止、
AtomicFileの中断復旧、Galaxy状態での共通ホーム操作と永続性の説明、
変更記録が残る場合のアンインストール案内、既存UIと自動維持のテストを実行した。
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

実行ログは `app/build/galaxy-final-validation.txt` と
`app/build/galaxy-instrumentation-final.txt`、Lintの詳細は
`app/build/reports/lint-results-debug.html` に保存した。
APKは `app/build/outputs/apk/debug/app-debug.apk` と
`app/build/outputs/apk/release/app-release.apk` に出力した。

Galaxy実機での動作は未検証。以下は実機で確認する必要がある。

- 対象の機種、地域設定、One UIでCSCキーが利用できること。
- ShizukuのADBモードから読み書きでき、純正カメラの消音に反映されること。
- サイレント／バイブ／システム音量による撮影音の変化。
- 再起動、Shizuku終了、OS更新後の保持やリセット、自動維持と復元。
- サブユーザー／仕事用プロファイルでカメラが参照する設定と、その端末での対応範囲。
