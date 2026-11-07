# Goldy 導入検証 — 2026-11-06

作業開始 22:08 JST、期限 23:50 JST。Goldy の公開配布・独立 Consumer 成功を確認してから ShutUp の実装を開始した。

## 開始状態と保護

- Goldy: `master` / `e1d9ecd88e75f28b855ecd568e2e675486d8750e`、clean、公開 GitHub HEAD と一致。
- ShutUp: `main` / `1a18497cfbe62eac20ecf3ba8264cb8daf163fac`、公開 GitHub HEAD と一致。
- ShutUp 既存変更: `app/build.gradle.kts`, `settings.gradle.kts`, `AboutScreen.kt`, `ShutterApp.kt`, `Theme.kt`, `verification/pyrite-migration/report.md`。未追跡は `PyritePaletteTest.kt` と `verification/pyrite-light-palette/`。
- 既存24ファイルのコピー・SHA-256、binary diff、status を ignored `.gradle/goldy-baseline-20261106-2208/` に退避。22:38時点で欠落0、21ファイルのハッシュ一致。変更を重ねた3ファイルは Gradle2件と `ShutterApp.kt` のみ。Pyrite の既存差分を維持した。
- Windows / PowerShell、JDK21.0.7、Gradle9.6.0、AGP9.4.1、SDK37.0。ShutUp Kotlin Compose2.3.10、minSdk28、targetSdk36、JVM17。
- 既存 Pyrite `43557c8`、Trickuild `2926f49060e58e2701f97de3c92322be26ed9811`、Compose1.12.0 / M3 Expressive1.5.0-beta01 とテーマ・PyriteScreenTransition を維持。

## 完了した実装範囲

- 公開座標 `com.github.Link2011-Act2.Goldy:elief:e1d9ecd88e75f28b855ecd568e2e675486d8750e` を追加。Gold は同 SHA の推移依存。Goldy 用 JitPack exclusive repository を追加し、ローカル project/Maven へ置換していない。
- 実通信は Gold の `GitHubReleaseClient`、候補・履歴選択は `ReleaseSelector` / `UpdatePolicies.shutUp()` を使用。
- 既存 `AppUpdateRepository` / `AppUpdateViewModel` はホスト側アダプタとして維持。確認中・失敗の状態を Elief に接続。旧 `github_release_updates` の保存先・全キー・JSON形式、8時間間隔、バージョン変更・時計巻き戻り、成功時だけの保存、dismissed tag の大小文字無視、banner の一時非表示、手動強制確認、IntDev 専用設定を維持。Goldy の別形式の標準ストアへコピーしていないため、旧データを消す移行マーカーも不要。
- Banner/Icon/改善済み `UpdateCheckDialog`/詳細画面を Elief に移行。候補ありでは View update が主操作、再確認は副操作。単なる自動確認でダイアログを開かない。About で候補を得たときは既存どおり詳細へ進む。
- 既存テーマ・アイコン・日英ラベル・ファイル名・Markdown・履歴・戻る操作を接続。追加の画面遷移アニメーションなし。履歴の見た目・内部 testTag は Elief のものへ変わるため、既存 UI テスト6箇所の locator を更新した。テストを削除・無効化していない。
- UI 用の host metadata ID は APK の検証結果やインストール権限ではない。APK 処理には引き続き元の ShutUp release を渡す。

## APK 移行の保留と安全性

`UpdateDownloadViewModel.kt`、APK検証、既存 FileProvider 設定、インストーラー引き渡しは変更していない。

ShutUp は単独署名の candidate 履歴（複数署名なら現在署名）から、インストール済みアプリに `hasSigningCertificate` が認める証明書が一つでもあれば受理する。Gold は現在署名者集合・単独/複数署名区分の完全一致が必須で、履歴を表現・検証する契約がない。ShutUp の条件は前方 rotation だけに限定されているわけでもない。Gold の署名情報を偽造したり条件を緩めたりして同等動作を装わない。

APK選択も異なる。ShutUp は名前に shutup を含む最初の APK を優先するが、Gold の標準 ShutUp selector は prefix の単一候補 / 単一 APK を要求する。今回このアプリの選択は維持した。Gold の追加の size上限・release/versionName binding・URI lease の導入も未完了。

既存実装では packageName、versionCode 非降下、HTTP Content-Length（存在時）、digest（存在時）、署名条件を検査する。versionName と release の厳密 binding、複数署名者集合、APK署名の実認証、前方/後方rotationの整理、ファイル寿命の契約は次工程で扱う。固定 `cache/updates/update.apk` は次の転送開始時に削除されるため、インストーラーが読み終わる前の削除・上書きへの対処も残る。

インストーラーへ渡したことをインストール成功とは表示・記録していない。署名 rotation を含む実インストールは未検証。この報告は APK 経路まで含む完全移行の完了宣言ではない。

## 検証状況

