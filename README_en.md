# ShutUp

This is a fork of [soralis0912/ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle).  
The main changes are the Material 3 Expressive UI, automatic enforcement removal, a Galaxy-specific mode, and some other stuff. The basic idea is still pretty much the same.

Devices sold in Japan and some other regions are forced to play the camera shutter sound even in silent mode.  
This app uses [Shizuku](https://shizuku.rikka.app/) to remove that enforcement without root.

Basically, this means you can mute the camera shutter sound on Japanese smartphones by putting them in silent mode.

## How it works

### Standard Android

When a device, its SIM's carrier config or `audio.camerasound.force` asks for an audible shutter, AudioService sets the audio policy's force-use for `FOR_SYSTEM` to `FORCE_SYSTEM_ENFORCED`. The shutter then plays on `STREAM_SYSTEM_ENFORCED`, which the ringer mode does not mute.

The app starts a Shizuku user service, running as `shell`, which holds the `MODIFY_AUDIO_ROUTING` permission that audioserver checks. The service calls `AudioSystem.setForceUse(FOR_SYSTEM, FORCE_NONE)` to lift the enforcement, and `FORCE_SYSTEM_ENFORCED` to put it back. What to put back comes from `MediaActionSound.mustPlayShutterSound()`, so a device that does not enforce it is left alone.

This method isn't device-specific; it uses AOSP's audio policy.

### Galaxy

Buuuut!!! Galaxy is the exception! The method above doesn't work there, so it needs its own approach.

On Samsung Galaxy devices, shutter sound enforcement is controlled by CSC (device settings for regions and carriers). When the System setting `csc_pref_camera_forced_shuttersound_key` is `1`, the shutter sound is enforced. Setting it to `0` removes that enforcement so the shutter sound can follow the ringer mode.

In this case, the app starts Shizuku's UserService as `shell` and uses the `WRITE_SECURE_SETTINGS` permission to modify the System setting through SettingsProvider. It sets the key to `0` to remove enforcement and restores the system default obtained through `dumpsys settings` when you choose to restore it.

<br>**To put it stupidly simply, there's this shitty setting that forces the shutter sound to play no matter what, and this app forcibly disables it so the shutter sound can be muted in silent mode.**

## Things to know

### Standard Android

- The change lives in audioserver only. A reboot, an audioserver restart, or Android reapplying audio policy can restore enforcement.

- Removing enforcement can also affect other sounds that use the same enforced audio stream.

### Galaxy

- On Galaxy, the CSC setting does not reset when you reboot. I've heard an OTA update might reset it, but I haven't confirmed that.

- The shutter sound may not be muted when using a user other than the main user. On some Galaxy devices, the camera app doesn't even check the CSC setting for users other than User 0 and simply forces the shutter sound anyway.

### Both methods

- With enforcement removed, the shutter follows the ringer mode: it is muted only in silent or vibrate mode.

- If the shutter sound still plays in silent mode even after removing the enforcement, you're out of luck. The camera app itself is playing the sound, so this app can't stop it.

- **Uninstalling the app does not automatically restore the setting.** I recommend restoring the device's default setting before uninstalling.  
  Touch and hold the app icon and select *Restore before uninstall* to open the restoration screen.

- This app technically supports Android 9 and later, but Android 13 or later is generally required without root.<br>If your device is rooted, I think there are better ways to do this than using this app.

- On ColorOS (OPPO, OnePlus, realme), turn on *Disable permission monitoring* in the developer options before starting Shizuku, or the shell calls are refused.

- Respect the privacy of the people around you and the rules where you live.

## Use

1. Install and start Shizuku.
2. Open this app and grant Shizuku permission.
3. Tap *Stop enforcing the shutter sound* in the app, or use the Quick Settings tile. The shutter sound will now be muted in silent mode.  
   A notification will be displayed while this change is active.
4. If you enable the automatic removal feature, the app will periodically check whether shutter sound enforcement has been restored.  
   It can also automatically remove the enforcement after a device reboot. It also checks when Shizuku restarts, so...

## License

For now, this app is released under the MIT License.  
However, the original project is licensed under NYSL 0.9982, and the other OSS libraries used by this app are subject to their respective licenses.


## Screenshots
<img width="200" alt="Screenshot_20261011-154744" src="https://github.com/user-attachments/assets/352cada1-7547-4a50-bc4d-37b1a5a0f2fd" />
<img width="200" alt="Screenshot_20261011-154751" src="https://github.com/user-attachments/assets/e4d1d5b3-f491-42a8-a737-a2889a1dd67a" />

## Fun fact

The name of this app is terrible, but it's actually a pun combining "Shutter" and "Shut up".
