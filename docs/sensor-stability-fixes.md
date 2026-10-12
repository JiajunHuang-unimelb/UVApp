# Fix notes: sensor stability and indoor auto-pause

Date: 11 October 2026

These fixes stop the burn countdown and the indoor auto-pause from reacting to short, noisy sensor readings. Each one was reproduced and then verified on a Samsung SM-S926B (Android 16) connected over adb, using the Dev card and recordings taken every 2 to 3 seconds.

## 1. Tilting the phone no longer makes the countdown jump

**Problem:** Tilting or picking up the phone made the remaining time jump back and forth, for example between about 12 and 38 minutes.

**Cause:** `ExposureContextDetector` returns a context for every sensor sample. In low light, a still face-up phone is SHADE (dose rate factor 0.3), but face down, moving or outdoor-like sound makes the same reading UNKNOWN (factor 1.0). Tilting flips these inputs every few seconds, so the countdown changed by about 3.3 times on each flip.

**Fix:** New `ExposureContextStabilizer`, used by `MainViewModel.detectExposureContext()`. A new context is only applied once it has held steadily:

- 3 seconds to move to a higher dose rate (under-counting UV is the riskier mistake)
- 10 seconds to move to a lower dose rate
- INDOOR is applied immediately, because the ViewModel already debounces the indoor transition
- Developer overrides on the Dev card still take effect immediately
- The stabilizer resets when a new session starts

**Verified:** While the phone was flipped face up and face down about 6 times over 20 seconds, the countdown changed once (38:xx to 11:16, 6 to 8 seconds after tilting began) and then counted down smoothly. It returned once, about 12 to 14 seconds after the phone was laid flat. Dose kept accumulating through both changes.

## 2. "Saved place" no longer switches between within and outside the radius

**Problem:** At Home, with location clearly inside the 100 m radius, the Dev card kept switching between "within 100 m" and "outside radius", and the session never auto-paused.

**Cause:** Two writers shared one flag. The monitoring service wrote the correct GPS-based result every 5 seconds. `IndoorLocationsViewModel.proximity()` ran every second and, without a precise fix under 30 seconds old, reported `null`, which `UVAppRoot` converted to `false`. The Home screen location was a searched address (marked approximate), so it never qualified, and a "use current location" fix would also expire after 30 seconds. The 10-second indoor confirmation kept restarting.

**Fix:** `UVAppRoot` now forwards the view model's result only when it has one (`near?.let(...)`). Without a usable fix it writes nothing and the service's result stays. Indoor suggestions are unaffected because they never read this flag.

**Verified:** 30 samples over 74 seconds were all "within 100 m" (before: 4 of about 60). The session auto-paused about 11 seconds after Start.

## 3. Enhanced sensing stays on after the app restarts

**Problem:** Re-running the app from Android Studio turned Enhanced sensing off even though the permissions were still granted.

**Cause:** The setting only lived in `SettingsViewModel` memory, so every new process started with the default (off).

**Fix:** The setting is saved in DataStore like SPF (`enhanced_sensing_enabled` in `UserPreferences`, `UserPreferencesRepository` and `DataStoreUserPreferencesRepository`) and restored by `SettingsViewModel`. At startup, if it was saved as on but microphone, camera and activity recognition permissions have all been revoked in system settings, `UVAppRoot` switches it back off so the toggle does not claim a state it cannot deliver. Other settings (developer mode, theme, notifications) are unchanged and still reset on restart.

**Verified:** Turned it on, force-stopped and reopened the app: it stayed on and the value was present in DataStore.

## 4. A knock on the table is no longer counted as movement

**Problem:** With the phone flat on a desk, each light tap on the screen switched Motion to "moving" for about 4 seconds.