- Goldy: 公開8成果物・metadata/source整合性、旧公開API/JVM linkage、Gold単独/Elief/stable/Expressive/POM-only の独立 Debug/Release/R8/Lint/Manifest が成功。Gold119 / Elief18 Unit Test成功。詳細は Goldy の `docs/publication-2026-11-06.md`。
- ShutUp Unit Test: 190件成功・失敗/スキップ0。旧 selector との1,840条件の候補・履歴・チャンネル・preview比較と APK 選択維持を含む。
- Debug APK: 成功。実アプリ merged Manifest で `.updates` と `.goldy.fileprovider` が固有 authority・非export・URI grant で共存し、install permission は元の1件。
- Release APK: `validateReleaseSigning` が設定不足で失敗（STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD なし）。検証を迂回せず、debug署名へ代替していない。
- 初回 Lint: `AsyncExecutionService.getService must not return null` という解析エンジン内部例外。次の試行では追加UIテストのR参照エラーと、履歴ラベルの設定変更追従エラーを検出し、いずれも修正した。その試行の Lint は同一 PSI 走査を10分以上継続したため、スレッド記録を保存して当該タスク所有の Gradle プロセスだけ停止。チェックを無効化せず、新しい Gradle プロセス・`--max-workers=1` で直列に再実行した。
- `testReleaseUnitTest` はこの構成に存在せず、最初の指定はタスク選択段階で失敗。Debug Unit Test は全件実行している。
- 最終 Debug APK/UIテストAPK/Unit Test: `ui-build.log` で成功（190件、失敗/スキップ0）。
- 最終 Release R8 / Debug Lint / Release Lint: `r8-lint-serial.log` で22:53ごろ成功。Lint は両方エラー0・警告57。警告の一括抑制や関連外の修正はしていない。
- R8 の最新 mapping は `app/build/intermediates/mapping/release/minifyReleaseWithR8/mapping.txt`（22:50生成）、最新 dex は同 `intermediates/dex/release/minifyReleaseWithR8/`。署名不足で APK packaging が未完了なので、古い `outputs/mapping/release/` のファイルを最新の証拠として代用していない。Gold/Elief/ホストadapterの mapping を確認し、最新の内部成果物を検証用ディレクトリへ保存した。
- API36 通常UI: 14件開始/14件成功/失敗・スキップ0。既存 AppUpdateUiTest 6、DeveloperUpdateSettingsTest 4、新規 GoldyMigrationUiTest 3、実 MainActivity の再生成テスト1。確認ダイアログ、詳細、展開済み履歴と戻り先の復元に成功。
- 旧保存形式を保存 → `am force-stop jp.linkserver.shutter` → 別 instrumentation 実行で復元: 2件成功。キャッシュ、無視済みタグ、IntDev設定、確認間隔、通知抑制を確認。APK転送・保留インストール要求のプロセス復元を試験したものではない。
- 320dp / fontScale1.8: 候補ありの主操作・再確認・失敗時の再試行2件成功。
- 初回キャプチャには emulator の System UI ANR が重なった。テスト結果と目視確認を混同せず、観測した Wait ボタンで復帰し、UI hierarchy からANR消失を確認。既存 AppUpdateUiTest 6件を再実行してすべて成功し、再取得した `screenshots-recovered/prompt.png` / `markdown.png` の主操作・再確認アイコン・Markdown・ファイル名・下部操作を目視確認した。
- 実機、API28での今回ランタイム、実APKのダウンロード・署名rotation・インストール許可/インストーラー完了、R8 APKの起動は未検証。

## 従来との差分

候補・履歴の選択、チャンネル許可、IntDev動作は1,840条件で旧実装と一致。APK選択・永続設定・通知の無視/一時非表示・間隔・手動/自動の意味も維持した。
通信は Gold の応答サイズ上限・厳格なID/URL検証・redirect制限に従い、不正な項目を旧parserのように部分スキップする代わりに応答全体を失敗として既存キャッシュを維持する場合がある。ShutUp の先頭100件という選択窓は維持。実API応答3リリース/13,656 bytesをHTTP200で取得し、ID契約を確認した。
UI は Elief のレイアウト、非操作channel表示、履歴件数、再確認副操作、ホストに追従するMarkdownへ変わる。Pyriteの画面遷移・ホストテーマは維持した。

## 今回の変更ファイル

`app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/java/jp/linkserver/shutter/GitHubReleaseChecker.kt`,
`ui/AppUpdateUi.kt`, `ui/ShutterApp.kt`, `ui/UpdateOverviewContent.kt`, `ui/GoldyUpdateStrings.kt`,
`update/AppUpdateRepository.kt`, `update/GoldyUpdates.kt`（ui/update は同 shutter package 配下）、
`app/src/main/res/values/goldy_updates.xml`, `app/src/main/res/values-ja/goldy_updates.xml`,
`app/src/test/java/jp/linkserver/shutter/update/GoldyMigrationTest.kt`,
`app/src/androidTest/java/jp/linkserver/shutter/ui/AppUpdateUiTest.kt`, `GoldyMigrationUiTest.kt`,
`GoldyActivityRestorationTest.kt`, `GoldyProcessRestorationTest.kt`、本報告と `results.json`。
機械可読な完全パス一覧・結果・ハッシュは `results.json` に記録する。

生ログ・APK・mapping は ignored `build/goldy-migration-20261106/` と `app/build/`。API36 は `Pixel_9_Pro_XL` を `-read-only -no-window -no-snapshot-save`、`emulator-5564` で起動。接続中の実機にはインストールしていない。

## 再開手順

1. 両リポジトリの status/HEAD と退避ハッシュを確認。既存差分を reset/clean しない。
2. 未解決の検証結果を本報告・生ログから確認し、必要なものだけ再実行する。
3. APK の署名履歴・複数署名・versionName binding・asset size・lease の仕様を先に設計し、移行前後の実署名 APK と鍵 rotation を試験する。安全な同等挙動を用意するまでは現経路を残す。
4. 正規の Release 署名設定を用意して `:app:assembleRelease` を実行。署名値をログへ出さない。

Commit/Push/Release/Tag は作成していない。Goldy/ShutUp 以外のリポジトリは変更していない。

作業終了: 2026-11-06T22:59:19+09:00（JST）。最終差分チェック成功、全変更ファイルを保存、専用エミュレーター終了、実行中のビルド・テストなし。23:40以降の継続工程へ入る前に部分完了として終了した。
