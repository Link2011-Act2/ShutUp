# Goldy 完全移行作業 — 2026-11-07

この記録は11月6日の部分移行の続きです。前回の報告と結果JSONは履歴として保持しています。検証結果の最終集計は `full-migration-2026-11-07.json` を参照してください。

最終結果: Unit190件、関連Android28件、360dp/文字1.3倍の追加2件と最終バナー撮影テスト成功。Debug/Release Lintは各0 errors・61 warnings、Debug buildとRelease R8成功。正式署名Release APKのみ未生成です。

表示証跡: [バナー](2026-11-07-banner.png) / [主操作Dialog](2026-11-07-dialog.png) / [ダウンロード取消](2026-11-07-download.png)。通常幅と拡大文字で詳細・Markdown・履歴も目視確認しました。初期Activity遷移中の白画面を撮影した試行は、Compose時計と実描画の完了を待って撮り直しています。

## 開始状態と保護

- Goldy: `master`, `d4d9e1e9d561be9eccddda081385a5b8fa6f6157`。既存 `.idea/misc.xml` のみ変更あり。`build/rotation-20261107/baseline` にコピー、ハッシュ、binary diff、statusを保存。
- ShutUp: `main`, `1a18497cfbe62eac20ecf3ba8264cb8daf163fac`。前回のGoldy部分移行とPyrite作業の未コミット差分・未追跡ファイルあり。`.gradle/rotation-baseline-20261107` に全開始差分と未追跡ファイルをコピーし、ハッシュ・binary diff・statusを保存。11:50 JSTの移行着手直前にも全開始ファイルのハッシュ一致を確認。
- Windows / PowerShell / JDK21 / Gradle9.6.0 / AGP9.4.1 / compileSdk37。ShutUpはminSdk28、targetSdk36、JVM17、Compose plugin2.3.10。既存Pyrite `43557c8` とTrickuild・Expressiveテーマを維持。
- 正式署名鍵は取得・複製していません。ShutUpのCommit/Pushは実施していません。他3アプリの実装は変更していません。

## 公開依存と単一経路

採用済み公開SHA: `af5f415db976f62f991369224cb236d272f0103a`。
`com.github.Link2011-Act2.Goldy:elief:<SHA>` と同SHAの推移Goldを使用します。ローカルMaven、隣接project依存、included buildはありません。Goldyの新成果物・3独立Consumerの成功を確認してからShutUpへ採用しました。

Goldyの最終HEADは採用SHAと同じです。公開成功後に更新したREADME・配布記録・API26結果・検証スクリプトはGoldyのローカル差分に残しています。これらを別SHAで公開検証済みとする記載はしていません。

| 旧実装・責務 | 最終経路・ホストに残す理由 |
|---|---|
| GitHubReleaseChecker / ReleaseVersion / 候補・履歴選択 | Goldy.create → GitHubReleaseClient → UpdatePolicies.shutUp()。旧実装はmainから除去、比較用の凍結参照だけをtestへ保持 |
| AppUpdateRepository | mainから除去。Goldy.stateが候補・通知・確認結果の単一所有者 |
| AppUpdatePreferences | UpdatePreferences.ktがホスト設定と一度限りの移行のみ担当。カタログ・8時間間隔・複数無視タグは標準Gold永続ストア |
| AppUpdateViewModel | ライフサイクル、初期化ゲート、ホスト設定変更、日本語エラー表示の接続のみ。判定・通知の別経路なし |
| AppUpdateUi / UpdateOverviewContent / AboutScreen | EliefのBanner/Icon/UpdateCheckDialog/詳細を既存Pyrite遷移・テーマ・文字列へ接続 |
| 旧UpdateDownloadViewModelのHTTP・署名照合・APK検証 | 公開ApkDownloader、ApkSelectionPolicies.ShutUp、ApkVersionBindings.Published、ApkInstallerへ統合。ホストは操作Job・UI状態・要求台帳・Activity起動だけを所有 |
| 旧 `.updates` FileProvider / update_file_paths.xml | 削除。GoldyFileProvider `${applicationId}.goldy.fileprovider` の限定URIのみ |
| ReleaseNotesMarkdown / 直接Markwon依存 | 未使用の重複実装を除去。Eliefのrendererを使用 |
| ReleaseChannel | About等でも使うホスト表示関数を保持。更新選択やAPK本人確認には使用しない |
| OSS表示 | Goldy MIT本文とapksig Apache 2.0の帰属表示を追加。Markwon等の推移依存の表示も維持 |

