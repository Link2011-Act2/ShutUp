# Pyrite導入記録 — 2026-10-19

ShutUp `34241e9`からの未コミット変更。固定依存は `com.github.Link2011-Act2:Pyrite:43557c8`（完全SHA `43557c8e2c9e669dd6675d08d733db0610e53442`）。コミット・Push・タグ・Releaseは行っていません。

## 置き換えと保持する責務

| 元実装 | 採用した公開API / アプリに残した責務 |
| --- | --- |
| SettingsEntry / SettingsItemSurface / SettingsSwitch | SettingsGroup、SettingsNavigationItem、SettingsSwitchItem。安定keyで可視行を登録。保存先・設定キー・callbackは維持 |
| AutoMaintainSettingsSection | supportingContent付きSwitch。enabledとarmed、一時停止表示を維持 |
| GalaxyControlSettingsSection | ON前の確認・取消、OFF処理、`!busy && !hasChange`、復元条件を維持 |
| DeveloperUpdateSettingsSection | 2行 / 5行の連結。更新2行と環境模擬3行の無効化条件を区別 |
| AboutOverviewMosaic | AboutInfo、AboutOverviewContent、AboutAttribution。Channel / Build / Version、詳細ダイアログ、上流リンク、ShutUpのアイコンを渡す |
| About更新・アクションカード | AboutUpdateCard / AboutActionGroup。Coroutine・重複防止・CancellationException・結果・latestTag・画面遷移はアプリに保持 |
| ScreenScaffold | PyriteScreenScaffold / PyriteBackButtonへのアプリ内委譲。戻る処理・actionsは既存のまま |
| ShutterNavigationのAnimatedContent | 単一のPyriteScreenTransition。screenDepth、画面ID、戻り先、BackHandlerはアプリに保持 |

独自AboutOverview.kt / SettingsComponents.ktと未使用chevronを削除。OSS画面へPyriteのMIT表示と帰属ファイルを同梱。NYSL等の既存表示は維持しています。

ViewModel、maintain/、update/、Shizuku制御、Preferences、設定キー、署名、版・BUILD_NUMBERの生成処理は変更しません。MainActivityの変更はWindow初期化とInsets配信のみ。AppUpdateUiの変更はConfigurationに追従するLocalResourcesによる表示文字列取得のみです。

## 依存と単体ビルド

- compileSdk 37、targetSdk 36、minSdk 28。
- Compose本体・tooling・UIテスト1.12.0、Material 3 1.5.0-beta01、Icons 1.7.8。古いCompose BOMを除去。
- AGP 9.4.1 / Gradle 9.6.0 / JDK 21 / Compose Compiler 2.3.10を維持。stdlibは2.3.10へ解決。
- JitPackは対象groupだけ許可。project依存・includeBuild・mavenLocal・隣接Pyrite参照はありません。
- 匿名AAR / POM / sources / module取得と、POMのみを使うPyrite独立consumerのビルドに成功。

```powershell
# JDK 21、Android SDK platform 37（platforms;android-37.0）。
# SDKはANDROID_HOME、または自分のlocal.propertiesで指定。
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
# IntDev限定Runtime統合テスト用。通常の版設定は変更しない。
$env:VERSION_NAME = '1.1.0-IntDev_migration'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
Remove-Item Env:VERSION_NAME
```

## 表示差分と未統合部分

- 設定順、外側20dp/18dp、セクション間28dp、設定連結22/6dp・2dp間隔を維持。
- Aboutの92dpアイコン、20dp角丸、3dp間隔、48dpブリッジ、Channel / Build / Versionの構造と操作を維持。
- 23sp/29sp MediumのTop App Bar、元の戻る矢印、300ms・幅1/5スライド、220ms+40msの入場fade、140msの退場fadeを維持。
- 外側PyriteThemeはスタイル・明暗コンテキストを供給。内側のMaterialExpressiveThemeでShutUpの配色・Typography・元の公式Shapes()を維持。公式alpha10/beta01のソースで、テーマの入れ子ではshapeが継承されることを確認して明示しました。
- ライトのPyriteカードはsurfaceContainerLowest（従来High）。設定見出しはPyrite標準。Aboutアプリ名は27sp/32spから30sp/36sp。自由な形状・文字サイズの補正APIは追加していません。
- モザイク最大幅520dp。狭幅・文字拡大では64dpアイコンと縦ヘッダーになり、下段は内容で伸長。従来の固定高は強制しません。
- 通常の360dp幅では下段最小高が従来160dp→158.5dpになり、更新セクション開始位置に3pxの差があります。無効なSwitchはPyriteのdisabled表現へ揃います。
- ダウンロード画面はbottomBar付き独自Scaffoldを維持。指定PyriteにはbottomBar slotがなく、二重Scaffoldでは代用しません。Home固有のTop App Barも維持。

## Activity再生成

API 35、720×1280 / 320dpiで計測。修正前：scroll 827→767px、maxは前後871px、Android contentは720×1280/y=0→720×1176/y=56。systemBarsは上56px+下48px。viewportはy=220→164に変化。

Android 15のpreserved Window初期化と最初のCompose Insets配信の問題です。初期Insets未配信でviewportが104px広がるとmaxは871−104=767となり、827が制限された値と一致します（初回maxの値は計算による推定。根拠はgeometry-before.txtとPyriteの既存調査）。失われる量が60pxなのは復元位置が末端より44px手前のためです。

Pyriteカタログで検証済みの順序を適用：enableEdgeToEdge前にsetDecorFitsSystemWindows(false)、ON_CREATE後のonPostCreateでWindowの実Insetsを同期配信。delay・固定px補正・独自Saverは導入しません。実Activityの連続2回再生成を、期待値を緩めず位置・viewport・ボタン位置で検証します。

