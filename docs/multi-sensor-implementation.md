# Multi-Sensor Implementation

## Implemented inputs and outputs

| Capability | Android API | Runtime behavior |
| --- | --- | --- |
| Ambient light | `Sensor.TYPE_LIGHT` | Reads lux while an exposure session is monitored. |
| Physical proximity | `Sensor.TYPE_PROXIMITY` | Diagnostic indication that the phone is covered; it does not classify an indoor location. |
| Accelerometer | `Sensor.TYPE_ACCELEROMETER` | Classifies posture and movement; stationary state gates indoor-location suggestions but not auto-pause. |
| Step counter | `Sensor.TYPE_STEP_COUNTER` | Rebases the reboot-scoped counter into session steps and average steps per minute. |
| Location | `FusedLocationProviderClient` | Compares accurate, fresh fixes with saved indoor locations. |
| Microphone | `AudioRecord` | Calculates smoothed RMS/dBFS for suggestion eligibility and discards PCM immediately. No audio is stored or uploaded. |
| Haptic output | `Vibrator` | Signals automatic indoor pause and exposure-limit alerts. |
| Audio output | `ToneGenerator` | Signals exposure-limit and developer-preview alerts using the notification stream. |

All optional hardware is declared with `android:required="false"`. Missing hardware or denied optional permission produces an unavailable value rather than stopping the application.

## Permissions and lifecycle

Location permission is requested by the existing current-location flow. The foreground exposure service collects light, proximity, accelerometer, steps and location only while an exposure session needs monitoring.

Settings contains an explicit **Enhanced sensing** switch. Turning it on requests `ACTIVITY_RECOGNITION` and `RECORD_AUDIO` where required. Microphone sampling runs only while the application lifecycle is at least `STARTED`; it stops when the application leaves the foreground or the switch is disabled. Raw audio never enters application state.

## Decision rules

Automatic indoor detection requires ambient light below 1,000 lux and a fresh, precise GPS fix within 100 m of a user-saved indoor location for 10 continuous seconds. There is no weighted evidence score.

Physical proximity, posture, movement, step count and microphone context do not affect automatic pause or resume. Leaving the detected state also uses a 10-second debounce and occurs when lux rises above 2,000 or the fresh location fix leaves the saved radius. A manually paused countdown is never automatically resumed.

An indoor-location suggestion is separate from detection. It requires low light, quiet microphone context and stationary accelerometer state at the moment the user manually pauses, plus a usable GPS fix outside all saved radii. The user must explicitly confirm before anything is saved.

## Alerts

- Confirmed automatic indoor pause: one short vibration.
- First transition to completed exposure: a vibration pattern and notification-stream tone.
- Developer mode: **Test reapply alert** and **Test band warning** invoke real haptic/audio output.
- Repeated state collection and recomposition cannot replay the completion alert because the ViewModel emits it only on the transition into `COMPLETE`.

## Real-device verification

1. Install the debug APK on a physical Android phone.
2. Grant precise location, then open Settings and enable **Enhanced sensing**.
3. Grant physical-activity and microphone permissions.
4. Enable Developer mode to inspect live lux, saved-radius proximity, physical proximity, posture, movement, steps, dBFS and acoustic context.
5. Start an exposure session. Verify step and motion updates while walking and rotating the phone.
6. In a quiet environment, verify microphone values update while the app is visible.
7. Enter a saved 100 m radius, hold the light below 1,000 lux for 10 seconds and verify the countdown pauses with one vibration.
8. Use the developer alert buttons to verify vibration and sound, respecting the phone's current notification volume.

Microphone, proximity, accelerometer and step-counter behavior varies by device, so the JVM test suite validates classifiers and decision rules while final hardware acceptance must use the team's reference phone.
