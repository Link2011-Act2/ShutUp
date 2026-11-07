# Pyrite / Elief統一と転送UX改善の検証記録

主要な配色・アイコン・許可UX・進捗表示の修正を実装し、公開依存へ反映しました。追加のRange途中再開は未完了です。

## 変更内容

旧構造はSettings / Aboutの一部だけにPyriteの配色を適用し、Updateには別のShutUp配色を適用していました。
現在はルートの `ShutterTheme` で公開PyriteThemeを1回適用し、Home / Settings / About / Update、通知、Dialog、遷移中の両画面が同じテーマを継承します。
旧配色用のCompositionLocal・画面別ラッパー・未使用Typographyを整理しました。PyriteのTypography / Shapes / MotionSchemeを上書きしていません。

| 対象 | Light | Dark |
|---|---|---|
| Elief / ホストのカード | surfaceContainerLowest | surfaceContainerHigh |
| 画面・固定フッターのキャンバス | Pyriteのsurface | Pyriteのsurface |
| 更新確認Dialog | 親のMaterialTheme、標準Dialogロール | 同左 |

カード形状はPyriteのlargeです。通知Banner、Release Notes、履歴の外側、インライン操作領域、保持InstallRequestカードがLocalUpdateCardStyleを使います。
Elief既存の固定フッターと履歴内側のsurfaceは、同じPyrite ColorSchemeのキャンバスとして維持しています。独立した旧ColorSchemeは残していません。
PyriteScreenTransitionのアニメーション・方向・ナビゲーションを維持しています。

Goldy標準 `elief_update.xml` をMaterial Symbols Outlinedの `browser_updated` に統一しました。
ShutUp / Schedulerの参照ベクターと公開AARのviewport・group変換・pathを機械比較し、一致しています。
標準Icon / Banner / Dialogが同じリソースを参照し、MaterialThemeのtintを継承します。
`elief_refresh.xml` は変更せず、GoogleのApache2.0帰属とライセンス全文をAAR / sourcesに含めています。
ShutUp側の3つのicon指定と未参照Drawableを削除しました。

共通DownloadProgressFormatterをEliefへ追加しました。1000進数のB/KB/MB、KB/MBと進捗率の小数2桁、ロケール別の小数・%表記、未知サイズ、異常値、Long境界、未完了時の99.99%上限を扱います。
転送・検証ロジックは変更していません。

## 許可・実行管理

設定からのActivity resume時と操作前に、Gold経由で実際のcanRequestPackageInstallsを再判定します。
未許可は設定を開く明確な1ボタンです。許可済みなら未取得はDownload、検証・準備済みはInstallとなり、再度設定を開きません。
設定からのキャンセル、二重タップ、プロセス終了、準備済み要求の復元を扱います。
許可付与だけではインストーラーを自動起動しません。明示操作で一度だけ続行します。

UpdateDownloadViewModelの寿命から転送を分離し、アプリ状態を保持するRuntimeとAndroid実行管理へ移しました。
API34以降はUIDT Job、API28〜33はForeground WorkManagerの経路です。
転送中通知は進捗・詳細への復帰・キャンセルを提供します。完了後はReadyで止まり、ユーザーのInstall操作を待ちます。
バイト転送・digest・APK署名・packageName・versionCode・versionName・prepare・launchは公開Goldの単一経路です。
保持URIをActivity復帰で解放しません。曖昧なHandedOffは自動再実行せず、従来の明示的な確認からのみ再度開けます。

旧APKが準備済みでも新しいReleaseは選択できます。古い要求とURIは保持カードに残します。
取消完了まで新しい転送を受け付けず、古い通知ticketで新しい転送を取り消せないようにしています。
Readyは終了処理完了後に公開し、表示直後の最初の操作も受け付けます。

HTTP Rangeによる途中バイト再開は未実装です。現在は中断後のメタデータと状態を復元し、明示的に最初から再取得します。
画面にはその動作を表示します。OSによる停止・force-stop後も必ず転送し続けるとは扱いません。
Range実装の条件と残タスクは `implementation.md` に記録しています。

## 公開依存