## 結果

| 検証 | 結果 |
| --- | --- |
| JitPack固定SHA | 匿名AAR/POM/sources/module取得成功。POMだけでPyrite独立consumerをビルド、2分42秒 |
| ShutUp通常版 | assembleDebug / assembleDebugAndroidTest / testDebugUnitTest / lintDebug成功。最終ログ verified-final-build.txt |
| JVM | 187件、失敗0・エラー0 |
| Lint | エラー0・警告48（既存コード・依存更新提案等）。チェックの無効化・baselineによる隠蔽なし |
| 通常版UI | 最終APKで36件すべて成功、97.123秒。4配色条件、150%文字、確認・取消・更新、遷移、保存状態、実Activity再生成を含む |
| IntDev UI | 14件すべて成功、45.785秒。開発者設定、実Runtime、Galaxy確認・復元、自動維持、更新表示。最終ソースの独立コピーでもRuntime統合2件を再確認し成功（23.191秒） |
| 独立ShutUp | 最終ソースの新規コピー、40タスクを--no-build-cacheで実行、43秒で成功 |
| 元UIの比較用再撮影 | 製品ソース34241e9 + 同じ描画同期テストのみの一時コピー。4件成功、31.642秒 |

独立ShutUpは`build/pyrite-migration/standalone-final-43557c8`。Pyriteソース・.git・local.properties・既存プロジェクトキャッシュはコピーしていません。SDKはANDROID_HOMEから指定。Gradleユーザーキャッシュは共有しており、完全空キャッシュ検証とは区別します。未コミット成果のため、リモートからの最新ShutUp Clone検証はまだ実施できません。確認後のCommit/Pushにより、通常のCloneへ反映されます。

最終通常版APKは`app/build/outputs/apk/debug/app-debug.apk`。versionName=1.1.0-Release、versionCode=4、Build=Lethe-v1.1.0-261019-1747-5bc。debug署名の検証用APKであり、署名Releaseビルドは未実施です。

- 公開AAR SHA-256: `CB3FB9B3D2B2DBFC402F6F33D30EB110A206A47C02A212E0AD0A72F649D44545`
- 最終APK SHA-256: `0BE0CB6011BB5062B81DEB01CE314DE6402208504A2B0256865B12C16B3C8BF6`
- sourcesには15 Kotlinファイル。AAR metadataはminCompileSdk=37、minor/extension=0。
- AboutのcheckUpdates関数が元実装と一致し、ViewModel・maintain/・update/の差分がないことも確認。

スクロール修正後は連続2回ともvalue=827、max=871、viewport=(40,220)-(680,1196)、content=720×1280/y=0を維持。[修正前](geometry-before.txt)・[修正後](geometry-after.txt)に実ログを保存しています。

初回試行ではAVD起動直後のOS assets構成変更によるActivity再生成でテスト用Compose内容が失われ、移行前後とも先頭1件が失敗しました。安定後に再確認済みです。またRelease版でIntDev専用テストを実行した失敗は、適切なIntDev APKで検証しました。画面キャプチャの遷移前フレーム混入は、仮想時計400ms前進＋PixelCopyによる描画同期で再撮影しています。製品のアニメーション時間は変えていません。

### 比較画像

API 35 / 720×1280px / 320dpi（360dp幅）/ en-US / 100%文字。同じAVD・壁紙・表示条件。左右が移行前 / 移行後です。時刻・OSバーを比較から除くため上下60pxを両側とも切り出しています。Build文字列は生成時刻・入力ソースの違いで変わります。

| 条件 | 設定 | About |
| --- | --- | --- |
| Light / Dynamic ON | [比較](light-dynamic-settings.png) | [比較](light-dynamic-about.png) |
| Dark / Dynamic ON | [比較](dark-dynamic-settings.png) | [比較](dark-dynamic-about.png) |
| Light / Dynamic OFF | [比較](light-static-settings.png) | [比較](light-static-about.png) |
| Dark / Dynamic OFF | [比較](dark-static-settings.png) | [比較](dark-static-about.png) |

[画素比較](image-metrics.json)：Homeは4条件すべて835,200px中の差分0。Darkの設定は両条件とも300pxの差分で、Google公式chevronと元SVGの形状差です。Aboutの文字組み・下段高・生成Build、ライトカードの色差は上記に明示しています。全画像が画素一致したという判定ではありません。

150%文字では縦ヘッダー、更新カードの縦積み、Channel/Version/OSSの操作まで確認。検証はCompose LocalDensityによる文字拡大で、OS全体の文字拡大・TalkBack実聴・日本語/RTL・広幅/横画面・API28/31/33・Samsung実機のShizuku操作は今回未検証です。

再実行はJDK21/SDK37でビルド後、専用エミュレーターにapp-debug.apkとapp-debug-androidTest.apkを入れ、次のように実行します。IntDevのRuntimeテストは上記のVERSION_NAMEでビルドしてから実行します。

```powershell
adb -s emulator-5580 shell am instrument --user 0 -w -e timeout_msec 60000 -e class 'jp.linkserver.shutter.ui.PyriteMigrationTest,jp.linkserver.shutter.ui.PyriteAboutBehaviorTest,jp.linkserver.shutter.ui.PyriteActivityRestorationTest,jp.linkserver.shutter.ui.NavigationTest' jp.linkserver.shutter.test/androidx.test.runner.AndroidJUnitRunner
```

詳細な一時ログ・全撮影画像は`build/pyrite-migration/`、JitPack consumerはPyrite側の`build/jitpack-shutup-43557c8.txt`です。現時点ではCommit/Pushを行わず、APKと表示差分のユーザー確認を待ちます。
