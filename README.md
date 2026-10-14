# Salt AirMouse

Turn an Android phone into a large gesture trackpad and rotation-controlled air mouse for a Windows laptop over trusted private Wi-Fi. Built by **Niwas Repalle**.

Native Android Java handles gestures and motion sensors; a Python receiver handles pairing and Windows mouse input. No Python packages are required. This is a personal prototype: physical Android sensor and UI acceptance testing remains pending.

## Update from version 1
1. Stop the old Windows receiver with Ctrl+C.
2. Download or clone this source and launch its `receiver/start-receiver.bat`.
3. Build or download the v2 APK and install it on your Android phone. It should update the old app when the signing key matches. If Android reports a signature mismatch, uninstall the old app then install v2; this resets saved settings.
4. Open **Connect**, enter the laptop address and new code, and connect again. The v2 app detects the old receiver and asks you to update it.

## Minimal interface
The home screen has Connect, a settings menu, two mode tabs and a trackpad that expands to fill the remaining height. Address entry, sensitivity, recenter and calibration are in dialogs. There are no separate left/right click, scroll or zoom buttons.

## Trackpad gestures
| Gesture on the large pad | Laptop action |
| --- | --- |
| One finger moves | Pointer movement |
| One quick tap | Left click |
| Two quick consecutive taps | Double click (two normal clicks at the Windows double-click interval) |
| Two fingers tap together | Right click |
| Two fingers move up/down | Vertical scrolling |
| Two fingers move left/right | Horizontal scrolling |
| Pinch fingers apart | Zoom in |
| Pinch fingers together | Zoom out |
| Hold one finger still for 0.45 seconds, then slide | Hold left button and drag |
| Tap, then quickly tap again and slide | Tap-and-drag |
| Lift or cancel a drag | Release left button |

These same tap / right-click / scroll / zoom gestures also work on the pad in air mode. Long-press dragging is for Trackpad mode only, because holding the pad in Air mode means move the pointer.

Zoom sends Ctrl + mouse wheel. Browsers, documents and other programs commonly use this for zoom; an application can interpret it differently or ignore it. Horizontal scrolling also depends on the laptop application. This app is not Windows Precision Touchpad hardware; three-/four-finger Windows workspace gestures, pressure/force click and per-app touchpad APIs are not implemented.

Natural scrolling is enabled by default; turn it off in Settings if you prefer the other direction. Pointer sensitivity is saved for both modes.

## Air mouse
1. Connect, select **Air mouse**, and check the footer: it should show **Rotation sensor / Receiving** (or Gyroscope / Receiving).
2. Touch and HOLD anywhere on the large pad. Gently rotate your phone left/right and tilt it up/down. Release the pad to reposition your hand.
3. Automatic grip maps upright and screen-up grips using the sensor's gravity direction. If direction feels wrong, select **Upright** or **Flat** in Settings, or reverse the horizontal/vertical axes there.
4. Settings also offers hands-free movement by turning off **Air: hold pad to move**. In this mode the pointer moves continuously while Air mode is visible; opening a dialog or switching modes pauses it. Holding the pad is recommended for control.
5. If the footer says **No readings**, check Android's Sensors Off developer quick tile if enabled, then reopen the app. The app tries a game rotation sensor, a rotation sensor, then a gyroscope. If all fail, Touchpad remains usable.
6. **Calibrate** resets the fused rotation reference. When the app has fallen back to raw gyro, keep the phone still for one second to estimate bias.

Air mode tracks ROTATION, not moving the entire phone sideways without changing its angle. It behaves like a relative air remote, not an absolute screen-pointing remote.

### What changed in air mode
The old implementation used raw X/Y gyro axes with one upright grip, a separate hold button and no sensor-registration check or reading status. Version 2 prefers fused relative quaternion readings, maps yaw against gravity for different grips, checks sensor registration, falls back if readings stop, lowers the movement dead-zone and makes the entire pad the hold area. This addresses concrete implementation limitations; physical operation on the reported Pixel 9a still needs verification. No claim is made that one specific cause was proven on the user's device.

