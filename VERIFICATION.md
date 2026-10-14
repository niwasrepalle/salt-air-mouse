# Version 2 verification — 9 October 2026

## Reported device issue
User reports Google Pixel 9a: version 1 connects and touchpad works, but air mouse does not move. This supports narrowing investigation to Android air input rather than the basic network/receiver path. No live phone sensor trace was available; the exact on-device cause is not proven.

## Implemented changes
- Large dark trackpad fills the available home-screen height; connection and settings live in dialogs.
- Tap left click, consecutive taps double click, two-finger right click, vertical/horizontal scrolling, pinch zoom, long-press drag and tap-and-drag.
- No dedicated click/scroll/zoom buttons. Two mode tabs remain visible.
- Air mode prefers GAME_ROTATION_VECTOR then ROTATION_VECTOR then raw GYROSCOPE; registration result checked and no-reading timeout triggers fallback.
- Relative quaternion motion and gravity-aware grip mapping; automatic, upright and flat grip modes; axis reversal controls.
- Entire trackpad acts as air hold area. Optional hands-free setting. Sensor receiving status displayed.
- Receiver protocol 2 supports horizontal wheel, Ctrl-wheel zoom and drag down/up. The app rejects a v1 receiver with an explicit update message.
- Receiver releases its held drag button on disconnect; zoom modifier released in finally; physical pre-held Ctrl preserved.

## Historical checks recorded on 9 October 2026
- **23/23 Java core tests:** tap/double tap, move-vs-click, right tap, scroll-vs-pinch, horizontal scroll, zoom direction, drag ordering and cancellation, staggered releases, third-finger suppression, air hold/tap separation, gyro hold gating, upright quaternion yaw, flat grip axis, quaternion sign equivalence, gap/recenter suppression, axis reversal and gyro bias.
- **15/15 receiver tests:** original pairing/security/command cases plus drag idempotence, button release on disconnect, zoom key sequencing, modifier release on injected error, preservation of pre-held Ctrl, horizontal scrolling and invalid zoom payloads.
- Actual paired TCP -> SendInput -> Windows cursor smoke test: passed; 12 relative units yielded 14 screen pixels in that run; restored pointer afterward.
- 100 loopback pings: passed; mean 0.124 ms / max 0.220 ms. These are local results, not phone Wi-Fi latency.
- Supplied core-test PowerShell script: passed.
- Supplied APK PowerShell build script with existing JDK/SDK: passed. Java compilation, D8 API23 conversion, AAPT2 packaging, alignment and signing completed.
- APK verified with v1/v2/v3 signature schemes. Version 2.0 / code 2, API23 minimum, API35 target and INTERNET permission inspected.

## Still not tested
- No APK installation, launch, UI screenshot/layout inspection or physical gesture/sensor test was performed on Pixel 9a or any Android device here.
- Synthetic gyro/quaternion trace tests establish math and gating behavior, not physical sensor registration success or orientation feel on the phone.
- Actual Windows click, drag, horizontal scroll and Ctrl-wheel behavior in real applications remain device/application acceptance tests; dispatch and release paths passed recorded-input tests.
- Android lifecycle, real LAN/firewall latency, multi-touch ergonomics and accessibility runtime behavior require phone testing.
- No claim of complete Windows Precision Touchpad functionality; common requested gestures are implemented. Zoom support depends on the foreground application.
- Full fresh-cache PowerShell download path and GitHub Actions workflow were not run in this update. Previously downloaded SDK/JDK used; standard Gradle route previously failed before compilation with Java loopback socket error.

## Pixel 9a acceptance checklist
1. Stop old receiver, run the v2 receiver, install v2 APK, connect with its fresh code.
2. Trackpad: one tap, double tap, two-finger tap, vertical/horizontal scroll, pinch both directions in a browser.
3. Drag a harmless object or select disposable Notepad text using long-press and tap-and-drag. Release, switch modes and disconnect; no button should remain held.
4. Select Air mouse. Footer must say Rotation sensor / Receiving (or Gyroscope / Receiving).
5. Hold anywhere on pad, turn phone gently left/right and tilt up/down. Release and reposition; pointer should stop. Air input responds to rotation, not translating the phone sideways.
6. Try automatic grip upright and screen-up. If needed select explicit grip/reversal in Settings.
7. If no movement, record the footer message and whether the update counter changes while rotating. This distinguishes missing sensor events from projection/hold problems.
8. Open settings, switch apps or lock screen; air movement should pause/disconnect. Reconnect after returning.

APK: SaltAirMouse-debug.apk
Size: 29075 bytes
SHA-256: `cddd5bdec9a5cbfbd96b64c2b64ddb7d7999e25400608a64892095dfb35a4b85`


## Upload preparation — 14 October 2026
- Receiver suite rerun on Windows: **15/15 passed**. The sandbox initially blocked local sockets; the authorized unrestricted rerun passed. Tests use a recording mouse adapter.
- Revised CI includes Windows receiver tests, recorded Java core tests, Android SDK setup v4 and a debug APK artifact. The revised CI is prepared, not cloud-verified.
- Java core tests and APK build were not rerun during this preparation: a local JDK was unavailable. Historical results above are not new validation.
- No Android device acceptance tests were added.
- The historical APK hash above identifies the earlier build; that APK is not bundled in this source archive.
- Upload authorized on 14 October 2026; cloud results are recorded below after verification.

