# シャッター音の解除状態の自動維持

設定画面に「解除状態を維持」を追加した。初期値はOFFで、日本語・英語に対応する。
既存のホーム画面、更新確認、About、アンインストール前の案内は維持している。

## 状態と手動操作

`auto_maintain` SharedPreferencesへ、`autoMaintainEnabled`と`autoMaintainArmed`を
同じトランザクションで`commit()`する。両方がtrueのときだけ自動解除できる。

| 操作 | enabled | armed | 音声ポリシー |
| --- | --- | --- | --- |
| 初期状態 | false | false | 変更しない |
| OFF→ON | true | true | 利用可能なら確認し、11だけを0へ戻す |
| 手動解除 | 変更しない | true | 0にする。既に0なら書き込まない |
| 手動復元 | 変更しない | false | `mustPlayShutterSound()`から取得した既定値へ戻す |
| OFF | false | 変更しない | 変更しない |

手動復元の一時停止は、アプリ終了・プロセスの再生成・端末再起動後も保持する。
トグルはONのまま表示し、設定画面に一時停止と再開方法を表示する。
維持が有効という表示は現在値が0と確認できたことを意味しない。

すべての経路は`ShutterSound`から同じコーディネーターへ入る。
ホームとアンインストール前の案内は明示的なrelease/restore、通知はrestoreを使用する。
QSタイルは直列化された現在値の読み取りから解除・復元を選ぶ。
QSの読み取りが失敗した場合は、安全のため自動解除を一時停止したままにする。
既定値が0であっても、restoreとreleaseを同じ操作として扱わない。

## ShizukuとWorkManager

Applicationがプロセスに1個のランタイムを保持する。ON中だけ
`addBinderReceivedListenerSticky()`と認可結果リスナーを登録し、OFFで解除する。
アプリ起動・復帰時にも確認する。ViewModelは表示更新の責務を持ち、自動維持の
リスナーやWorkerの所有者にはならない。

`maintain-shutter-release`という名前で、1時間の`PeriodicWorkRequest`を
`enqueueUniquePeriodicWork(..., KEEP, ...)`する。初回の定期実行も1時間後とし、
ON直後の確認は別途アプリ内で実施する。ネットワーク制約を設けず、OFFでキャンセルする。
一時停止中は定期登録を残すが、WorkerはShizukuへ接続せず終了する。
WorkManager標準の再スケジュール受信機とDBを使い、独自の起動時ポーリングは設けない。

Workerは`CoroutineWorker`で、接続・確認を直接awaitする。接続待ちは8秒、
コーディネーターの接続タイムアウトは10秒、Worker全体の待機予算は12秒。
キャンセル時に接続コールバック、タイマー、一時的なBinderリスナーをMainで解除する。
接続コールバック自体は音声ポリシーを読み書きしない。
Shizuku未起動・未認可、未知の値、確認失敗は次の定期実行または接続イベントまで保留し、
短い間隔のretryを繰り返さない。

## 競合防止

- 単一のMutexで、UI・QS・通知・Worker・接続イベントの実際の読み書きを直列化する。
- 設定・手動要求はコルーチン起動前に共通ロック下で記録し、自動処理の世代を無効化する。
  実行中の自動処理の子Jobもキャンセルする。手動要求には別の世代を設け、OFF操作で
  進行中の手動復元を取り消さない。
- 自動処理は接続の前後と書き込み直前に、世代・両フラグ・手動操作待ちを再確認する。
  書き込み直前の確認と同期Binder書き込みは同じロック下で実行する。
- 同時の自動トリガーはまとめる。UIの読み取り中の接続イベントはMutexで待機し、
  確認機会を失わない。接続待ちの手動復元・OFF・Worker停止は残存コールバックから
  書き込みを発生させない。
- 保存失敗ではメモリ上もarmedをfalseにして、その要求から書き込まない。
  UIに保存失敗を通知する。通知やタイル表示の更新失敗で音声操作の結果を失わせない。

同期Binder呼び出し自体をKotlinのタイムアウトで強制中断することはできない。
既に書き込み中なら完了までロックを保持し、その後の手動復元を最終操作にする。
そのため上記の時間は接続・非同期待機の予算で、応答しないBinderの厳密な実行時間上限ではない。

## 制約

