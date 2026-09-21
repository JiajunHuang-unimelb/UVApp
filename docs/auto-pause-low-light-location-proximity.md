# Auto-Pause from Low Light and Location Proximity

## Overview

The exposure countdown can now infer that the user is indoors from two signals:

- the ambient light level is low; and
- the user is near a known indoor location.

When both signals remain stable, the app automatically pauses an active exposure countdown and produces a short vibration. This workflow currently uses mock environmental data so the feature can be developed and tested before the GPS/geofence implementation is ready.

## Detection rules

Indoor and outdoor transitions use different light thresholds to avoid repeated state changes around a single boundary:

| Transition | Required condition | Stability period |
| --- | --- | --- |
| Enter indoor | Lux is below `1,000` and indoor-location proximity is true | 10 seconds |
| Leave indoor | Lux is above `2,000`, or indoor-location proximity is false | 10 seconds |

Lux values from `1,000` through `2,000` preserve the current indoor/outdoor classification. If a required condition changes during its stability period, the pending transition is cancelled. A new complete 10-second period is then required.

Low light alone and location proximity alone are insufficient to classify the user as indoors.

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
)

interface EnvironmentContextProvider {
    val samples: StateFlow<EnvironmentSample>
}
```

The application currently injects `MockEnvironmentContextProvider`. A future light-sensor and GPS/geofence implementation should implement the same interface and emit `EnvironmentSample` values. No countdown or indoor-fusion changes should be required when that provider is replaced.

`ExposureAlertGateway` separates alert side effects from the ViewModel. The current Android implementation checks for an available vibrator and performs a best-effort 250 ms vibration. A missing vibrator or unavailable vibration permission does not interrupt exposure tracking.

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

The exposure indicator's lux slider can also supply mock light values. The explicit developer light-level override takes precedence while enabled.

## Verification

Unit coverage includes:

- low light without location proximity;
- location proximity without low light;
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

An automatic suggestion requires an actual running-to-manually-paused transition, effective lux below 1,000, suggestions enabled, a usable GPS fix outside saved radii, and no pending candidate. Only one automatic opportunity is considered per exposure session. Explicit saving remains available after dismissal. Low light alone and automatic pauses do not prompt.

When hidden, a pending suggestion produces **Were you indoors when you paused?** with a tap action opening the app's confirmation dialog. It never opens a screen without a tap or saves automatically. There is only one pending suggestion; notification slot 2002 is reused for it, updated without repeating alerts, and cancelled on confirmation/dismissal. If notifications are denied, the persisted candidate remains available on the next app visit. The present app has no background pause action/service: this notification path handles pending work completing while hidden and is ready for future service integration.

### Proximity and development testing

`MainUiState.nearIndoorLocation` is now nullable: true = within a saved radius, false = usable fix outside all radii, null = missing/stale/unusable fix. Location fixes and saved-list edits recalculate proximity; a one-second expiry check changes expired results to unknown. Unknown cancels location-based entry/exit decisions rather than acting as outside. Bright light can still establish the existing exit condition. Location freshness uses the 30-second limit above.

The developer **Near known indoor location** override still forces true while enabled. Disabling it returns to calculated proximity. A separate, explicitly enabled **Demo: University Square radius** uses the university map's marker at -37.7986, 144.9602, with a 100 m radius, held only in memory and never inserted into saved places. This is a campus demonstration marker, not evidence of an indoor building. Coordinate source: [University of Melbourne map](https://maps.unimelb.edu.au/point?poi=1001526284). Leaving developer mode disables it.

For emulator testing, provide a precise location in Extended controls → Location, choose I’m indoors here, name and save it, and use the mock light controls. Use Locate to obtain another fix after changing emulator coordinates. Check rename/delete and cancellation, then enable save suggestions, obtain a fresh fix outside all saved radii, start exposure in low light, and manually pause. The timer must remain manually paused after saving. Test notification permission denied and permitted, Activity recreation with a pending dialog, and tapping the notification after backgrounding while a save request finishes.

Automated coverage includes quality/distance boundaries, opt-in saving, duplicate replacement, manual pause eligibility, once-per-session suppression, captured-candidate restoration, and DataStore disk persistence. A Compose instrumentation test exercises saving, rename/delete, empty state, dismissal and visibility restoration. Instrumentation requires a connected emulator/device; compiling the test APK does not mean these device tests have executed.

Validation on 21 September 2026: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` succeeded (86 tests passed, 2 skipped, no failures). No device was connected, so instrumentation and on-device notification permission/tap checks remain pending. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations

- Environmental monitoring runs while the app process is alive; there is no foreground service or persistent background monitoring.
- Saved places and distance calculations now use one-shot GPS fixes; continuous GPS/geofencing and real light-sensor integration remain pending. Movement between fixes is not detected automatically.
- Android's physical proximity sensor is not used for location proximity.
- Indoor auto-pause still uses vibration only. Save suggestions use their own notification channel and contextual notification permission request.
