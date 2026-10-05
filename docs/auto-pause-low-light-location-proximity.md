# Auto-Pause from Multi-Sensor Environmental Context

## Overview

The exposure countdown identifies a confirmed indoor context from two signals:

- the ambient light level is low; and
- the user is within 100 m of a saved indoor location.

When both conditions remain stable, the app automatically pauses an active exposure countdown and produces a short vibration. Production sessions also read proximity, accelerometer and step-counter data for diagnostics and suggestion context, but those signals do not classify a place as indoors. The optional microphone monitor runs only while the app is visible and Enhanced sensing is enabled.

## Detection rules

Indoor and outdoor transitions use different light thresholds to avoid repeated state changes around a single boundary:

| Transition | Required condition | Stability period |
| --- | --- | --- |
| Enter indoor | Lux is below `1,000` and the current fix is within 100 m of a saved indoor location | 10 seconds |
| Leave indoor | Lux is above `2,000`, or the current fix leaves the saved radius | 10 seconds |

Lux values from `1,000` through `2,000` preserve the current indoor/outdoor classification. If a required condition changes during its stability period, the pending transition is cancelled. A new complete 10-second period is then required.

Low light alone and saved-location proximity alone are insufficient because both are required. Physical proximity, microphone context, posture, movement and steps never change the automatic indoor classification.

## Countdown behavior

- A confirmed indoor transition pauses a running exposure session.
- The transition vibrates once when it causes an automatic pause.
- Repeated indoor samples do not repeat the vibration.
- Returning outdoors resumes the countdown only when indoor detection caused the pause.
- A manually paused countdown remains paused after returning outdoors.
- Starting or resetting a countdown while already classified indoors creates an immediately paused session without producing a duplicate vibration.

Pause ownership is exposed by `MainUiState.pauseReason` and enforced by `MainViewModel`. This prevents the environmental workflow from overriding an explicit user pause while allowing the frontend to explain why the timer stopped.

## Architecture

`EnvironmentContextProvider` is the boundary between environmental inputs and exposure logic:

```kotlin
data class EnvironmentSample(
    val lux: Int,
    val nearIndoorLocation: Boolean,
    val deviceOccluded: Boolean?,
    val posture: DevicePosture?,
    val isMoving: Boolean?,
    val stepsSinceStart: Int?,
    val recentSteps: Int?,
    val stepsPerMinute: Int?,
    val lastStepElapsedMillis: Long?,
    val stepActivity: StepActivity,
    val soundLevelDb: Double?,
    val acousticContext: AcousticContext?,
)

interface EnvironmentContextProvider {
    val samples: StateFlow<EnvironmentSample>
}
```

The application injects the process-scoped `AndroidEnvironmentContextProvider`. `ExposureMonitoringService` registers light, proximity, accelerometer and permitted step-counter sensors, reads saved indoor locations from DataStore and publishes combined `EnvironmentSample` values. High-accuracy fused-location updates normally run every 30 seconds. While inside a saved radius with stationary, unknown, or at most 100 session steps of walking activity, the interval becomes 60 seconds. Entering walking activity after more than 100 session steps starts a 30-second burst of five-second updates and requests one fresh fix unless the latest fix is already no more than 15 seconds old or another triggered request occurred within 15 seconds. A lifecycle-bound microphone monitor publishes sound context while the visible app has permission. Missing hardware is represented by `null`. `AndroidExposureMonitoringController` starts the service when a session starts and stops it when the session completes or its owning ViewModel is cleared.

The foreground service collects facts only. Saved-radius gating, thresholds, debounce, pause ownership and exposure calculations remain in `MainViewModel`. `MockEnvironmentContextProvider` remains available for deterministic unit tests.

If the sensor, location or service becomes unavailable, the provider publishes a conservative high-light/outside state. That prevents an old indoor classification from suppressing outdoor exposure indefinitely.

`ExposureAlertGateway` separates alert side effects from the ViewModel. Indoor auto-pause uses a short vibration. The first transition to completed exposure uses a vibration pattern plus `ToneGenerator`, and developer alert buttons exercise the same real output path. Missing output hardware never interrupts exposure tracking.

## Frontend integration

The frontend should collect `MainViewModel.state` and use these fields:

- `indoorDetected` indicates the current stable indoor classification.
- `exposureStatus` indicates whether the timer is running, paused, complete, or not started.
- `pauseReason` is `MANUAL`, `INDOOR_DETECTED`, or `null`. It is non-null only while the session is paused.

A persistent message can be selected without generating side effects from Compose:

```kotlin
val message = when {
    state.pauseReason == ExposurePauseReason.INDOOR_DETECTED ->
        "Indoors detected — exposure timer automatically paused"
    state.indoorDetected ->
        "Indoors detected"
    else -> null
}
```

