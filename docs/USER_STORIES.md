# User stories and MoSCoW

This file collects user stories for UVApp and ranks each one with a MoSCoW priority. The feature list comes from `PLAN.md` Part I ("In scope" and "Out of scope"). It is a starter set, not the full backlog. Add more stories in later pull requests.

## Format

Write each story in the standard shape:

> As a **[role]**, I want **[goal]** so that **[benefit]**.

Tag every story with one MoSCoW value: **Must**, **Should**, **Could**, or **Won't**.

## User stories

**US-01** - As a user outdoors, I want to see the current UV index for my location so that I know how strong the sun is right now.
Priority: **Must**

**US-02** - As a user with fair skin, I want a burn-time countdown personalized to my skin type so that I know when to seek shade.
Priority: **Must**

**US-03** - As a user who puts the phone in a pocket, I want the app to pause the countdown so that pocket time does not count as sun time.
Priority: **Should**

**US-04** - As a user applying sunscreen, I want a notification when it is time to reapply so that I stay protected without watching the clock.
Priority: **Could**

## MoSCoW summary

| ID    | Story (short)                            | Priority | Rationale                                                                 |
| ----- | ---------------------------------------- | -------- | ------------------------------------------------------------------------- |
| US-01 | See the current UV index for my location | Must     | Core value. The app has no purpose without it.                            |
| US-02 | Burn-time countdown by skin type         | Must     | Second core promise. Uses the Fitzpatrick model in `PLAN.md` Part VI.     |
| US-03 | Pause the countdown when in a pocket     | Should   | Uses the accelerometer path (`PLAN.md` Part V, module 7). On the cut list at rank 3. |
| US-04 | Reapply-sunscreen notification           | Could    | Nice to have. Needs `POST_NOTIFICATIONS` and WorkManager.                 |