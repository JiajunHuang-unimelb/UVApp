# User stories and MoSCoW

This file catalogs the user stories for UVApp with MoSCoW priority, a size estimate, and Given/When/Then acceptance criteria. The feature list comes from `PLAN.md` Part I.

## Format

Story shape: **As a [role], I want [goal] so that [benefit].**

Priority uses MoSCoW: **Must**, **Should**, **Could**, or **Won't**.

Size uses T-shirt units. It reflects estimated implementation effort for a two-person pair, based on the module LOC guide in `PLAN.md` Part V.

| Size | Rough effort         |
| ---- | -------------------- |
| XS   | under a day          |
| S    | 1-2 days             |
| M    | 3-5 days             |
| L    | 1-2 weeks            |
| XL   | more than two weeks  |

Acceptance criteria are one or more Given/When/Then rules. Each rule sits on its own row. When a story has more than one rule, only the first row repeats the story text, priority, and size.

## Personas

Personas use archetype titles, not personal names. Each one grounds a subset of the stories below.

### The Cyclist (primary)

- 28, weekend road cyclist, fair skin (Fitzpatrick II).
- Goals: know UV before a ride, avoid a bad burn on long rides, get a reminder to reapply sunscreen at rest stops.
- Frustrations: guessing sun strength through cloud, forgetting the reapply timer, keeping the phone in a jersey pocket without the countdown drifting.
- Anchors: US-01, US-02, US-08, US-11.

### The Tourist (secondary)

- 32, first-time visitor to Melbourne from a lower-UV country, medium-fair skin (Fitzpatrick III).
- Goals: understand how strong Australian UV is, decide whether to spend the afternoon outdoors, confirm which suburb the app is reading.
- Frustrations: unfamiliar with local UV levels, does not carry a UV meter, does not want to install extra apps.
- Anchors: US-01, US-03, US-04, US-09.

The Cyclist justifies the fair-skin edge case in US-02 and the pocket-pause behaviour in US-08. The Tourist justifies the place-name display (US-04) and the hourly chart (US-09).

## Mental model

Users think in feelings, not in UV-index numbers. They think of "shade" as "cannot feel heat on skin", not as a lux threshold. They think of sunburn as a time budget ("how long can I stay out?"), which matches the countdown metaphor in US-02.

The app maps user vocabulary to on-screen labels. Keep these pairings consistent when writing `strings.xml`.

| User word           | App word / label            | Where          |
| ------------------- | --------------------------- | -------------- |
| "sun strength"      | UV index + colour band      | Main screen    |
| "in the shade"      | "In shade"                  | Context label  |
| "how long is safe?" | "Safe minutes"              | Countdown      |
| "put on sunscreen"  | "Reapply sunscreen now."    | Notification   |
| "where am I?"       | Suburb + city (Nominatim)   | Main screen    |

## Stories