約1時間の実行は正確な60分間隔を保証しない。Doze、メーカーの省電力制御、
アプリの強制停止などにより遅延または停止する。Shizukuの接続イベントはプロセスが
生きている場合だけ受信でき、イベントだけで終了したアプリが起動するとは想定しない。
Shizukuが利用可能かつ認可済みでなければ確認・再解除できない。
ワイヤレスデバッグ版は再起動後にユーザーによるShizukuの再起動が必要になる場合がある。
OSが確認直後に再度ポリシーを適用した場合は、次の確認機会まで復旧されない。
Foreground Service、常時監視、30秒ポーリングは実装していない。

仕様の参照先:
[WorkManagerの定期実行](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)、
[CoroutineWorker](https://developer.android.com/develop/background-work/background-tasks/persistent/threading/coroutineworker)、
[Shizuku API](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java)。

## 変更ファイル

`app/src/main/java/jp/linkserver/shutter/`以下:

- 追加: `maintain/AutoMaintainController.kt`、`AutoMaintainPreferences.kt`、
  `AutoMaintainRuntime.kt`、`AutoMaintainScheduler.kt`、`AutoMaintainWorker.kt`
  （5つとも`maintain/`内）。
- 追加: `ShizukuAudioPolicy.kt`、`ShutUpApplication.kt`、`ui/AutoMaintainSettingsSection.kt`。
- 変更: `ShutterSound.kt`、`ShutterViewModel.kt`、`ShutterTileService.kt`、
  `RestoreReceiver.kt`、`MainActivity.kt`、`ui/ShutterApp.kt`、`ui/OssLicensesScreen.kt`。

その他:

- `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`。
- `app/src/main/res/values/strings.xml`、`app/src/main/res/values-ja/strings.xml`。
- `app/src/test/java/jp/linkserver/shutter/maintain/AutoMaintainControllerTest.kt`。
- `app/src/androidTest/java/jp/linkserver/shutter/ui/AutoMaintainSettingsTest.kt`。
- `app/src/androidTest/java/jp/linkserver/shutter/maintain/AutoMaintainWorkTest.kt`、
  `ShizukuConnectionCancellationTest.kt`（同ディレクトリ）。
- `README.md`、`design-qa.md`、このドキュメント。

テストでは音声ポリシーを模擬した接続と隔離した設定を使用する。
実機のShizukuによる音声ポリシー変更、実機再起動後の再解除、実際の省電力状態での
1時間間隔の実行を検証済みとは扱わない。

## 最終検証結果

- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug`成功
  （`app/build/auto-maintain/build-verified.txt`）。
- JVMテスト45件成功。うち自動維持の状態遷移・競合テスト23件。
  接続待ち中・読み取り直後の復元/OFF、OFF→ON、古い手動要求の無効化、
  進行中の手動復元とトグル変更、タイムアウト、Worker停止、重複トリガー、
  不明値、既定値0、保存失敗を含む。
- 専用Pixel 6aエミュレーターで27件成功
  （`app/build/auto-maintain/ui-tests.txt`）。新規8件は設定画面2件、
  WorkManager5件、Shizuku接続キャンセル1件。残りは既存のホーム、画面遷移、
  アンインストール前の案内、更新画面、IntDev開発者設定。
- 英語・font_scale=1.5で新規設定画面2件も成功
  （`app/build/auto-maintain/ui-tests-en-large.txt`）。
- 日本語の有効/一時停止表示と英語の大きな文字のキャプチャを目視確認。
  説明は折り返され、ONのままの一時停止が表示され、スイッチは操作可能。
- Lintはエラー0件・警告31件。追加分は`SharedPreferences.edit`のKTX提案と、
  ApplicationのContextを保持するプロセス共通ランタイムへのStaticFieldLeak警告。
  ランタイムは`applicationContext`だけを保持し、ActivityのContextは保持しない。
- WorkManagerの実登録・KEEPによる重複防止・キャンセル・通常Workerの生成と実行を検証。
  設定を再読み込みしたWorkerの一時停止保持と、キャンセル時の接続終了も検証。
  マージ後ManifestにWorkManager標準の再スケジュール受信機と起動権限が存在することを確認。
- QSの解除/復元選択と共通restoreの一時停止は模擬ポリシーで検証し、
  通知の`RestoreReceiver`が同じrestoreへ接続されていることをコード確認した。
  実機QS・通知からのShizuku音声変更の試験は行っていない。
- `git diff --check`成功。実機へのインストール、Release公開、コミットは行っていない。
