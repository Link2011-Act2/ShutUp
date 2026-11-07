# Goldy Phase 3 検証報告

作業終了時刻: 2026-11-07T23:18:37+09:00 (Asia/Tokyo)。

最終判定: Phase 3の実装・公開依存への反映と、API28/API36での実APK途中再開から
全体検証までの完了条件を満たしました。

## 公開版と作業状態

- Goldy開始HEAD: `847d098cd1ddff7cc740d8189c25a1f854e23b28` / master。
- 最終公開HEAD: `27692fc266e82f081406c85c9841226b4aca4733` / master。Commit/Push済み。
  途中の `480f64f` に加え、CDN期限切れ対応を追加した最終SHAを改めて検証しました。
- ShutUp HEAD: `d2aee9f9062517b5e0ac219392771dc3cee3d8b9` / main。Commit/Pushしていません。
- ShutUp依存: `com.github.Link2011-Act2.Goldy:elief:27692fc266e82f081406c85c9841226b4aca4733`。
  同SHAのGoldを推移解決。最終ビルドにincluded build / Maven Localはありません。
- 前回の配色・テーマ・更新アイコン・許可UX・バックグラウンド転送の変更は保持しています。
  READMEや他アプリのソースは変更していません。

## 実装

Goldの標準create/downloadとResumable APIを単一の処理に統一しました。
`filesDir/goldy-transfers` に、厳密なRelease/Asset同一性をSHA-256で識別する `.part` と
Properties形式の `.meta` を保存します。owner/repository、Release ID/tag、Asset ID/name、
元URL、公称size/digest、取得済みbytes、既知のtotal、状態、prefix hash、強い検証子を記録します。
一時的な署名付きリダイレクトURLは保存しません。

データfsync、一時メタデータfsync、atomic moveで確定します。クラッシュ末尾は確定prefixの
hash確認後に切り詰めます。プロセスMutexとキャンセル可能なOSファイルロックで直列化します。
清掃は有効な再開データを保持し、旧形式・孤立・破損・未確定メタデータを処理します。
上限256MiBと、完成APK/InstallRequest/URI leaseの別管理を維持しています。

| 応答・状態 | 処理 |
| --- | --- |
| 206 | 要求offset、Content-Range、length、total、encoding、検証子と実ファイルを検証して追記 |
| 200 | 追記せず先頭から取得。進捗リセット時は画面・通知に説明 |
| 416 | 完成扱いせず、確認したtotalの範囲で一度だけ正規URLから再取得 |
| CDN 401/403 | 正規GitHub URLから一度だけ署名URLを更新。拒否継続なら部分保持して停止 |
| 弱いETag・不適切な日付 | If-Rangeに利用せず安全に再取得 |
| 通信・OS/コルーチン中断 | 利用可能な部分データを保持 |
| 明示キャンセル | 実行停止/join後に当該Assetの部分データを削除 |
| ストレージ障害 | 再試行を繰り返さず停止し、回復可能なデータを保持 |

完成時はファイル全体を読み直し、size・SHA-256/digest・完全なAPK署名/PoR・packageName・
versionCode下限・厳密なversionName/tag照合を実施します。prepare/launchでも再検証します。
署名検証・Installer・FileProviderのソースは変更していません。HandedOffはインストール成功を
意味しません。Readyから自動でインストーラーを起動しません。

## 実APK・別プロセスの最終公開版検証

ホストHTTPサーバーで実際の署名済みShutUp Debug APKを配信し、UIDT/Foreground Workerで
部分取得した後にforce-stop、別PIDで再開しました。実AndroidApkInspector/ApkInstallerを使用。
以下の保存進捗とHTTP Range開始位置は実行スクリプトで一致を確認しています。

| API | 実行管理 | 再開offset / 完成サイズ | 結果 |
| --- | --- | --- | --- |
| 28 | Foreground WorkManager | 5,242,880 / 51,130,197 bytes | 206追記・全体検証・lease作成成功 |
| 36 | UIDT Job | 7,340,032 / 51,130,197 bytes | 206追記・全体検証・lease作成成功 |

両APIで、自動インストーラー起動0回、明示二重タップでの引き渡し1回、読み取り可能なURI、
部分ファイルの解消を確認しました。通知の保存進捗も既存Eliefフォーマッターと一致します。
証跡: `build/phase3/api28-public-27692fc/` / `api36-public-27692fc/`。

## テスト・配布

- JVM: Gold 157、Elief 23、Demo 9、ShutUp 190件。失敗0・skip0。
- 制御HTTP実ファイルテスト18件: 200/206/416、不正range/length/total/encoding、切断、
  timeout、弱い/変更ETag、Last-Modified、期限切れ/継続拒否、改ざん、Asset ID変更、
  digest不一致、明示削除、ストレージ障害等。別の実行から実ファイルを再読込します。