Render that message conditionally as a banner or status row. Do not infer an automatic pause from `indoorDetected && exposureStatus == PAUSED`, because a user can manually pause while indoors. Use `pauseReason` for precise wording.

Compose should not trigger vibration or a system notification from these state values. Recomposition and Activity recreation can repeat those effects. `ExposureAlertGateway` owns the one-time vibration at the confirmed transition. If a future design needs a one-time snackbar, add a dedicated UI-event stream rather than treating `indoorDetected` as an event.

The current state does not expose the remaining time in the 10-second debounce. The frontend should not display detection progress until a dedicated pending/progress value is added.

## Testing with developer controls

1. Enable **Developer mode** in Settings.
2. Start the exposure countdown.
3. Enable **Mock light level** and select **Indoors**. This supplies a mock value of 500 lux.
4. Enable **Near known indoor location**.
5. Keep both settings unchanged for 10 seconds. The countdown should pause and the device should vibrate once.
6. Select **Shade** or **Direct sun**, or disable **Near known indoor location**.
7. Keep the outdoor condition unchanged for 10 seconds. The countdown should resume if it was automatically paused.

The **Simulate occluded (in pocket)** control is diagnostic only and cannot trigger an indoor auto-pause without saved-location proximity.

Before a session starts, the exposure indicator's lux slider can supply a mock light value for previewing the UI. Starting a session clears that override and makes the indicator read-only so live sensor values cannot be replaced accidentally. The explicit developer light-level override remains available for deliberate in-session testing and takes precedence while enabled.

## Verification

Unit coverage includes:

- low light without location proximity;
- location proximity without low light;
- physical proximity with low light remaining insufficient;
- quiet and stationary context with low light remaining insufficient for auto-pause;
- accelerometer posture and movement classification;
- step-counter rebasing, rolling recent-step count, rate and `UNKNOWN`/`WALKING`/`STATIONARY` transitions;
- adaptive 30/60/5-second location cadence, fresh-fix suppression and refresh throttling;
- microphone RMS/dBFS classification;
- the full 10-second entry debounce;
- signal flapping and debounce restart;
- hysteresis between 1,000 and 2,000 lux;
- one vibration per confirmed transition;
- automatic resume ownership;
- preservation of manual pauses; and
- starting an exposure while already indoors.

The full JVM unit suite and debug APK build pass with this implementation.

## Saved indoor locations and confirmation

Home now offers **I’m indoors here**. This requests a fresh precise GPS fix and opens a confirmation dialog with a name field, Home/University/Work presets, Save and Not now. Nothing is added to saved locations until Save succeeds. Settings → Indoor locations provides rename/delete, a delete confirmation, and an opt-in switch for suggestions after manual pauses. The first-use invitation can be dismissed independently of saving a location.

Locations, invitation dismissal, suggestion preference, and the pending candidate are serialized in the separate `indoor_locations` Preferences DataStore. No Room schema is involved. Locations have a stable ID, name, coordinates, creation time and a fixed 100 m radius. A save within an existing radius offers to use that place or rename it rather than creating a duplicate. Coordinates are kept on device by this repository.

Saving requires precise permission, accuracy <= 50 m and a timestamp no older than 30 seconds. A failed fix shows an error with Retry location. The save provider bypasses the forecast provider's five-minute cache. Candidate coordinates and capture time remain fixed while the dialog is open, including after process recreation; saving an old pending candidate deliberately saves the originally captured place, not wherever the user moved later.

### Frontend state and actions

Collect `IndoorLocationsViewModel.state` with `collectAsStateWithLifecycle()`. `IndoorLocationsUiState` contains `data.locations`, `data.pending`, `name`, `loading`, `saving`, `error`, `visible` and the development-only `demoEnabled`. The pending candidate has a stable ID, latitude, longitude, capture time and persisted draft name.

- Invoke the shared location permission requester before `requestSave()`; route denial to `permissionDenied()`.
- Render `IndoorSuggestionDialog` only when a pending candidate exists and the app is visible. It is state-driven, so recomposition does not create a new candidate.
- Use `setName`, `confirm`, `dismiss`, `rename`, `delete`, and `dismissInvitation` for user actions. Keep Save disabled while saving; show storage errors without discarding the pending candidate.
- Enabling suggestions requests notification permission contextually. Denial still allows in-app suggestions. The existing Notifications preference suppresses background notifications.
- Forward lifecycle visibility through `setVisible`. The controller owns notification delivery; composables must not post notifications during rendering.
- Continue using `MainUiState.pauseReason` for pause messaging. Saving a place never transfers ownership of a manual pause to indoor detection.