## First-time setup
1. Windows needs Python 3.10+ from https://www.python.org/downloads/windows/ with Add Python to PATH enabled. No pip packages are needed.
2. Launch `receiver/start-receiver.bat`, or run `python receiver/receiver.py` from this folder. Keep it open.
3. Allow Python on PRIVATE networks when Windows Firewall asks. If pairing times out, add your python.exe under Windows Security -> Firewall & network protection -> Allow an app through firewall. Do not disable the firewall. Receiver uses TCP 8765.
4. Phone and laptop must be on the same trusted Wi-Fi. Use the receiver's Wi-Fi IPv4 (192.168.x.x or similar). If several appear, run `ipconfig` and select Wi-Fi, not VPN / Docker / VirtualBox.
5. Build an APK using the instructions below, or download the **SaltAirMouse-debug** artifact from a successful GitHub Actions run once this repository is published. Extract the artifact ZIP, copy `app-debug.apk` to the phone and open it. Allow installation from that file manager if prompted.
6. Tap Connect, enter the address, receiver's 6-digit code and port 8765. Code expires after five minutes; restart the receiver for a new code. Established connections continue past code expiry.
7. Switching away or locking the phone disconnects it. Connect again on returning. Ctrl+C in the receiver is the emergency stop.

## Build without Android Studio
Open PowerShell in the source folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

The script downloads portable JDK 17 and Android command-line tools to `%TEMP%\SaltAirMouse-build`, asks you to read/accept SDK licenses, installs SDK 35 / build-tools 35.0.0, compiles Java, converts it with D8, packages with AAPT2 and signs/verifies the test APK. It needs internet on the first run and a few GB of disk space. No Android Studio, emulator or Gradle is needed for this default route.

With existing tools:
```powershell
.\build-apk.ps1 -JdkPath 'C:\path\to\jdk17' -SdkPath 'C:\path\to\sdk' -SkipSdkSetup
```

The normal Gradle project is retained: `gradle -p android assembleDebug` with Gradle 8.9 / JDK 17 / SDK 35. That route previously failed here before compilation because Java could not open a local socket. The verified build uses the direct SDK route. The prepared `.github/workflows/apk.yml` runs Windows receiver tests, recorded Java core tests and an Android debug build. The revised workflow passed on 14 October 2026: Windows receiver tests, Java core tests and the Android build. Download the APK artifact from the [verified run](https://github.com/niwasrepalle/salt-air-mouse/actions/runs/38600956801). Source archives do not include an APK or signing key.

## Verification
```powershell
python -m unittest discover -s receiver -v
powershell -ExecutionPolicy Bypass -File .\tests\run-core-tests.ps1
python receiver/receiver.py --dry-run
```
Dry-run validates commands without affecting the desktop. Core tests use recorded gesture and motion traces, not physical phone sensors. See `VERIFICATION.md` for exact results and remaining device tests.

## Limits and safety
- The TCP code/commands are unencrypted. Use trusted private Wi-Fi; never forward port 8765 to the internet. Five failed pairing attempts per minute, a five-minute code and one active controller reduce accidental access, not hostile network attacks.
- Drag buttons release when the connection closes; a lost network can take up to eight seconds to time out. Ctrl+C stops the receiver and releases its held button. Zoom releases the Ctrl modifier it created even if sending the wheel fails, and preserves a Ctrl key already held locally.
- Wi-Fi delays, Windows pointer acceleration and sensor hardware affect feel. Under load stale movement frames can be dropped; a command already in transit can finish just after hold release.
- Fused sensor yaw can still drift. Grip auto-mapping near unusual orientations can feel different; explicit Upright/Flat modes and reversal switches are available.
- Android runtime/UI/physical sensors on the Pixel 9a have not been tested here. The user reported v1 touchpad working and v1 air movement failing while connected.
- Windows may reject SendInput into elevated windows, the UAC desktop, lock screen or some games. Normal receiver operation does not require administrator privileges.
- Guest Wi-Fi isolation, VPNs and firewall settings can block connection. Address entry is manual; no Bluetooth or QR discovery.
- This is a sideloaded personal-test APK with a debug signing key, not a Play Store release. Clearing the build cache can generate a different signing key.

## Source
- `MainActivity.java`: native dark interface, connection, sensors and pad events.
- `TrackpadEngine.java`: gesture recognition and drag lifecycle.
- `AirMotion.java`: quaternion / gyro processing and grip mapping.
- `receiver/receiver.py`: paired Windows receiver with mouse, wheel and zoom modifier input.
- `receiver/test_receiver.py`: receiver tests.
- `tests/CoreTests.java`: gesture and air-math tests.

Technical references: [Android SensorManager](https://developer.android.com/reference/android/hardware/SensorManager), [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [Windows MOUSEINPUT](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-mouseinput), [Windows SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput).