旧Repository、Downloader、署名検証をproductionの別経路として残していません。凍結した旧DTO・比較関数・Repositoryはテスト専用ソースセットにあり、アプリAPKには含まれません。既存テストは削除・無効化していません。

## 設定と復元

旧 `github_release_updates` は変更・削除せず残します。自動確認・通知・IntDev専用の比較無視/間隔無視を新ホスト設定へコピーし、旧単一 dismissed_tag をGoldの複数ignoredTags集合へ和集合で移行します。Goldストアへの永続書込が成功した後だけmigration_v1をcommitし、クラッシュ・保存失敗後も再実行可能です。以後は移行済みマーカーにより旧タグを再導入しません。

旧キャッシュJSONには実Release/Asset IDやsizeがないため、値を推測して新カタログにしません。旧JSONと最終成功時刻/版は保存し、Release情報のみ一度再取得します。その成功時からGoldの8時間間隔を使用します。旧通知無視やユーザー設定は保持されます。無視した候補も手動で詳細を開けます。

一時バナー非表示はタグをホスト設定へ保存し、Goldキャッシュ復元後、画面公開前に適用します。旧実装でメモリにしか存在しなかった非表示状態を終了済みプロセスから推測しません。現在の実インストール版はPackageManagerで取得し、BuildConfigで比較元を偽装しません。

## 転送・インストール要求

実GitHubカタログのRelease/Asset ID、URL、size、digestを使用します。Goldが配信元/HTTPS、size/digest、署名、packageName、versionCode下限、tag/versionNameを確認し、prepare・launch・provider openでも再認証します。UIの転送識別用ハッシュはAPKの信頼根拠ではありません。

単一ViewModelのJobで同時操作を抑止します。ダウンロード取消と再試行はGoldへ委譲し、部分ファイルを清掃します。VerifiedApkのキャッシュ元はdurable lease作成後に削除し、共有先のfiles領域を削除しません。準備前にプロセスが終了したキャッシュは再利用せず、再ダウンロード・再検証が必要です。

要求ID、対象タグ/アセット識別、起動前claim、引き渡し後HandedOffを永続化します。引き渡しの前後で終了した場合に自動再起動しません。曖昧なclaimだけの場合はReadyと確認不能の案内を表示します。HandedOffはインストール成功ではありません。

候補がなくてもAboutから保持APK管理へ移動できます。再起動は明示確認、releaseはインストーラー/読取画面を閉じたことの確認後だけです。onResume、onDispose、onCleared、時間経過だけで共有URIを解放しません。保存・復元失敗でも共有APKを勝手に削除しません。

## 旧仕様との差分

- 旧署名履歴の共通証明書一つによる許可を廃止。正当な前方PoRとinstalled-dataは許可し、逆行・証明なし・複数署名部分一致を拒否。
- tagとAPK versionNameの厳密対応、完全なAPK暗号認証、256MiB上限、size/digestを適用。IntDev/Beta/RC/revisionも正確に一致する場合だけ受理。
- APKが複数ある場合、旧「名前にshutupを含む最初の一つ」から指定されたGoldの単一候補選択へ変更。曖昧な集合を推測しない。
- 固定cache/updates/update.apkの上書きを廃止し、Goldの要求ごとの永続URIと明示解放へ変更。
- 単一無視タグを複数タグへ正式移行。旧メタデータのみ初回再取得。

## 検証の読み方と再開

`run-full-android.ps1 -Serial emulator-... -Adb <adb.exe>` は物理端末を拒否し、権限拒否/許可・UI・キャッシュと要求の各プロセス復元stageを順に実行します。REQUEST_INSTALL_PACKAGESの変更自体がAndroidにプロセスを終了させるため、モード変更はinstrumentation外で行います。

転送のAndroidテストは通信入力とActivity起動の記録だけを差し替え、公開Goldの暗号検証・prepare/launch/providerを使います。プロセス復元fixtureも実APKを認証してからSDKのdurable leaseを作ります。正式鍵や検証バイパスはproductionへ追加していません。`generate-host-fixture.py` の秘密鍵はignored build配下の使い捨て鍵のみです。

Debug build、Unit Test、両Lint、Release R8とAndroidテストの集計はJSONへ記録します。`assembleRelease` はSTORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD未設定で既存validateReleaseSigningにより停止しました。Debug署名でRelease成功扱いにしていません。正式署名環境でRelease APKを作り、正式鍵の実機更新・OEMインストーラーを確認する作業が残ります。