### Suggestions and notifications

An automatic suggestion requires an actual running-to-manually-paused transition, effective lux below 1,000, available microphone context that is not sustained loud activity, a stationary accelerometer classification, step activity classified as `STATIONARY` after 60 seconds without steps, suggestions enabled, a usable GPS fix outside saved radii, and no pending candidate. Quiet and conversational/uncertain audio are accepted. Audio must remain at or above -15 dBFS for five continuous seconds before it becomes `ACTIVE_OUTDOOR_LIKELY` and blocks a suggestion. Stationary acceleration and step inactivity remain required; missing microphone or step context suppresses only the automatic suggestion. Explicit saving remains available. Only one eligible automatic opportunity is considered per exposure session. Low light alone and automatic pauses do not prompt.

When hidden, a pending suggestion produces **Were you indoors when you paused?** with a tap action opening the app's confirmation dialog. It never opens a screen without a tap or saves automatically. There is only one pending suggestion; notification slot 2002 is reused for it, updated without repeating alerts, and cancelled on confirmation/dismissal. If notifications are denied, the persisted candidate remains available on the next app visit. The present app has no background pause action/service: this notification path handles pending work completing while hidden and is ready for future service integration.

### Proximity and development testing

`MainUiState.nearIndoorLocation` remains nullable for the one-shot save workflow. During an active exposure session, `ExposureMonitoringService` recalculates proximity from adaptive fixes and saved-list edits. A service fix must be no more than 75 seconds old (covering the 60-second stationary interval) and accurate within 50 m; unavailable or stale evidence is treated conservatively as outside so an automatic indoor pause can clear. Explicit place saving still requires its separate one-shot fix to be no more than 30 seconds old.

The developer **Near known indoor location** override still forces true while enabled. Disabling it returns to calculated proximity. A separate, explicitly enabled **Demo: University Square radius** uses the university map's marker at -37.7986, 144.9602, with a 100 m radius, held only in memory and never inserted into saved places. This is a campus demonstration marker, not evidence of an indoor building. Coordinate source: [University of Melbourne map](https://maps.unimelb.edu.au/point?poi=1001526284). Leaving developer mode disables it.

For emulator testing, provide a precise location in Extended controls → Location, choose I’m indoors here, name and save it, and use the mock light controls. Use Locate to obtain another fix after changing emulator coordinates. Check rename/delete and cancellation. To exercise automatic suggestions, enable Enhanced sensing and save suggestions, obtain a fresh fix outside all saved radii, then manually pause an exposure in low light while audio is not persistently loud, the accelerometer is stationary and the step state has reached `STATIONARY`. Normal conversation may remain `UNCERTAIN` and is accepted. The timer must remain manually paused after saving. Test notification permission denied and permitted, Activity recreation with a pending dialog, and tapping the notification after backgrounding while a save request finishes.

Automated coverage includes quality/distance boundaries, opt-in saving, duplicate replacement, manual pause eligibility, once-per-session suppression, captured-candidate restoration, and DataStore disk persistence. A Compose instrumentation test exercises saving, rename/delete, empty state, dismissal and visibility restoration. Instrumentation requires a connected emulator/device; compiling the test APK does not mean these device tests have executed.

Validation on 23 September 2026: `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug ktlintCheck` succeeded, and both connected instrumentation tests passed on a Samsung SM-S926B running Android 16. A side-by-side debug install registered the phone's `STK33F11 Light` sensor, observed live readings of 7–10 lux, received fresh fused fixes accurate to 3–8 m, and automatically paused a running session after the saved-location plus low-light debounce. The foreground service, partial wake lock and light-sensor listener remained active through a manual pause, Home/backgrounding and screen-off. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations

- Monitoring begins only after the user starts an exposure session and location permission is available.
- The foreground service uses adaptive 30/60-second fused-location updates with a temporary five-second walking burst rather than Android geofencing. It is intentionally `START_NOT_STICKY`; a killed app process does not restore an in-memory exposure session.
- Saving an indoor place still uses a separate fresh one-shot GPS fix, while active-session proximity uses continuous service updates.
- Proximity sensors are commonly binary and are retained only as diagnostics that the phone is covered; they do not affect indoor detection.
- Microphone level depends on device gain and is used only as a sustained-loudness veto alongside stationary motion and step inactivity. Quiet and conversational/uncertain levels are accepted; audio is never a UV measurement or definitive indoor classification.
- Microphone sampling intentionally stops when the app is no longer visible; continuous background access would require an additional foreground-service type and user-facing policy justification.
- Step-counter hardware is optional and may deliver updates with several seconds of latency.
