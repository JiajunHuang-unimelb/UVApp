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

## Current limitations

- Environmental monitoring runs while the app process is alive; there is no foreground service or persistent background monitoring.
- GPS/geofencing, saved indoor locations, and distance calculations are not implemented yet.
- Android's physical proximity sensor is not used for location proximity.
- This workflow uses vibration only; it does not create notification channels or request notification permission.
