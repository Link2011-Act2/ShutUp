# ShutUp

This is a fork of [soralis0912/ShutterSoundToggle](https://github.com/soralis0912/ShutterSoundToggle).  
The main changes are the Material 3 Expressive UI and the automatic removal feature. Otherwise, it does pretty much the same thing.

Devices sold in Japan and some other regions are forced to play the camera shutter sound even in silent mode.  
This app uses [Shizuku](https://shizuku.rikka.app/) to remove that enforcement without root.

Basically, this means you can mute the camera shutter sound on Japanese smartphones by putting them in silent mode.

## How it works

When a device, its SIM's carrier config or `audio.camerasound.force` asks for an audible shutter, AudioService sets the audio policy's force-use for `FOR_SYSTEM` to `FORCE_SYSTEM_ENFORCED`. The shutter then plays on `STREAM_SYSTEM_ENFORCED`, which the ringer mode does not mute.

The app starts a Shizuku user service, running as `shell`, which holds the `MODIFY_AUDIO_ROUTING` permission that audioserver checks. The service calls `AudioSystem.setForceUse(FOR_SYSTEM, FORCE_NONE)` to lift the enforcement, and `FORCE_SYSTEM_ENFORCED` to put it back. What to put back comes from `MediaActionSound.mustPlayShutterSound()`, so a device that does not enforce it is left alone.

Nothing device specific is involved; this is the AOSP audio policy.

**To put it stupidly simply, there's this shitty setting that forces the shutter sound to play no matter what, and this app forcibly disables it so the shutter sound can be muted in silent mode.**

## Things to know

- The change lives in audioserver only. A reboot, an audioserver restart, or Android reapplying audio policy can restore enforcement. **Uninstalling the app does not undo it**, so restore first.  
  Touch and hold the app icon and choose *Restore before uninstall* to open the reminder and restoration controls.

- With the enforcement off, the shutter follows the ringer mode: it is quiet in silent or vibrate mode only. Other sounds on the enforced stream are affected the same way.

- If the shutter sound still plays in silent mode even after removing the enforcement, you're out of luck. The camera app itself is playing the sound, so this app can't stop it.

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
