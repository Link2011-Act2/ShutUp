[日本語](README.md) | [English](README_en.md)

# ShutUp

[soralis0912/ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle)のフォークです
<br>変更点はMaterial 3 ExpressiveのUI、自動解除機能、Galaxy用モードとかです　根本の仕組みはほぼ同じです

日本向けなどの端末は、マナーモードでもシャッター音が鳴るよう強制されています。
このアプリは [Shizuku](https://shizuku.rikka.app/) を使い、root なしでその強制を解除できます。

要は、日本版スマホであってもマナーモードならシャッター音が消せるということです

## 仕組み

### 通常Android
端末本体、SIMのキャリア設定、または audio.camerasound.force によってシャッター音を鳴らすことが要求されると、AudioServiceは音声ポリシーの FOR_SYSTEM に対するforce-useを FORCE_SYSTEM_ENFORCED に設定します。これにより、シャッター音はマナーモードでもミュートされない STREAM_SYSTEM_ENFORCED で再生されます。

このアプリはShizukuのUserServiceを shell として起動し、audioserverがチェックする MODIFY_AUDIO_ROUTING 権限を利用します。このサービスは AudioSystem.setForceUse(FOR_SYSTEM, FORCE_NONE) を呼び出して強制を解除し、FORCE_SYSTEM_ENFORCED に設定することで元に戻します。復元する値は MediaActionSound.mustPlayShutterSound() から取得するため、もともとシャッター音が強制されていない端末には影響しません。

この場合、特定の端末に依存する処理はありません。AOSPの音声ポリシーを利用しています。

### Galaxy
ただーーーし！！！　Galaxyだけは先述の方法が効かず、独自の処理が必要です

Samsung Galaxyでは、CSC（国や地域、キャリアごとの端末設定）によってシャッター音の強制が制御されています。System設定の`csc_pref_camera_forced_shuttersound_key`が`1`の場合は強制が有効になり、`0`にすると強制が解除され、シャッター音がマナーモードに従うようになります。
この場合はShizukuのUserServiceをshellとして起動し、`WRITE_SECURE_SETTINGS`権限を利用してSettingsProviderのSystem設定を書き換えます。対象キーを`0`に変更することで強制を解除し、復元時は`dumpsys settings`から取得したシステム既定値に戻します。

<br>**死ぬほど簡単に言うと、どんな状態であってもシャッター音を鳴らすクソ設定があり、それを無理やり解除することでマナーモードならシャッター音が消えるということです**

## 知っておくべきこと

### 通常Androidの場合
- この変更はaudioserver上にのみ保持されます。端末の再起動、audioserverの再起動、またはAndroidによる音声ポリシーの再適用によって、シャッター音の強制が元に戻る場合があります。

- 強制を解除すると、同じ強制音声ストリームを使用する他の音にも影響します。

### Galaxyの場合
- Galaxyの場合は、再起動しても設定は元に戻りません。OTAアップデートすると戻るらしいですがわかりません

- メインユーザー以外だとシャッター音が消えない場合があります。これは、一部機種でUser 0以外だとそもそもカメラアプリが強制設定を参照せず問答無用でシャッター音を鳴らすからです。

### 両者共通
- 強制を解除すると、シャッター音はマナーモードの設定に従うようになります。サイレントまたはバイブレーションモードのときだけ消音されます。

- マナーモード中であってもシャッター音が鳴るなら、たぶん無理です。カメラアプリ自身が鳴らしているため、このアプリでは止められません

- アプリをアンインストールしても変更はもとに戻りません。そのため、アンインストールする前に既定値に戻す操作をすることをおすすめします。<br>アプリアイコンを長押しして「アンインストール前に復元」を選ぶと復元できますよ

- このアプリは一応Android 9から対応していますが、root無しだと原則Android 13以上が必要です。<br>root化しているなら、このアプリよりもっといい方法があると思います

- ColorOS（OPPO、OnePlus、realme）では、Shizukuを起動する前に開発者向けオプションから「権限の監視を無効化」をオンにしてください。そうしないと、shellからの呼び出しが拒否されます。

- 周囲の人のプライバシーと、お住まいの地域のルールを尊重してください。

## 使い方

1. Shizukuをインストールして開始しておきます
2. このアプリを開いて、Shizukuの権限を許可しておきます
3. クイック設定タイルか、アプリ内から「シャッター音の強制を解除」を押してください。これでマナーモード中はシャッター音が消えます<br>
   この変更が入っている間は通知が表示されます
4. 自動解除機能をオンにすれば、一定時間ごとにシャッター音の状態が戻っていないかを監視します<br>スマホの再起動後にも自動で解除できます。Shizukuが再始動したときにも確認するので

## ライセンス

とりあえずこのアプリはMIT Licenseで公開しています<br>
ただ、フォーク元はNYSL 0.9982ですし、他に使っているOSSライブラリはそれのライセンスに依存します

## スクリーンショット
<img width="200" alt="Screenshot_20261011-154731" src="https://github.com/user-attachments/assets/2fb41ef5-0e89-45b6-a02a-2ed8972a1b4f" />
<img width="200" alt="Screenshot_20261011-154737" src="https://github.com/user-attachments/assets/d1329c36-5c94-4d8a-80d6-846226c6db94" />


## 余談
このアプリの名前はひどいですが、これはShutterとShut upを掛けたダジャレです