初期テストではfixtureの復元状態・APK版情報・保存先制約を修正しました。API36のPlay Store背景更新による容量不足でキャッシュが消えた試行はAPKが拒否されました。成功扱いにせずログを残し、読取専用AVDの背景通信を止めて再検証しています。

## 要求ケースとの対応

| ケース | 根拠・範囲 |
|---|---|
| 1 更新なし | Android DeveloperUpdateSettingsTestで実インストール同版とGoldキャッシュを使用 |
| 2 Releaseあり | 新旧1840条件のJVM比較、Androidの実Gold engine・UI・キャッシュ復元 |
| 3 Beta/RC/IntDev | 4アプリPolicy/BindingのGold JVMテスト、新旧比較、Beta UI、実IntDev APKの転送。正式配布APK全チャンネルの端末更新は未実施 |
| 4/5 手動/自動 | Gold engineのsource回数・キャッシュ間隔・DisabledとManualの差、UI再確認 |
| 6/7 無視/手動詳細 | 旧タグ移行、複数タグ保持、通知なし・候補あり、Aboutから手動詳細 |
| 8/9 設定復元/旧移行 | 冪等・保存失敗・再起動、別PIDでの旧設定→Goldキャッシュ→ネットワークなし復元 |
| 10 ダウンロード成功 | 実ShutUp Debug APKを公開Gold downloader/inspector/installer/providerへ入力。通信とActivity callbackはテスト記録用 |
| 11 取消 | 遅延streamの途中取消・部分ファイルなし・新規再試行の成功 |
| 12 不完全 | Gold既存Unit TestのEOF/途中中断とsize検証。ShutUp Androidのsize不一致拒否 |
| 13 digest | Androidの実APKに誤SHA-256を指定し拒否。Goldの不正digest/転送テスト |
| 14 packageName | 暗号認証済みの別パッケージfixtureを拒否 |
| 15 別署名 | 同packageName・有効版名/コードを持つ使い捨て他鍵APKの署名不一致を確認し、転送でも拒否 |
| 16/17 正当な前方/不正・逆行 | Gold API28/33/36でapksigとPackageManagerの一致・実OS更新各14件。API26はv2実効同一性のみ14件。正式ShutUp鍵でのrotation更新は未実施 |
| 18 versionCode下限 | Goldの既存検証Unit Test。ShutUp正式鍵で低code APKを作成していない |
| 19 tag/versionName | 新Published Bindingの厳密例、Android実APKと異なるtagで拒否 |
| 20 権限 | 拒否時の設定Intent、OSが権限変更で終了した後の別プロセス復帰、自動起動なし・明示retry成功。ShutUp側のSettings/installer callbackは記録用 |
| 21 一度だけ引き渡し | 二連打でも1 callback、ViewModel復元でも0追加、曖昧claimからの明示再オープンのみ許可 |
| 22 Activity/プロセス | 実MainActivityで通知Dialog/詳細/履歴復元。別PIDでcache・lease復元。HandedOffとReady未知を区別しURI読取と明示解放まで確認 |
| 23 Pyrite遷移 | Home→設定→About→手動詳細→履歴→戻る、Activity再生成を実行。既存テーマファイルは開始時と同一 |
| 24 build等 | Debug、Unit190、両Lint、Release R8成功。正式Release APKは署名環境不足で未生成。最終数値はJSON |

## 結論と次回

**B: 実装の単一経路への移行と実施可能な検証は完了しましたが、正式署名Release APK・正式鍵の実機更新・OEMインストーラーまでの確認は未完了です。**

1. 現在のローカル差分とテストfixtureを保ったまま、秘密鍵をリポジトリへコピーせず既存の正式署名環境で `:app:assembleRelease` を実行してください。
2. 正式鍵で署名した適切なpackageName/版/codeのAPKを使い、実配信からの取得、OSの許可画面、インストーラーUI、完了後の実インストール版を確認してください。起動要求成功だけを完了としないでください。
3. OEM端末とAPI27等の未実行環境は追加検証対象です。GoldのAPI26/28/33/36実署名結果を他API/OEMの成功と読み替えないでください。
4. 任意の再検証はJDK21でDebug/Unit/Lint/R8、専用AVDでrun-full-android.ps1を使用してください。既存stageを途中で中断した場合はgoldy_transfer_process_test台帳とGold pendingRequestsを確認し、使用中のURIを早期削除しないでください。
