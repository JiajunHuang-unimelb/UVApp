# User stories and MoSCoW

This file catalogs the user stories for UVApp with MoSCoW priority, a size estimate, and Given/When/Then acceptance criteria. The feature list comes from `PLAN.md` Part I.

## Personas

Personas use archetype titles, not personal names. Each one grounds a subset of the stories below.

### Persona A：Outdoor‑Loving Casual User

Someone who spends regular time outdoors for walking, recreation and casual activities. Wants to understand UV levels to make better sun‑safety decisions in daily life.
Goals: Check current UV intensity; plan outdoor time; get basic sun‑safety guidance.
Frustrations: Unsure how strong the sun really is; easily forgets about sun exposure during everyday outings.

### Persona B：Sun‑Sensitive User

Has skin that burns easily. Needs precise exposure tracking to avoid sun damage. Pays close attention to how long they can safely stay outside.
Goals: Track safe sun‑exposure duration; receive reminders for sunscreen re‑application; know real‑time sun‑exposure state.
Frustrations: Prone to quick sunburn; hard to estimate how much time has been spent under direct sunlight.


## Epics

| Epic ID | Epic | Description |
|---|---|---|
| EP‑01 | Real‑time UV & Location Services | Deliver real‑time UV index tied to user location. Provides reverse‑geocoded suburb name for location trust, offline cached UV values for low‑signal conditions. Covers location permission step within first‑run onboarding. |
| EP‑02 | Personalised UV Safety Timers | Enable users to configure skin type and SPF. Drives personalised burn‑time countdown and sunscreen re‑apply reminder notifications for sun‑safety. |
| EP‑03 | Sensor‑Driven Exposure Context Detection | Leverage device onboard sensors (light sensor, accelerometer, optional rear camera) to classify real‑time sun‑exposure state. Supports automatic countdown pause when phone is placed inside a pocket. |
| EP‑04 | Outdoor‑Planning UV Forecast | Provide hourly UV forecast chart to support users making outdoor activity decisions. Incorporates skin‑type capture step from initial onboarding flow. |

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

## Stories

| Epic ID | User Story ID | AS… | I WANT TO… | SO THAT… | Size Estimation | MoSCoW Priority | Size Justification | MoSCoW Justification | Comments |
|---|---|---|---|---|---|---|---|---|---|
| EP‑01 | US‑01 | all| see current UV index for my location | I know how strong solar radiation is before going outside | M | Must | Requires location permission handling, external UV API integration, auto‑refresh logic; moderate backend‑app coordination | Foundational core capability; all other features depend on valid current UV measurement | MVP feature |  
| EP‑01 | US‑02 | all | complete short guided onboarding covering permissions + skin‑type capture | I can begin using the app without manual post‑launch configuration | M | Must | Build multi‑step welcome flow, orchestrate OS permission prompts, one‑time execution guard so onboarding does not re‑appear | New users will otherwise miss critical location and skin‑type setup; directly impacts correctness of all safety calculations | MVP feature |
| EP‑01 | US‑03 | Casual Outdoor User | see place‑name (suburb/city) for current coordinates | I can trust the UV reading matches my actual physical location | S | Must | Reverse geocoding API call + graceful failure fallback to raw lat/long; minimal UI work | Location uncertainty invalidates all UV advice for outdoor activities |  MVP feature |
| EP‑01 | US‑04 | all | keep viewing last‑known cached UV data for offline usage | I still receive UV advice when network signal is poor | S | Must | Implement local cache persistence, cache‑state UI tagging, empty‑cache error state; no complex business logic | Outdoor scenarios frequently have no cellular connectivity; prevents app failure in field conditions |  MVP feature |
| EP‑02 | US‑05 | Sun‑Sensitive User | get burn‑time countdown personalised to my Fitzpatrick skin‑type | I know how long I can safely stay outdoors before sunburn risk rises | M | Must | Skin‑type parameterised countdown calculation logic, live recalculation trigger on setting change, timer UI component | Core safety feature for users vulnerable to sun damage; differentiates app from generic weather tools |  MVP feature |
| EP‑02 | US‑06 | Sun‑Sensitive User | set and edit my skin‑type and SPF value inside Settings | countdown calculations match my personal skin risk profile | S | Must | Build settings screen inputs, persist configuration state, trigger countdown recompute on value change | Skin and SPF are core inputs driving safety timer behaviour |  MVP feature |
| EP‑02 | US‑07 | Sun‑Sensitive User | receive system notification when sunscreen needs re‑applying | I do not forget to re‑apply sunscreen during outdoor activities | S | Could | Notification permission handling, background timer trigger logic, notification tap deep‑link back into app, SPF timer reset | High‑value convenience feature; limited by platform background execution restrictions | Also applicable to Casual Outdoor User |
| EP‑03 | US‑08 | Sun‑Sensitive User | view live “Indoor / In shade / Direct sun” exposure context label | I understand my real‑time sun‑exposure risk level | S | Must | Read hardware light‑sensor samples, implement state‑classification thresholds with sample smoothing, dynamic UI label updates | Enables context‑aware timer logic; helps users interpret current sun conditions |  MVP feature |
| EP‑03 | US‑09 | Sun‑Sensitive User | have countdown auto‑pause | sun‑exposure time is not incorrectly accumulated while my phone is stowed away | M | Should | Combine accelerometer + light‑sensor state detection, pocket‑state label UI, pause/resume timer logic; sensor‑state tuning effort | Reduces measurement error for exposure tracking; non‑blocking for minimum viable safety feature |  |
| EP‑03 | US‑10 | Sun‑Sensitive User | cross‑validate light‑sensor readings with rear‑camera luminance sampling | I get more reliable sun‑exposure classification results | L | Could | Require camera permission flow, camera luminance capture, sensor‑fusion decision rules, error handling for camera access denial | Quality‑of‑life improvement; adds hardware permission complexity, can be deferred under schedule pressure |  |
| EP‑04 | US‑11 | Casual Outdoor User | view hourly UV forecast chart for the day | I can select safer time windows for my outdoor plans | M | Should | Consume hourly forecast dataset, implement scrollable line chart UI, re‑render chart on location change | Supports activity‑planning workflows; not required for core real‑time UV reading |  |

## Won't-have in v1

The following items are out of scope for the first release. See `PLAN.md` Part I "Out of scope" for the full list.

- User accounts, sign-in, or cloud data storage.
- Sharing or social features.
- iOS or web builds.
- Localisation beyond English.
- Background operation when the app is closed.
- Wearable integration.