- Pyrite: `43557c8`（変更なし）
- Goldy開始HEAD: `28d66246c410ebb7ab95f983a3a11de044ccb1b8` / master
- アイコンのコミット: `32ec799d85f76f21954d4f487e691e9ddaa0bd02`
- 最終公開SHA: `847d098cd1ddff7cc740d8189c25a1f854e23b28`
- ShutUp開始・終了HEAD: `d2aee9f9062517b5e0ac219392771dc3cee3d8b9` / main
- ShutUp依存: `com.github.Link2011-Act2.Goldy:elief:847d098cd1ddff7cc740d8189c25a1f854e23b28`

初回の新成果物取得はタイムアウトし、JitPack APIはnoneでした。同じ時点で旧SHAのPOMはHTTP200でした。
その後、新SHAのビルドログ進行とstatus=okを確認しました。旧SHAの成功を新SHAの成功として数えていません。
Gold / EliefそれぞれのAAR・POM・Module Metadata・sourcesの計8成果物を公開URLから取得し、座標・推移依存・サイズ・チェックサム・LICENSE/NOTICEを検証しています。
独立Consumer3構成（Gold単独、PyriteなしElief、EliefのPOM経路）でDebug / Release / R8 / 両Lint / Manifestを確認しました。Gold / Eliefの最初の2構成はrefresh-dependenciesを使用しています。

旧公開af5f415 AARに対して、Goldの107型/761記述子、Eliefの25型/237記述子に欠落がありません。
開始時のGold core 24ファイルはハッシュ一致で、今回の変更による署名・版名・APK検証の緩和はありません。
ShutUpのAppUpdateViewModel、設定・キャッシュ移行、IntDev設定のソースも開始時とハッシュ一致です。
included buildは開発検証だけに使用し、最終settingsには残していません。

## 検証範囲と限界

環境はWindows / PowerShell / JDK21、AGP9.4.1、Gradle9.6、ShutUp compileSdk37 / targetSdk36 / minSdk28です。
物理端末は操作せず、専用API36エミュレーターと読み取り専用API28 AVDで検証しています。
Gold JVM129件、Elief JVM23件、Demoの最終UI20件、アイコン段階の通知UI9件が成功しています。
Gold / Elief / DemoのDebug・Release・Demo R8・関連Lint、独立公開Consumer3構成も成功しています。

ShutUpの具体的な最終テスト件数、Light / Dark / Dynamic別の結果とAndroidバージョン別結果は末尾とresults.jsonに集計します。
テストは削除・無効化していません。狭幅の旧テストは、画面外の項目をスクロールして操作・確認する形へ修正しています。
失敗した実行ログもbuild内に残しています（取消関数解決、Ready公開競合、ビルド中APK読み取り、API注釈Lint、バックグラウンドActivity復帰手順、残存したテスト要求、狭幅テストの画面内前提）。

正式署名鍵や秘密情報には触れていません。署名付きRelease APKの作成と本番更新のインストール成功は確認していません。
インストーラー呼出テストはIntentを記録し、OSでのインストール成功とは区別しています。
転送結合テストはHTTPのバイト供給だけをテスト用に差し替え、実際のAndroidジョブ・通知・APK inspector・digest・署名検証・leaseを使用します。
権限テストは実PackageManager/AppOpとプロセス再起動を使いますが、設定アプリのスイッチを画面操作するE2Eではありません。
端末メーカー固有の省電力制限、実通信切断と復帰、OS自発停止・端末再起動、通知拒否時のシステムUI、全Androidバージョンでの実動作、正式鍵のAPK更新は未検証です。

## 保存と再開

Goldyのみ許可された2コミットをPushしました。ShutUpはローカル差分で保持し、Commit / Pushしていません。Tag / Releaseは作成していません。
READMEは編集していません。Pyrite / Trickuild / Scheduler / Glyph / BeastLocatorのソースは変更していません。
初期差分とハッシュ、開発時のVM原本、ビルド・テストログを各build内に残しています。

再開時はgit statusとresults.jsonを確認し、公開SHA依存を保持してください。Rangeを実装する場合はGold側の永続転送プロトコルとサーバー試験を先に実装し、全体検証成功後だけprepareに渡してください。
再現スクリプトは同ディレクトリにあります。実行中ビルドとAPKインストールを重ねず、明示的な検証用emulator serialを指定してください。

