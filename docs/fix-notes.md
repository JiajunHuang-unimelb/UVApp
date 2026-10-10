# Fix notes: first-run setup and indoor suggestions

Date: 10 October 2026

These fixes cover what a new user sees on first install, how the app offers its indoor features, and how indoor place suggestions are triggered. They were tested on a Samsung SM-S926B (Android 16) with a fresh install.

## 1. Fresh install no longer shows a saved "Home"

**Problem:** After reinstalling the app, an indoor place called "Home" was already saved.

**Cause:** Android Auto Backup restored the saved places file (`files/datastore/indoor_locations.preferences_pb`) on install. The backup rules only excluded the database, not DataStore files.

**Fix:** `backup_rules.xml` and `data_extraction_rules.xml` now exclude the saved places file and the first-run flag (`shared_prefs/first_run.xml`) from cloud backup and device transfer. Saved places hold precise home and work coordinates, so they stay on the device.

## 2. One-time popup after the first Start

**Behavior:** Enhanced sensing and Suggest indoor places are both off by default. The first time the user taps Start, a popup offers to turn them on. It appears only once; users can turn both options on anytime in Settings.

Popup text:

> **Turn on indoor features?**
> Enhanced sensing and Suggest indoor places let the app notice when you're indoors and offer to save the place, so tracking pauses there.
>
> If you leave them off, you won't get indoor place suggestions, and sound and motion won't be used to judge your surroundings. You can turn them on anytime in Settings.
>
> [Not now] [Turn on]

**Turn on** requests microphone, camera and physical activity access in turn, then notifications, and switches on whichever option is still off. **Not now** leaves both off.

The "already shown" flag lives in `first_run.xml`, which is excluded from backup so a fresh install shows the popup again.

## 3. Suggestions appear automatically

**Problem:** A suggestion only appeared right after the user manually paused tracking.

**Fix:** Suggestions are now checked while a session is running or paused. A suggestion appears when all of these hold:

- Suggest indoor places is on and the place is not already confirmed indoors
- Light is below 1,000 lux
- Sound is available and not sustained loud activity
- The phone is still and the step state is stationary
- A precise location fix (within 50 m, under 30 seconds old) is outside every saved place

Turning suggestions on mid-session checks the current surroundings straight away. There is still at most one suggestion per session, and nothing is saved until the user taps Save. The notification title is now "Are you indoors here?".

## 4. Settings and Indoor locations layout

- Suggest indoor places now sits above Indoor locations in the Sensing card, with the subtitle "Offer to save a place when you're still in low light".
- The "Not now" button was removed from the Indoor locations card on both Home and Settings.

## Verification

- `ktlintCheck`, 197 unit tests and `assembleDebug` passed.
- On the phone, after a fresh install: no saved place, both options off, the popup appeared after the first Start and not on a later Start, and Turn on requested the permissions and enabled both options.
- With the phone still on a desk, a suggestion appeared about 5 seconds after turning the features on, without pausing, and tracking kept running.

## Files changed

- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`: backup exclusions
- `app/src/main/java/com/example/uvapp/ui/UVAppRoot.kt`: one-time popup and permission sequence
- `app/src/main/java/com/example/uvapp/viewmodel/IndoorLocationsViewModel.kt`: automatic suggestion trigger
- `app/src/main/java/com/example/uvapp/ui/components/IndoorLocationsUi.kt`: Settings order, wording, removed "Not now"
- `app/src/main/java/com/example/uvapp/platform/alerts/IndoorSuggestionNotifier.kt`: notification title
- `app/src/test/java/com/example/uvapp/IndoorLocationsTest.kt`: tests for automatic suggestions
