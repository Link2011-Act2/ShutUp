# Settings / AboutのPyrite M3Eライト配色（2026-10-19）

基準はShutUp `1a18497`（開始時の作業ツリーはclean）、依存は公開JitPack版Pyrite `43557c8`のままです。

## 原因と修正範囲

`ShutterTheme`は外側の`PyriteTheme`が生成したColorSchemeを、内側の`MaterialExpressiveTheme(colorScheme = colorScheme)`で全画面にわたり上書きしていました。その結果、ライトのcanvasがPyriteの`surfaceContainerLow`ではなくShutUp従来の`surface`となり、Dynamic OFFでは元の紫系パレットが使われていました。

- 上書き前のPyrite ColorSchemeをprivateなCompositionLocalへ保持し、設定・Aboutのライト画面だけに適用します。固定HEX、色の加工、依存版変更はありません。
- 一つの`PyriteScreenTransition`の**各遷移先の内側**で`ShutterDestinationTheme`を適用します。遷移中も出る画面・入る画面がそれぞれの配色を保持します。
- Home・更新詳細・ライセンス画面は従来のColorScheme、ダークは全画面で従来のColorSchemeを維持します。
- MaterialThemeの色だけを切り替え、ShutUpのTypography・Shapes・MotionSchemeを継承します。余白、角丸、文字サイズ、callback、ViewModel、Shizuku、設定保存、更新処理、ActivityのInsets修正は変更していません。

## 実測色値

設定・About共通。背景は`surface`（Pyriteでは`surfaceContainerLow`）、カードは`surfaceContainerLowest`です。修正後PNGの実際の塗りと、同じ端末上の公開PyriteThemeの全ColorSchemeを検証しています。

| Dynamic | 変更前 背景 / カード | 修正後 背景 / カード | Pyriteカタログ基準 |
| --- | --- | --- | --- |
| ON | #FAF8FF / #FFFFFF | **#F4F3FA / #FFFFFF** | 一致 |
| OFF | #FEF7FF / #FFFFFF | **#E2EBF0 / #F2F8FB** | 一致 |

Dynamic OFFのprimaryもPyrite本来の`#0E5E76`となります（以前は`#6750A4`）。ONのprimaryは`#495D92`で変わりません。ONの具体値は検証AVDの壁紙・システム配色に依存します。

[カタログとの実画素・色値照合4件](catalog-color-checks.json)、各条件の`*-before.json` / `*-after.json`、[画素比較20画面](image-metrics.json)を保存しています。カタログの基準値はPyriteの`docs/reviews/m3e-light-2026-10-19/applied/applied-metrics.json`です。

## 比較画像

すべて左が変更前、右が修正後。同じAPI 35 / Pixel_6a / 720×1280px / 320dpi / en-US / 文字100% / 壁紙で撮影しました。PixelCopyで描画同期し、OSバー背景を除く上下各60pxを同じように切り出しています。

| Dynamic | 設定 | About |
| --- | --- | --- |
| ON | [比較PNG](light-dynamic-settings.png) | [比較PNG](light-dynamic-about.png) |
| OFF | [比較PNG](light-static-settings.png) | [比較PNG](light-static-about.png) |

Home・更新詳細・Homeへ戻った画面は明暗×Dynamicの4条件でそれぞれ**835,200画素中の差分0**。ダークの設定も両条件とも差分0です。ダークAboutの差分は両条件とも437画素、範囲は(x=471..530, y=713..732)の自動生成Build文字列だけです。Build入力ソースの更新による時刻/hash変更で、配色・配置は維持しています。

## 検証結果

- APK・テストAPK・JVM・LintをまとめたGradleビルド成功（3分41秒）。JVM **187件成功、失敗/エラー0**。Lint **エラー0、既存と同数の警告48件**。
- 変更前の配色・撮影テスト4件成功（59.349秒）。[ログ](before-ui.txt)
- 修正後UI **15件成功、失敗0**（78.648秒）。[ログ](after-ui.txt)
  - `PyritePaletteTest` 4件：明暗×Dynamic ON/OFF。Settings/Aboutの全カラーロール、Home/update/licensesの従来配色、全5遷移先のTypography・Shapes・MotionSchemeの同一参照を確認。実際のHome→設定→About→更新詳細→About→設定→Homeで撮影・背景とカードの塗りを検証。
  - `NavigationTest` 2件：進む/戻る、途中反転、Home状態保持。
  - `PyriteActivityRestorationTest` 1件：連続2回のActivity再生成とスクロール位置・viewport保持。
  - `PyriteAboutBehaviorTest` 2件：更新失敗/再試行/取消/重複防止、ダイアログ・スクロール状態復元。
  - `AppUpdateUiTest` 6件：更新詳細、通知、ダウンロード操作等。

APK：`app/build/outputs/apk/debug/app-debug.apk`（1.1.1-IntDev、36,631,510 bytes）。SHA-256：`CA764B33EF4D980FF6B90931794B81FB104B15FCA8C30FCA00FDF3C6640710A8`。

今回の追加テストはオフラインの固定更新データを使い、実際のShizuku制御やダウンロードを発火させていません。他の壁紙/端末/APIでの全組み合わせは未検証です。製品のDynamic Colorは引き続き公式APIに従います。

一時ログ・原寸画像は`build/pyrite-palette/{before,after}/`、ビルドログは`build/pyrite-palette/after-build.txt`にあります。再実行はJDK21で通常の`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`後、専用AVDへAPKを入れ、以下を実行します。

```powershell
adb -s emulator-5580 shell am instrument --user 0 -w -e timeout_msec 60000 -e palettePhase after -e class 'jp.linkserver.shutter.ui.PyritePaletteTest,jp.linkserver.shutter.ui.NavigationTest,jp.linkserver.shutter.ui.PyriteActivityRestorationTest,jp.linkserver.shutter.ui.PyriteAboutBehaviorTest,jp.linkserver.shutter.ui.AppUpdateUiTest' jp.linkserver.shutter.test/androidx.test.runner.AndroidJUnitRunner
```

README、Pyrite本体、JitPack/Maven設定、他2アプリには変更していません。Commit/Pushは行っていません。