## 最終集計

| 検証 | 結果 |
|---|---|
| ShutUp JVM | 190件成功 |
| 公開依存 Debug / Release R8 / Debug・Release Lint | 成功（Lintエラー0） |
| Lint警告 | Debug 62件 / Release 63件 |
| 通常UI | 20件成功、Light/Dark × Dynamic ON/OFF |
| 320dp・fontScale1.8 UI | 20件成功、同4配色と操作・遷移・復元 |
| 実Activity Dark再生成 | 2件成功 |
| 転送回帰 | 拒否権限1件＋許可時8件成功（設定移行を含む） |
| 許可復帰・プロセス終了・保持要求 | 10段階すべて成功 |
| API36 UIDT | Home・他アプリ・画面OFF・再生成・通知取消・完了・手動Install成功 |
| API28 Foreground Worker | 実動作テスト成功、プロセス復元2段階成功 |

終了記録: 2026-11-07T18:05:03+09:00 (JST)

**結論 B：一部未完了です。** 主要4件とAPI36のバックグラウンド継続は実装・検証済みです。Range途中再開と上記の未検証環境・本番インストール成功は完了扱いにしていません。

## Goldy変更ファイル

- `app/src/androidTest/java/jp/linkserver/projectgoldy/DefaultUpdateIconTest.kt`
- `app/src/androidTest/java/jp/linkserver/projectgoldy/UpdateScreenTest.kt`
- `app/src/main/java/jp/linkserver/projectgoldy/FixtureUpdateViewModel.kt`
- `docs/download-progress.md`
- `docs/third-party-licenses.md`
- `docs/update-icons.md`
- `elief/src/main/java/jp/linkserver/goldy/compose/DownloadProgressFormatter.kt`
- `elief/src/main/java/jp/linkserver/goldy/compose/UpdateScreenLayout.kt`
- `elief/src/main/java/jp/linkserver/goldy/compose/UpdateStrings.kt`
- `elief/src/main/res/drawable/elief_update.xml`
- `elief/src/main/resources/META-INF/elief/MaterialSymbols-APACHE-2.0.txt`
- `elief/src/main/resources/META-INF/elief/NOTICE`
- `elief/src/test/java/jp/linkserver/goldy/compose/DownloadProgressFormatterTest.kt`
- `verification/verify-update-icon.py`

## ShutUp変更・削除・追加ファイル

- `app/build.gradle.kts`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/AppUpdateUiTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/EliefThemeParityTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/PyriteActivityRestorationTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/PyritePaletteTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/GoldyTransferIntegrationTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/GoldyTransferProcessTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/InlineTransferExecution.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/UpdateBackgroundExecutionTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/UpdatePermissionRecoveryTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/update/UpdateTransferProcessRecoveryTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/jp/linkserver/shutter/MainActivity.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/AboutScreen.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/AppUpdateUi.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/GoldyUpdateStrings.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/ShutterApp.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/Theme.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/Type.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/UpdateOverviewContent.kt`
- `app/src/main/java/jp/linkserver/shutter/update/UpdateDownloadViewModel.kt`
- `app/src/main/java/jp/linkserver/shutter/update/UpdateTransferExecution.kt`
- `app/src/main/java/jp/linkserver/shutter/update/UpdateTransferRuntime.kt`
- `app/src/main/java/jp/linkserver/shutter/update/UpdateTransferSnapshot.kt`
- `app/src/main/res/drawable/ic_browser_updated.xml`
- `app/src/main/res/values-ja/goldy_transfer.xml`
- `app/src/main/res/values/goldy_transfer.xml`
- `verification/theme-unification/build-with-local-goldy.ps1`
- `verification/theme-unification/implementation.md`
- `verification/theme-unification/report.md`
- `verification/theme-unification/results.json`
- `verification/theme-unification/run-transfer-ux.ps1`
- `verification/theme-unification/run-ui-matrix.ps1`

削除は旧TypographyのType.ktと重複ic_browser_updated.xmlです。詳細設計と再現方法はimplementation.mdを参照してください。
