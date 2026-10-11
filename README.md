# ShutUp

Forked from [soralis0912/ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle),
with modifications by Azul1nk including the Jetpack Compose UI, settings, and app information screens.

Phones sold in Japan and a few other regions play the camera shutter sound
even in silent mode. This app switches that enforcement off and back on
through [Shizuku](https://shizuku.rikka.app/), with no root.

日本向けなどの端末は、マナーモードでもシャッター音が鳴るよう強制されています。
このアプリは Shizuku を使い、root なしでその強制を解除したり戻したりします。

## How it works

When a device, its SIM's carrier config or `audio.camerasound.force` asks
for an audible shutter, AudioService sets the audio policy's force-use for
`FOR_SYSTEM` to `FORCE_SYSTEM_ENFORCED`. The shutter then plays on
`STREAM_SYSTEM_ENFORCED`, which the ringer mode does not mute.

The app starts a Shizuku user service, running as `shell`, which holds the
`MODIFY_AUDIO_ROUTING` permission that audioserver checks. The service calls
`AudioSystem.setForceUse(FOR_SYSTEM, FORCE_NONE)` to lift the enforcement,
and `FORCE_SYSTEM_ENFORCED` to put it back. What to put back comes from
`MediaActionSound.mustPlayShutterSound()`, so a device that does not enforce
it is left alone.

Nothing device specific is involved; this is the AOSP audio policy.

## Things to know

- The change lives in audioserver only. A reboot, an audioserver restart,
  or Android reapplying audio policy can restore enforcement. **Uninstalling the app
  does not undo it**, so restore first.
  Touch and hold the app icon and choose *Restore before uninstall* to open
  the reminder and restoration controls.
- With the enforcement off, the shutter follows the ringer mode: it is quiet
  in silent or vibrate mode only. Other sounds on the enforced stream are
  affected the same way.
- On ColorOS (OPPO, OnePlus, realme), turn on *Disable permission monitoring*
  in the developer options before starting Shizuku, or the shell calls are
  refused.
- Respect the privacy of the people around you and the rules where you live.

## Use

1. Install and start Shizuku (wireless debugging or adb; root also works).
2. Open the app, grant the Shizuku permission.
3. *Stop enforcing the shutter sound*, or use the quick settings tile.
   A notification stays while the setting differs from the device default
   and restores it from its button.

## Optional automatic maintenance

Enable **Maintain released state** in Settings to automatically release shutter
sound enforcement again when it returns. It defaults to off. The enabled flag
and the armed (maintenance target) flag are saved separately and survive app
process death and device reboot.

After the first successful manual release, a one-time prompt offers to enable
this setting. **Maintain** turns the Settings toggle on; **Not now** leaves it
unchanged and dismisses the offer permanently. You can enable it later in Settings.

- Enabling it arms maintenance, checks immediately, and schedules unique
  WorkManager periodic work approximately once an hour, without a network
  requirement. Doze, battery saving, force-stop, and manufacturer restrictions
  can delay or prevent execution; exact hourly timing is not guaranteed.
- A Shizuku connection (or permission grant) also triggers a check while the
  application process is alive. Shizuku events do not launch a stopped app.
  WorkManager can start the app process for periodic work and reschedules it
  after reboot. No foreground service or continuous polling is used.
- Recovery requires both saved flags to be true and usable, authorized Shizuku.
  If Shizuku is stopped or not authorized, recovery is deferred. Wireless
  debugging Shizuku may need to be started manually again after reboot.
- **Restore device default** leaves the toggle **ON**, but durably pauses
  automatic release. This applies to the home screen, QS tile, notification
  restore action, and before-uninstall reminder. Periodic checks and Shizuku
  reconnections do not undo that pause, including after reboot.
- Manually releasing enforcement resumes maintenance if enabled. With the
  toggle off, it only performs the manual operation. Switching off and back on
  also re-arms maintenance.
- Switching **OFF** stops automatic recovery and cancels periodic work;
  it does **not** change the current audio policy or restore the device default.
- Only a known `FOR_SYSTEM=11` is automatically changed to `0`. A current `0`
  needs no write; unknown values are left alone. Device defaults still come
  from `MediaActionSound.mustPlayShutterSound()`, including defaults of `0`.

「解除状態を維持」は任意の機能で、初期値はOFFです。Shizuku接続時と約1時間ごとに
確認しますが、省電力制御などにより間隔は遅延します。Shizukuが利用できなければ
再解除できません。「既定値に戻す」を操作すると、トグルはONのまま自動解除だけを
一時停止し、アプリ終了・再起動後も保持します。次の手動解除、またはOFF→ONで
再開します。OFFにしても現在の音声ポリシーは変更しません。
初回の手動解除成功後に一度だけ案内し、「維持する」で設定がONになります。
「今はしない」は設定を変更せず、再案内しません。後から設定でONにできます。

## Updates

The app checks this repository's GitHub Releases when opened or resumed,
at eight-hour intervals after a successful check. It does not schedule
checks while closed. Saved update information remains available offline.
Close the banner to hide it for the current session, or choose *Ignore this
version* to suppress its banner and icon until a newer version is available.
Manual checks in app information always contact GitHub, including for ignored versions.

Stable builds receive full releases; Beta builds receive Beta and full releases;
IntDev builds can also receive development releases. The update screen shows
release notes and intermediate versions, and can download an APK and open
Android's installer after installation permission is granted. The APK must
belong to this package and have a compatible signing certificate.

IntDev builds expose two independent, saved developer options in Settings:
ignore version comparison (always offer the most recently published release,
even if its version is the same, older, or not a version tag), and ignore the
eight-hour automatic-check interval. Both default to off. Other channels hide
these controls and always disable both behaviors, even if enabled settings
remain from an earlier IntDev installation. APK package, signing, and Android
version-code checks still apply during installation.

## Build

```sh
./gradlew assembleRelease
```

Requires Android 13 (API 33) or later.

The keystore comes from `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` in the environment; a local build without them is signed with
the debug key.

Version settings are maintained in `app/build.gradle.kts`:

- `appVersionName` is the user-facing version, optionally overridden by the
  `VERSION_NAME` environment variable.
- `appVersionCode` is Android's internal version code. Increase it for each
  distributed APK across all release channels; the current value is `2`.

Build and publish APKs manually. Upload the signed APK to a GitHub Release
whose tag matches its version name (an optional leading `v` is supported).
Update detection compares version names, so also advance the version name
when distributing an update.

Use the same signing key for subsequent updates. An APK signed with a different
key cannot update an existing installation.

## License

ShutUp is published under the [MIT License](LICENSE), copyright (c) 2026 Azul1nk.
This notice covers our original contributions and modifications; it does not
claim ownership of the original upstream code. Third-party code and assets
retain their respective licenses.

- The original [ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle)
  by soralis0912 was released under NYSL Version 0.9982. Its original license
  text is preserved in [licenses/ShutterSoundToggle-NYSL.txt](licenses/ShutterSoundToggle-NYSL.txt).
- Dependencies and assets including AndroidX, Shizuku API, HiddenApiBypass,
  and Material Design icons / Material Symbols are under Apache License 2.0.
  Their licenses are also available in the app's open-source license screen.

ShutUpはMIT Licenseで公開しています。著作権表記は、ShutUp独自の追加・改変部分を対象とするもので、
フォーク元のコードの権利を取得したことを示すものではありません。
元のShutterSoundToggleはNYSL 0.9982で公開されており、上記の別ファイルに原文を保存しています。
使用する第三者のライブラリ・素材には、それぞれのライセンスが適用されます。
