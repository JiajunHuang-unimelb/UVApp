# Multi-Sensor Implementation

## Implemented inputs and outputs

| Capability | Android API | Runtime behavior |
| --- | --- | --- |
| Ambient light | `Sensor.TYPE_LIGHT` | Reads lux through a five-sample rolling median while an exposure session is monitored. Invalid readings are ignored. |
| Physical proximity | `Sensor.TYPE_PROXIMITY` | Marks the phone as covered. Covered low-light readings are excluded from shade-dose decisions, but do not classify an indoor location. |
| Accelerometer | `Sensor.TYPE_ACCELEROMETER` | Classifies posture and movement; stationary state gates indoor-location suggestions but not auto-pause. |
| Step counter | `Sensor.TYPE_STEP_COUNTER` | Rebases the reboot-scoped counter and classifies available readings as walking (at least three steps in the last 15 seconds) or stationary; missing readings remain unavailable. |
| Location | `FusedLocationProviderClient` | Uses adaptive high-accuracy updates only when saved indoor locations exist and validates fix age with the monotonic elapsed clock. |
| Microphone | `AudioRecord` | Samples classification at 2 Hz, calculates smoothed RMS/dBFS and blocks suggestions only after five seconds of sustained loud activity. PCM is discarded immediately; no audio is stored or uploaded. |
| Haptic output | `Vibrator` | Signals automatic indoor pause and exposure-limit alerts. |
| Audio output | `ToneGenerator` | Signals exposure-limit and developer-preview alerts using the notification stream. |

All optional hardware is declared with `android:required="false"`. Missing hardware or denied optional permission produces an unavailable value rather than stopping the application.

## Permissions and lifecycle

Location permission is requested by the existing current-location flow. The foreground exposure service collects light, proximity, accelerometer, steps and location only while an exposure session needs monitoring.

Settings contains an explicit **Enhanced sensing** switch. Turning it on requests `ACTIVITY_RECOGNITION` and `RECORD_AUDIO` where required. Microphone sampling runs only while the application lifecycle is at least `STARTED`; it stops when the application leaves the foreground or the switch is disabled. Raw audio never enters application state.

## Decision rules

Automatic indoor detection requires ambient light below 1,000 lux and a fresh, precise GPS fix within 100 m of a user-saved indoor location for 10 continuous seconds. There is no weighted evidence score.

Physical proximity, posture, movement, step count and microphone context do not affect automatic pause or resume. However, physical proximity invalidates low-light shade evidence: unless indoor detection has already been confirmed, a covered phone uses `UNKNOWN` exposure with the conservative 1.0 dose factor. Leaving the detected state also uses a 10-second debounce and occurs when lux rises above 2,000 or the fresh location fix leaves the saved radius. A manually paused countdown is never automatically resumed.

An indoor-location suggestion is separate from detection. It requires low light, available audio that is not sustained loud activity, stationary accelerometer state and an available `STATIONARY` step state (fewer than three steps in the last 15 seconds) at the moment the user manually pauses, plus a usable GPS fix outside all saved radii. Quiet and conversational/uncertain audio are accepted; a smoothed level at or above -15 dBFS must persist for five seconds before it blocks. Missing audio, unavailable step data and `WALKING` step states suppress automatic suggestions. The user must explicitly confirm before anything is saved.

## Adaptive location cadence

- The normal high-accuracy update interval is 30 seconds.
- With no saved indoor locations, continuous location updates are stopped because they cannot contribute to saved-radius detection.
- Inside a saved 100 m radius, stationary, unavailable step data, and minimal activity up to 100 session steps use a 60-second interval.
- Entering `WALKING` after more than 100 session steps starts a 30-second burst of five-second updates.
- The walking transition requests one fresh precise fix unless the existing fix is at most 15 seconds old.
- Triggered fresh fixes are throttled to one per 15 seconds.
- Failed continuous or one-shot location requests clear old indoor evidence. Ordinary failures retry after a 30-second backoff; permission failures stop protected requests until monitoring restarts with permission.
- A GPS fix outside the saved radius publishes outside immediately, which starts the existing 10-second outdoor debounce in `MainViewModel`; steps never clear the indoor state directly.
- Active-session fixes remain usable for 75 seconds so the 60-second cadence does not falsely clear a saved-radius match. Explicit place-saving fixes retain their stricter 30-second limit.

## Alerts

- Confirmed automatic indoor pause: one short vibration.
- First transition to completed exposure: a vibration pattern and notification-stream tone.
- Developer mode: **Test reapply alert** and **Test band warning** invoke real haptic/audio output.
- Repeated state collection and recomposition cannot replay the completion alert because the ViewModel emits it only on the transition into `COMPLETE`.

## Real-device verification

1. Install the debug APK on a physical Android phone.
2. Grant precise location, then open Settings and enable **Enhanced sensing**.
3. Grant physical-activity and microphone permissions.
4. Enable Developer mode to inspect filtered lux, saved-radius proximity, physical proximity, posture, movement, total/recent steps, step state, dBFS and acoustic context.
5. Start an exposure session. Verify step and motion updates while walking and rotating the phone.
6. In a quiet environment, verify microphone values update while the app is visible.
7. Enter a saved 100 m radius, hold the light below 1,000 lux for 10 seconds and verify the countdown pauses with one vibration.
8. Use the developer alert buttons to verify vibration and sound, respecting the phone's current notification volume.

Microphone, proximity, accelerometer and step-counter behavior varies by device, so the JVM test suite validates classifiers and decision rules while final hardware acceptance must use the team's reference phone.
