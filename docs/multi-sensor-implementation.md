# Multi-Sensor Implementation

## Implemented inputs and outputs

| Capability | Android API | Runtime behavior |
| --- | --- | --- |
| Ambient light | `Sensor.TYPE_LIGHT` | Reads lux while an exposure session is monitored. |
| Physical proximity | `Sensor.TYPE_PROXIMITY` | Detects a covered phone as pocket evidence. |
| Accelerometer | `Sensor.TYPE_ACCELEROMETER` | Classifies face-up, face-down, upright and tilted posture, plus short-window movement. |
| Step counter | `Sensor.TYPE_STEP_COUNTER` | Rebases the reboot-scoped counter into session steps and average steps per minute. |
| Location | `FusedLocationProviderClient` | Compares accurate, fresh fixes with saved indoor locations. |
| Microphone | `AudioRecord` | Calculates smoothed RMS/dBFS and discards PCM immediately. No audio is stored or uploaded. |
| Camera luminance | CameraX `ImageAnalysis` | Samples the Y plane once per second. No photo or video is saved. |
| Haptic output | `Vibrator` | Signals automatic indoor pause and exposure-limit alerts. |
| Audio output | `ToneGenerator` | Signals exposure-limit and developer-preview alerts using the notification stream. |

All optional hardware is declared with `android:required="false"`. Missing hardware or denied optional permission produces an unavailable value rather than stopping the application.

## Permissions and lifecycle

Location permission is requested by the existing current-location flow. The foreground exposure service collects light, proximity, accelerometer, steps and location only while an exposure session needs monitoring.

Settings contains an explicit **Enhanced sensing** switch. Turning it on requests `ACTIVITY_RECOGNITION`, `RECORD_AUDIO` and `CAMERA` where required. Microphone and CameraX sampling run only while the application lifecycle is at least `STARTED`; they stop when the application leaves the foreground or the switch is disabled. Raw audio and camera frames never enter application state.

## Environment fusion

Automatic indoor or pocket detection still requires ambient light below 1,000 lux for 10 continuous seconds. Supporting evidence is scored independently:

| Evidence | Weight |
| --- | ---: |
| Near a saved indoor location | 0.80 |
| Physical proximity reports covered | 0.80 |
| Camera luminance is dark | 0.20 |
| Relative sound is quiet | 0.20 |
| Phone is face-down | 0.15 |
| Accelerometer is stationary | 0.05 |

An indoor-support score of at least 0.35 is required. Location or physical proximity is therefore sufficient supporting evidence, but microphone, camera and motion signals must agree. A bright camera frame, active sound or movement reduces the weak-signal score. These signals provide context only: microphone volume and camera luminance are not direct measurements of UV.

Leaving the detected state also uses a 10-second debounce. It occurs when lux rises above 2,000 or supporting evidence disappears. A manually paused countdown is never automatically resumed.

## Alerts

- Confirmed automatic indoor pause: one short vibration.
- First transition to completed exposure: a vibration pattern and notification-stream tone.
- Developer mode: **Test reapply alert** and **Test band warning** invoke real haptic/audio output.
- Repeated state collection and recomposition cannot replay the completion alert because the ViewModel emits it only on the transition into `COMPLETE`.

## Real-device verification

1. Install the debug APK on a physical Android phone.
2. Grant precise location, then open Settings and enable **Enhanced sensing**.
3. Grant physical-activity, microphone and camera permissions.
4. Enable Developer mode to inspect live lux, proximity, posture, movement, steps, dBFS, acoustic context, camera luminance and fusion confidence.
5. Start an exposure session. Verify step and motion updates while walking and rotating the phone.
6. In a quiet, dark environment, verify camera and microphone values update while the app is visible.
7. Cover the light and proximity sensors for 10 seconds and verify the countdown pauses with one vibration.
8. Use the developer alert buttons to verify vibration and sound, respecting the phone's current notification volume.

Camera, microphone, proximity and step-counter behavior varies by device, so the JVM test suite validates classifiers and fusion while final hardware acceptance must use the team's reference phone.