**Cause:** `MotionClassifier` marked the phone as moving when the standard deviation of the last 20 accelerometer magnitudes reached 0.45 m/s². At the service's sampling period (160 ms) the window covers about 3 seconds, and one sample about 2 m/s² away from the rest is enough. The spike stayed in the window until it was pushed out. Besides the Dev card, this turned shade into UNKNOWN for the countdown and restarted the indoor confirmation.

**Fix:** A sample now counts as outlying when it is at least 0.6 m/s² from the median of the window. Moving starts when at least 4 of the last 8 samples (about 1.3 seconds) are outlying and stops when fewer than 2 are, so a single spike cannot trigger it and the state settles quickly after shaking stops. Posture detection is unchanged.

**Verified:** Flat on the desk, normal taps stayed "stationary"; only hard taps right next to the camera flashed "moving" briefly. Shaking and walking with the phone showed "moving".

## 5. One inaccurate location fix no longer ends the indoor pause

**Problem:** Sitting still in the same room, the countdown sometimes started running again on its own.

**Cause:** `ExposureMonitoringService.publishIndoorProximity()` only looked at the newest fix. Indoors the fused provider sometimes falls back to Wi-Fi or cell positioning. On the device, a 206.8 m fix replaced a 17 m fix taken seconds earlier, "Saved place" became "outside radius" while the phone lay untouched, and the next precise fix only arrived about 30 seconds later because location is requested every 60 seconds at a saved place. The indoor exit rule needs 10 seconds, so tracking resumed for 37 seconds. Light was not the cause: it only exceeded 2,000 lux in one sample.

**Fix:**

- A fix worse than 50 m never replaces the last precise fix (`LocationFixValidator.shouldReplace`), for both continuous updates and one-off requests.
- New `IndoorProximityRule` decides from the last precise fix. While the step counter reports STATIONARY, that fix is held without the 75-second age limit, because someone who has not walked has not left; only a new precise fix can change the result. The step counter is used instead of the accelerometer movement flag, so taps cannot affect it.
- When the user walks, or step data is unavailable (no activity recognition permission), the existing 75-second limit applies again, measured from when the last precise fix was taken.

**Verified:** Left still at Home for 6 minutes: all 167 readings were "within 100 m", and the session stayed paused from 10 seconds after Start (11.0 seconds of tracked time in total). The 17 fixes received were between 9.2 and 49.5 m, so no fix worse than 50 m arrived during this run; that path is covered by unit tests.

## Tests

- 217 unit tests pass (`testDebugUnitTest`), including new tests for the stabilizer, settings persistence, single knocks versus sustained shaking, and the location rules.
- `compileDebugAndroidTestKotlin` passes.
- `ktlintCheck` reports success, but it stayed up to date after source changes and appears to check only Gradle Kotlin scripts, not the app sources. This should be looked at separately.

## Files changed

- `domain/exposure/ExposureContextStabilizer.kt` (new), `viewmodel/MainViewModel.kt`: countdown stabilizer
- `ui/UVAppRoot.kt`: saved-place result is no longer overwritten; Enhanced sensing permission check
- `domain/model/UserPreferences.kt`, `domain/repository/UserPreferencesRepository.kt`, `data/preferences/DataStoreUserPreferencesRepository.kt`, `viewmodel/SettingsViewModel.kt`: Enhanced sensing is saved
- `domain/environment/MotionClassifier.kt`: movement needs sustained evidence
- `domain/environment/LocationFixValidator.kt`, `domain/environment/IndoorProximityRule.kt` (new), `platform/environment/ExposureMonitoringService.kt`: inaccurate fixes are ignored and the result is held while stationary
- Tests: `ExposureContextStabilizerTest`, `MainViewModelSensorTest`, `SettingsViewModelTest`, `FakeUserPreferencesRepository`, `MotionClassifierTest`, `LocationFixValidatorTest`, `IndoorProximityRuleTest`, and `IndoorLocationsUiTest` (androidTest fake)

All paths above are under `app/src/main/java/com/example/uvapp/` or the matching `test`/`androidTest` folders.
