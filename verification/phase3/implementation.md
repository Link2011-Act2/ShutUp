# Phase 3: 永続HTTP Range再開

開始時のGoldyは `847d098cd1ddff7cc740d8189c25a1f854e23b28`、ShutUpは
`d2aee9f9062517b5e0ac219392771dc3cee3d8b9` です。前回の配色・アイコン・許可UX・
UIDT/WorkManager移行のローカル変更を保持したまま追加しています。
README、他アプリのソース、正式署名鍵は変更していません。

## 責務と保存先

Goldの標準DownloaderとResumable APIは一つの転送・検証処理を使います。
`filesDir/goldy-transfers` に、Release/Assetの厳密な同一性をキーにした `.part` と
Properties形式の `.meta` を保存します。識別子にはowner/repository、Release ID/tag、
Asset ID/name、正規URL、公称サイズ/digestをすべて含みます。取得済みサイズ、既知の
全体サイズ、状態、prefix SHA-256、強い検証子も記録します。

データと一時メタデータをfsync後、メタデータをatomic moveで置換します。プロセス終了で
余分な末尾が残った場合、確定済みprefixのサイズ/hashを確認してから切り詰めます。
破損は再取得へ倒し、prefix hashをAPKの正当性の根拠にはしません。
プロセスMutexとキャンセル可能なOSファイルロックで転送・復元・削除を直列化します。

完成APKは、全体検証後に従来の `cacheDir/goldy-apks` へ移します。Installerの限定した
受け付け先や、別の永続InstallRequest/URI leaseの保存先は維持しています。
転送清掃は有効な再開データと完成APK/leaseを消しません。旧形式のUUID部分ファイル、
孤立ファイル、破損レコード、一時メタデータだけを対象にします。上限は256MiBです。

## HTTPと検証

- 正規GitHub Asset URLから毎回接続し、許可済みHTTPSリダイレクトへRange/If-Rangeを引き継ぎます。
  一時的な署名付きURLは永続的な信頼元として保存しません。
- 強いETag、またはDateとの間隔で強いと確認できるLast-ModifiedだけをIf-Rangeに使います。
  弱いETagからの再開や、ETagを無視した日付への置換はしません。
- 206は開始位置・範囲・Content-Length・全体サイズ・検証子・encodingを検証してから追記します。
- 200は先頭から再取得し、416は完成と見なさず一度だけ安全な再取得へ切り替えます。
- 全体サイズ、全体SHA-256、公称digest、APK署名/PoR、packageName、versionCode、厳密な
  versionName/tag bindingをすべて通過して初めてVerifiedApkを作ります。
  prepareと明示launchでも再検証します。

## ShutUp側の統合

UIDT Job(API34+) / Foreground WorkManager(API28–33)の構成は維持しています。
`UpdateTransferRuntime` が保存位置を復元し、再開後の進捗は既存Eliefフォーマッターで
通知・画面へ表示します。Paused表示にも保存容量、明示的な続行・キャンセルを出します。
サーバーがRangeを無視して0からの再取得になった場合は説明を表示します。

通信障害、OS/コルーチン中断は部分ファイルを保持します。明示キャンセルは実行を停止・
joinした後、固定された転送対象の部分ファイルだけを削除します。新しい候補の部分ファイルと
取り違えません。Activity/ViewModelの終了は転送のキャンセルにしません。

プロセス再生成では状態と保存進捗を復元して明示続行できます。不明なHandedOff要求や、
バックグラウンド完了から勝手にインストーラーを起動しません。
OSがforce-stopやメーカー制限で実行を止めた場合の自動継続は保証しません。

## 再現方法と証跡

`run-range-process.ps1` は明示したemulator serialだけを対象とします。ホストの所有HTTP
サーバーが実際の署名済みDebug APKを配信し、Stage1で一部を保存、force-stop後のStage2で
別プロセスから206追記します。実AndroidApkInspector/ApkInstallerを使い、通知と保存進捗、
全体検証、読み取り可能なlease、Readyでの自動起動0回、明示二重タップでの起動1回を確認します。
テスト専用のloopback transport以外にセキュリティ検証の差し替えはありません。

実行例:

```powershell
./verification/phase3/run-range-process.ps1 -Serial emulator-5564 -Label unique-run
```

実行結果は `build/phase3/<label>/` のHTTP記録、APK検証記録、各Stageのinstrumentation出力に
保存します。公開切替前だけ、既存 `build-with-local-goldy.ps1` の開発用compositeを使います。
このスクリプトはfinallyでsettingsの元のバイト列を復元します。
最終検証は新しい公開JitPack依存だけで行い、ShutUpはCommit/Pushしません。

最終の公開SHA、API28/API36、JVM/AndroidTest、R8/Lintと未検証範囲は、このディレクトリの
`results.json` / `report.md` に記録します。途中の失敗ログを成功結果として扱いません。