| ID    | Story                                                                                                                             | Priority | Size | Given                                                                                    | When                                             | Then                                                                             |
| ----- | --------------------------------------------------------------------------------------------------------------------------------- | -------- | ---- | ---------------------------------------------------------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------- |
| US-01 | As a user outdoors, I want to see the current UV index for my location so that I know how strong the sun is.                      | Must     | M    | I grant location permission                                                              | the main screen opens                            | the app shows the current UV index within 5 seconds                              |
|       |                                                                                                                                   |          |      | the device has fresh UV data                                                             | the hour rolls over                              | the shown value updates without a manual refresh                                 |
| US-02 | As a user with fair skin, I want a burn-time countdown personalized to my skin type so that I know when to seek shade.            | Must     | M    | I set my Fitzpatrick skin type to II                                                     | the current UV index is 8                        | the countdown starts under 20 minutes                                            |
|       |                                                                                                                                   |          |      | I change my skin type in Settings                                                        | I return to the main screen                      | the countdown recomputes with the new value                                      |
| US-03 | As a user moving between rooms and outdoors, I want a live "Indoor / In shade / Direct sun" label so that I understand my exposure. | Must   | S    | the phone sits on a table and the light sensor reads under 200 lux                       | I open the main screen                           | the context label shows "Indoor" within 3 seconds                                |
|       |                                                                                                                                   |          |      | the light sensor reading rises above 10,000 lux for three consecutive samples            | the classifier runs                              | the label switches to "Direct sun"                                               |
|       |                                                                                                                                   |          |      | the light reading sits between 200 and 10,000 lux                                        | the classifier runs                              | the label shows "In shade"                                                       |
| US-04 | As a user checking the app, I want to see the place name (suburb, city) for my current coordinates so that I trust the location.  | Must     | S    | the device has fresh coordinates                                                         | the main screen opens                            | the app shows a place name from the Nominatim service                            |
|       |                                                                                                                                   |          |      | the reverse-geocoding call fails                                                         | the main screen renders                          | the app shows the raw latitude and longitude instead                             |
| US-05 | As a user with specific skin, I want to set and change my skin type and SPF in Settings so that the countdown fits me.            | Must     | S    | I am on the main screen                                                                  | I tap the Settings icon                          | the Settings screen opens with my current skin type and SPF selected             |
|       |                                                                                                                                   |          |      | I pick a new skin type                                                                   | I press back                                     | the app saves the new value and updates the countdown                            |
| US-06 | As a new user, I want a short onboarding that explains permissions and captures my skin type so that I can start without friction. | Must    | M    | I open the app for the first time                                                        | the app launches                                 | the Welcome screen appears with a one-sentence explanation                       |
|       |                                                                                                                                   |          |      | I am on the Location step                                                                | I tap Allow                                      | the system permission dialog for fine location appears                           |
|       |                                                                                                                                   |          |      | I complete the Skin type step                                                            | I tap Continue                                   | the main screen opens and onboarding does not appear again                       |
| US-07 | As a user in a low-signal area, I want the app to keep showing the last-known UV data so that I still get advice offline.         | Must     | S    | the app has cached UV data for my location                                               | the network is unavailable                       | the main screen shows the cached value with a "cached" tag                       |
|       |                                                                                                                                   |          |      | the cache is empty and the network is unavailable                                        | the main screen loads                            | the app shows "UV data unavailable" instead of a crash                           |
| US-08 | As a user who pockets the phone, I want the countdown to pause automatically so that pocket time does not count as sun time.      | Should   | M    | the accelerometer variance is low and the light reading is under 50 lux for 10 seconds   | the posture detector runs                        | the countdown pauses and the label shows "Paused (phone in pocket)"              |
|       |                                                                                                                                   |          |      | I take the phone out of my pocket                                                        | the light reading rises above 200 lux            | the countdown resumes within 5 seconds                                           |
| US-09 | As a user planning my day, I want an hourly UV forecast chart so that I can pick a safer time to go outside.                      | Should   | M    | the app has hourly UV data for today                                                     | I scroll down on the main screen                 | a line chart shows UV values across the next 12 hours                            |
|       |                                                                                                                                   |          |      | I change my location                                                                     | the fetch completes                              | the chart redraws with data for the new location                                 |
| US-10 | As a user who wants a second opinion, I want the app to cross-check the light-sensor reading with a rear-camera luminance sample. | Could    | L    | I granted the camera permission                                                          | the light-sensor label reads "Direct sun"        | the app takes a luminance sample from the rear camera within 10 seconds          |
|       |                                                                                                                                   |          |      | the camera luminance disagrees with the light-sensor label                               | the fusion rule runs                             | the app shows the more conservative label                                        |
| US-11 | As a user applying sunscreen, I want a notification when it is time to reapply so that I stay protected without watching the clock. | Could  | S    | I granted the notification permission and my SPF timer runs out                          | the app is in the background                     | a notification with the text "Reapply sunscreen now." appears                    |
|       |                                                                                                                                   |          |      | I tap the notification                                                                   | the app opens                                    | the main screen shows and the SPF timer resets                                   |
| US-12 | As a returning user, I want to sign in with a Google account so that my settings sync across devices.                             | Won't    | XS   | this is v1                                                                               | any user opens the app                           | no sign-in flow appears                                                          |

## Won't-have in v1

The following items are out of scope for the first release. See `PLAN.md` Part I "Out of scope" for the full list.

- User accounts, sign-in, or cloud data storage.
- Sharing or social features.
- iOS or web builds.
- Localisation beyond English.
- Background operation when the app is closed.
- Wearable integration.