- API28/API36: 既存の許可復帰・Home/他アプリ/画面OFF/Activity再生成・通知キャンセル・
  プロセス復元・URI lease保持の転送回帰が成功。
- Gold Android署名/Installer/FileProvider、実HTTPS GitHub/CDN転送、cancel、404を両APIで確認。
  v3/v3.1、前方/逆行/欠損capability/改ざん/複数署名を含む14件のOS install試験が各APIで成功。
- Gold/Elief Debug/Releaseビルド、関連Lint、Demo Debug/Release R8/Lint成功。
  ShutUpは最終公開依存でDebug/AndroidTest APK、Unit、Release R8、Debug/Release Lint成功。
- Lintエラー0。既存の依存更新・KTX・catalog・style等の警告は残っています。
- 新SHAのJitPack status=ok、AAR/POM/Module Metadata/sources全8成果物を実取得し、
  座標・推移依存・size/checksum・ライセンスを検証。公開された新しい5ソースはコミットと一致。
  初回POM取得は60秒timeoutしましたが、その後同SHAで取得・検証できています。
- 新SHAの独立Consumer3構成（Gold単独 / Elief推移 / Elief POMのみ）が、
  refresh付き依存解決・Kotlin/Compose・Debug/Release R8・Lint・Manifest/provider共存を通過。
  ConsumerにPyrite依存がないことも検証しています。
- 旧公開AARとの比較でGold 718 / Elief 244の宣言済み公開記述子を維持。
  内部privateアクセス用のcompiler-generated synthetic bridge 4件は削除されています。
  名前だけで除外せず、ACC_SYNTHETICを実クラスで確認して別記しています。

## 配色・アイコン・許可UXの維持

Elief AARは旧847d098とSHA-256まで一致します。Pyriteを正本とする単一テーマ、Light/Dark/
Dynamic Color、カード、ダイアログ、browser_updatedアイコン、画面遷移を保持しています。
公開API28で320dp/fontScale1.8の21件と暗色Activity再生成2件が成功しました。
公開API36でも同じ狭幅21件・暗色2件が成功。先行API36では通常21件も成功しています。
Goldy Demo/Elief UI 22件もAPI36の320dp/fontScale1.8で成功しています。

Paused UIに保存容量と続行/キャンセルを追加しました。旧「Try again」期待値は現行の明確な
操作名へ修正し、Markdown字体は旧線形SP換算でなく実際のPyrite/Compose Typographyとの
一致を検証しています。製品のテーマ・Typographyをこのために変更していません。

## 変更ファイル

Goldyの変更15ファイルは、`results.json` のgoldy.changedFilesに記録しています。
中心はApkDownloader、新規ResumableTransfer/ResumeCheckpoint/ResumeHttp/ResumeStorage、
関連JVM/Android/Demoテスト、ABI検証スクリプト、`docs/phase3-progress.md` です。

今回のShutUp中心変更は、公開依存SHA、UpdateTransferRuntime/Execution、UpdateDownloadState、
UpdateOverviewContent、英日transfer文字列、AppUpdateUiTest、RangeApkFixture/RangeProcessTest、
`verification/phase3/` と既存theme-unification文書の参照更新です。
引き継いだ全ローカル差分の一覧はresults.jsonに分けて記録し、破棄していません。

## 制限・未検証

- 正式署名設定がないためShutUp `assembleRelease` は既存validateReleaseSigningで停止。
  Release R8は成功。正式鍵の取得・複製・debug署名への置換はしていません。
- 実機・メーカー独自制限、今回のAPI26/27、R8済み正式署名APKでの実インストールは未検証。
- ストレージを物理的に満杯にする試験や、2つの実Androidプロセスでの同時転送は未実施。
  Storage障害・OS lock競合/取消・二重操作は制御テストで確認しています。
- CDN期限切れ/Range異常は制御HTTPで検証。実GitHubで意図的に期限切れを起こす試験は未実施。
  接続timeoutは例外注入、読み取りtimeoutは実HTTPで検証しています。
- OS Job停止後の自動再スケジュールの全条件は未検証。force-stop/再起動からの明示再開は成功。
  OSのforce-stopやメーカー制限中に常時動作し続けるとは保証しません。

再現には `verification/phase3/implementation.md` と `run-range-process.ps1` を参照してください。
署名設定が必要な正式Release APK作成と、追加実機/OS条件の確認は上記の環境が利用可能になってから
実施できます。再開機能のために検証を緩和する必要はありません。
