# PLAN - UV/Sun-Protection Advisor

> **Reader**: this document initialises development for a COMP90018 group project. It will also be consumed by an AI coding agent later.
>
> The app name is not yet decided. Throughout this document, `<app_name>` is a placeholder. Once the team picks a name, do a repo-wide search and replace.

---

## Table of contents

- Part I - Project foundations
- Part II - GitHub setup
- Part III - Android Studio setup (per developer)
- Part IV - Project scaffolding
- Part V - Architecture
- Part VI - Data and sensors
- Part VII - UI
- Part VIII - Quality
- Part IX - Deliverables
- Part X - Risk and backup
- Appendix A - Glossary
- Appendix B - Reference links
- Appendix C - Commit message convention
- Appendix D - Full configuration file contents

---

# Part I - Project foundations

## Project overview

`<app_name>` is an Android utility app. It shows the UV index at the user location. It computes a personalised "time until sunburn" countdown based on skin type. Phone sensors detect whether the user is indoors, in shade, or in direct sun. The app adjusts the risk score for each case. The countdown pauses when the phone is in a pocket.

**In scope**

- Real-time UV index from a keyless API
- Skin-type-aware burn-time countdown
- Context classification (indoor / shade / direct sun) from the ambient light sensor
- Camera-based luminance cross-check (optional module)
- Phone posture detection (face-up / in-pocket / in-hand) from the accelerometer
- "Reapply sunscreen" push notification
- Reverse-geocoded place name display
- Local caching for offline use
- Settings for skin type and SPF

**Out of scope**

- User accounts, sign-in, or cloud data storage
- Sharing or social features
- iOS or web builds
- Localisation beyond English (v1)
- Background operation when the app is closed
- Wearable integration

**Success criteria**

- The app builds cleanly on any team member's laptop
- The app runs on a physical Android phone
- All 5 required rubric categories are addressed with evidence
- Every team member can explain their module in a viva

---

# Part II - GitHub setup

## Prerequisites

Every member must:

1. Create a GitHub account at https://github.com/join.
2. Enable two-factor authentication: Settings → Password and authentication → Two-factor authentication → Enable.
3. Post their GitHub username in the team chat by end of day 1.

Verification: run `curl https://api.github.com/users/<username>` for each member. Expect HTTP 200.

## Repository settings

Repo owner does the following in Settings.

1. **General → Default branch**: confirm it is `main`.
2. **General → Pull Requests**:
   - Allow squash merging: check
   - Allow merge commits: uncheck
   - Allow rebase merging: uncheck
   - Automatically delete head branches: check
3. **General → Features**: turn off Wikis, Projects, and Discussions (until the team needs them).
4. **About** (right side of the repo home page): add topics `android`, `kotlin`, `compose`, `uv`, `health`.

## Add collaborators

1. Settings → Collaborators → Add people.
2. Add each of the 5 remaining members by GitHub username.
3. Set role to **Write** for each.
4. Each invited member accepts the invitation via email or notification.

Verification: Settings → Collaborators shows 6 members total, all with Write role.

## Branch protection

Settings → Branches → Branch protection rules → Add rule.

1. Branch name pattern: `main`.
2. **Require a pull request before merging**: check.
   - Require approvals: 1.
   - Dismiss stale approvals when new commits are pushed: check.
   - Require review from Code Owners: check.
3. **Require status checks to pass before merging**: check.
   - Require branches to be up to date before merging: check.
   - Status checks (add after the first CI run has happened): `build`, `test`, `ktlint`.
4. **Require conversation resolution before merging**: check.
5. **Restrict who can push to matching branches**: leave empty.
6. **Do not allow bypassing the above settings**: check.
7. **Allow force pushes**: uncheck.
8. **Allow deletions**: uncheck.
9. Click Create.

Verification: try to push directly to `main` from a clone. Expect: rejected.

## Repository files to add on first push

Create these files on a branch called `chore/repo-setup` and merge via a PR. Full file contents are in Appendix D.

| File | Purpose |
|---|---|
| `.gitignore` | Excludes build artifacts, IDE files, OS files, secrets |
| `CODEOWNERS` | Maps module paths to owners for automatic PR review requests |
| `.github/pull_request_template.md` | Standard PR description |
| `.github/ISSUE_TEMPLATE/bug.md` | Bug report template |
| `.github/ISSUE_TEMPLATE/feature.md` | Feature request template |
| `.github/workflows/android-ci.yml` | CI pipeline: build + test + ktlint |
| `.github/dependabot.yml` | Weekly dependency updates |
| `README.md` | Project name, one-line pitch, build status badge, quick-start |
| `config/.editorconfig` | Ktlint config (imported by Android Studio) |

## GitHub Actions workflow

Full YAML is in Appendix D. Summary of what it does:

- Runs on every push to `main` and every pull request targeting `main`.
- Job `build`: `./gradlew assembleDebug`.
- Job `test`: `./gradlew testDebugUnitTest`.
- Job `ktlint`: `./gradlew ktlintCheck`.
- Uploads the debug APK as an artifact on merge to `main`.
- Caches Gradle dependencies between runs to save time.

## Dependabot

Full YAML is in Appendix D. Summary:

- Ecosystem `gradle`, weekly, target `main`.
- Ecosystem `github-actions`, weekly, target `main`.
- Grouping: minor + patch grouped into one PR per ecosystem per week.

## Secrets policy

This app requires **no API keys**. Open-Meteo and Nominatim are keyless.

Rules:

1. Never commit `local.properties` (already in `.gitignore`).
2. Never commit `keystore.jks` or any `.jks` file.
3. If Google Maps SDK is added later, put the key in `local.properties` under `MAPS_API_KEY=…` and read it via `manifestPlaceholders` in `build.gradle.kts`. The key is loaded at build time, never checked in.
4. GitHub Actions secrets: none required for v1.

Verification: run `git ls-files | grep -Ei '(secret|key|password|token|jks|local\.properties)'`. Expect empty output.

---

# Part III - Android Studio setup (each developer)

Every member does these steps on their own machine.

## Download

- URL: https://developer.android.com/studio
- Version: **Android Studio Ladybug (2024.2.1)** or newer stable channel.
- Windows: download the `.exe` installer.
- macOS: download the `.dmg` for the correct chip (Apple Silicon `arm64` or Intel `x86_64`).
- Linux: download the `.tar.gz` archive.

Verification: file size ≥ 1 GB and downloaded checksum matches the page (if provided).

## Install

- Windows: run the installer, accept defaults, allow through firewall.
- macOS: drag Android Studio to Applications, then open. Approve the Gatekeeper prompt.
- Linux: extract the archive to `$\approx$ /android-studio`, then run `bin/studio.sh`.

On first launch:

1. Choose **Standard** setup type.
2. Accept all licences.
3. Let it download the default SDK components (about 3 GB).
4. Wait for the initial index to finish.

Verification: the Android Studio welcome screen appears without errors in the event log.

## SDK Manager

Open Android Studio → More Actions → SDK Manager (or `Tools → SDK Manager` in an open project).

**SDK Platforms tab** - check the following, then click Apply:

- Android 14.0 (API 34) - **compileSdk + targetSdk**
- Android 8.0 (API 26) - **minSdk**

**SDK Tools tab** - check the following, then click Apply:

- Android SDK Build-Tools 34.0.0
- Android SDK Command-line Tools (latest)
- Android SDK Platform-Tools
- Android Emulator
- Google Play services

Verification: `adb --version` in a terminal returns a version string. If not, add `$\approx$ /Library/Android/sdk/platform-tools` (macOS) or `%LOCALAPPDATA%\Android\Sdk\platform-tools` (Windows) to your PATH.

## JDK

Android Studio ships with **JetBrains Runtime (JBR) 17**. Use it. Do not install a separate JDK unless required.

Verify: `File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`. Confirm it points to the bundled JBR-17. If it is empty or wrong, click Add JDK → Download JDK → Vendor: JetBrains Runtime → Version: 17.

Verification: open the terminal inside Android Studio and run `./gradlew --version`. Expect a Gradle version and JVM 17.

## AVD Manager (emulator)

Tools → Device Manager → Create Device.

1. Category: Phone.
2. Device: Pixel 7.
3. System image: Android 14 (API 34) with the **Google Play** target. Download it if not present.
4. Advanced settings: RAM 2048 MB, Internal Storage 6 GB.
5. Click Finish.
6. Click ▶ next to the new AVD to cold-boot it. Wait for the home screen to appear.

Verification: the emulator boots to the Android home screen within 2 minutes.

## Physical device setup

Only one member (the "reference device owner") strictly needs a physical device with a camera, light sensor, and accelerometer. Others may use the emulator for most work. Physical device is required for the final video and final sensor testing.

Steps for the reference device:

1. Open Settings → About phone → tap "Build number" 7 times. This unlocks Developer options.
2. Settings → System → Developer options → enable **USB debugging**.
3. Connect the phone to the laptop with a USB cable.
4. On the phone, accept the "Allow USB debugging" prompt. Check "Always allow from this computer".
5. In a terminal, run `adb devices`. Expect one line: `<serial>  device`.

Verification: run `adb shell dumpsys sensorservice | grep -Ei 'light|accel'`. Expect at least Light and Accelerometer entries.

## Code style (ktlint / editorconfig)

Once the repo has `config/.editorconfig` (Appendix D):

1. File → Settings → Editor → Code Style → check "Enable EditorConfig support".
2. File → Settings → Editor → General → Auto Import: enable "Optimize imports on the fly".
3. File → Settings → Tools → Actions on Save: enable "Reformat code" and "Optimize imports".

Verification: open any Kotlin file, add extra spaces, press save. Expect them to be removed.

## Git integration

1. File → Settings → Version Control → Git. Confirm Git path (`git --version` in terminal to check).
2. File → Settings → Version Control → GitHub → click `+` → Log In via GitHub → open browser → authorise the plugin.
3. Terminal (inside Android Studio):
   ```bash
   git config --global user.name "Your Full Name"
   git config --global user.email "your.email@student.unimelb.edu.au"
   git config --global pull.rebase false
   git config --global init.defaultBranch main
   ```

Verification: `git config --global --list` shows the values above.

## Setup verification (mandatory before starting real work)

Once Sections 13–20 are done:

1. Clone the repo: `git clone git@github.com:<owner>/<app_name>-android.git`.
2. Open the folder in Android Studio: File → Open → select the folder.
3. Wait for Gradle sync to finish (green "Sync succeeded" in the status bar).
4. Click the ▶ Run button, target the emulator.
5. Expect: the emulator boots the app and shows a blank Compose screen with the placeholder "Hello, `<app_name>`" text.

If any step fails, post the error in the team chat before proceeding.

---

# Part IV - Project scaffolding

Done once by the scaffolding owner. All other members pull afterwards.

## Create the project

1. Android Studio → New Project → **Empty Activity** (Compose, Kotlin).
2. Name: `<app_name>` (as typed by the user).
3. Package name: `com.<group_name>.uvadvisor` (lowercase, no hyphens).
4. Save location: outside any cloud-synced folder (avoid iCloud, OneDrive, Dropbox).
5. Language: Kotlin.
6. Minimum SDK: **API 26 (Android 8.0)**.
7. Build configuration language: **Kotlin DSL (build.gradle.kts)**.
8. Click Finish.

Verification: the project builds and runs on the emulator with the default "Hello Android" text.

## Gradle configuration

Edit `app/build.gradle.kts` to set:

```kotlin
android {
    namespace = "com.<group_name>.uvadvisor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.<group_name>.uvadvisor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}
```

## Version catalog

Create `gradle/libs.versions.toml` with the exact contents in Appendix D. This file pins every dependency version. Anyone adding a dependency edits this file, not the module `build.gradle.kts` directly.

## Package structure

Create these directories under `app/src/main/java/com/<group_name>/uvadvisor/`:

```
data/
├── openmeteo/          # Retrofit client + DTOs + repository
├── nominatim/          # Retrofit client + DTOs + repository
└── db/                 # Room entities, DAOs, database
domain/
├── model/              # Domain classes (UvReading, ContextClassification, SkinType, ...)
├── uvmodel/            # Fitzpatrick burn calculator + countdown
└── advisor/            # Rules that combine sensor + UV state
sensing/
├── light/              # TYPE_LIGHT listener + classifier
├── camera/             # CameraX luminance (optional)
└── posture/            # Accelerometer posture detector
platform/
├── location/           # FusedLocationProviderClient wrapper + permissions
└── notify/             # Notification channel + reapply scheduler
ui/
├── theme/              # Material 3 theme, colours, typography
├── main/               # Main screen
├── settings/           # Settings screen
├── about/              # About screen
└── common/             # Shared composables (UvChart, ColourBand)
viewmodel/
├── MainViewModel.kt
└── SettingsViewModel.kt
di/                     # (optional) Hilt or manual DI wiring
```

Verification: the project still builds after creating the empty directories.
Verification: `main` on GitHub contains the scaffolded project. The green build badge appears in the README.

---

# Part V - Architecture

## High-level architecture

The app uses MVVM with unidirectional data flow.

```
[ Sensors ]        [ Open-Meteo API ]        [ Nominatim API ]
    │                     │                          │
    ▼                     ▼                          ▼
[ sensing/* ]      [ data/openmeteo ]          [ data/nominatim ]
    │                     │                          │
    └──────►[ domain/* ]◄─┴──────────────────────────┘
                 │
                 ▼
           [ viewmodel/* ] ─── exposes StateFlow ───► [ ui/* ]
                                                        │
                                                    Compose recomposes
```

Rules:
- Data flows **up** from sensors and network to ViewModels, then **out** to UI.
- UI events flow **down** to ViewModels as sealed-class actions.
- No layer skips: UI never touches the Room DAO directly.
- All layers use Kotlin coroutines and `Flow`. Blocking calls are banned.
- View state is a single immutable `data class` per screen, held in `StateFlow`.

## Module breakdown

Owner slots are placeholders. Fill on day 1. LOC estimates from `analysis/03_sunsafe_deep_dive.md`.

| #   | Module path          | Owner | Approx LOC   | Purpose                                                                      |
| --- | -------------------- | ----- | ------------ | ---------------------------------------------------------------------------- |
| 1   | `data/openmeteo/`    |       | $\approx$ 120 | Retrofit client for UV forecast. Repository fetches, parses, writes to Room. |
| 2   | `data/nominatim/`    |       | $\approx$ 80  | Retrofit client for reverse geocoding. OkHttp interceptor adds User-Agent.   |
| 3   | `data/db/`           |       | $\approx$ 90  | Room `AppDatabase`, `UvEntity`, `UvDao`.                                     |
| 4   | `platform/location/` |       | $\approx$ 100 | `FusedLocationProviderClient`, permission flow, first-launch onboarding.     |
| 5   | `sensing/light/`     |       | $\approx$ 80  | `TYPE_LIGHT` listener, median filter, indoor/shade/sun classifier.           |
| 6   | `sensing/camera/`    |       | $\approx$ 140 | CameraX luminance cross-check. *Optional.*                                   |
| 7   | `sensing/posture/`   |       | $\approx$ 110 | Accelerometer z-axis + variance. States: face-up / in-pocket / in-hand.      |
| 8   | `domain/uvmodel/`    |       | $\approx$ 130 | Fitzpatrick enum + burn calculator + countdown coroutine.                    |
| 9   | `platform/notify/`   |       | $\approx$ 90  | Notification channel + reapply scheduler.                                    |
| 10  | `ui/`                |       | $\approx$ 500 | Compose screens: main, settings, about, UV chart.                            |
| 11  | `viewmodel/`         |       | $\approx$ 200 | State plumbing between sensors/repo and UI.                                  |

Cross-file rules:

- `AndroidManifest.xml` - one owner (usually the UI owner). Others send patches via PR comment.
- `gradle/libs.versions.toml` - one owner (usually the networking owner). Others request via PR comment.

## Interfaces up front

On day 1 of week 2, every module owner creates an `interface` file that defines the public API of their module. Downstream modules program against the interface, not the implementation. Fake implementations let everyone compile and test in parallel.

Example - `sensing/light/LightSensor.kt`:

```kotlin
interface LightSensor {
    val readings: StateFlow<LightReading>
    fun start()
    fun stop()
}

data class LightReading(
    val lux: Float,
    val context: LightContext,
    val timestamp: Instant,
)

enum class LightContext { INDOOR, SHADE, DIRECT_SUN, UNKNOWN }
```

A fake for tests and downstream development:

```kotlin
class FakeLightSensor : LightSensor {
    private val _readings = MutableStateFlow(LightReading(0f, LightContext.UNKNOWN, Instant.EPOCH))
    override val readings: StateFlow<LightReading> = _readings
    override fun start() {}
    override fun stop() {}
    fun emit(reading: LightReading) { _readings.value = reading }
}
```

Every module ships a fake alongside its interface.

---

# Part VI - Data and sensors

## Data model

**Room entity - `UvEntity`**

```kotlin
@Entity(
    tableName = "uv_hourly",
    primaryKeys = ["latE7", "lonE7", "timestampEpochHour"],
)
data class UvEntity(
    val latE7: Int,                  // latitude × 1e7 (integer for clean primary key)
    val lonE7: Int,                  // longitude × 1e7
    val timestampEpochHour: Long,    // hours since Unix epoch
    val uvIndex: Float,
    val uvIndexClearSky: Float,
    val cloudCoverPct: Int,
    val fetchedAtEpochSec: Long,
)
```

**DTOs - Open-Meteo forecast**

```kotlin
data class OpenMeteoUvResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val hourly: HourlyBlock,
    val daily: DailyBlock,
) {
    data class HourlyBlock(
        val time: List<String>,           // ISO 8601 local
        val uv_index: List<Float?>,
        val uv_index_clear_sky: List<Float?>,
        val cloud_cover: List<Int?>,
    )
    data class DailyBlock(
        val time: List<String>,
        val uv_index_max: List<Float?>,
    )
}
```

**Domain classes**

```kotlin
data class UvReading(
    val uvIndex: Float,
    val timestamp: Instant,
    val source: Source,
) {
    enum class Source { LIVE, CACHE }
}

enum class SkinType(val baseMinutesAtUv1: Int) {
    I(67), II(100), III(200), IV(300), V(400), VI(500),
}
```

## API contracts

**Open-Meteo UV forecast**

- Endpoint: `GET https://api.open-meteo.com/v1/forecast`
- Query: `latitude`, `longitude`, `hourly=uv_index,uv_index_clear_sky,cloud_cover`, `daily=uv_index_max`, `timezone=auto`
- Auth: none
- Rate limit: 10,000 requests/day (free tier)
- Refresh policy: once per hour per unique (lat, lon) key
- On failure: log the exception, keep serving cached rows, do not show a modal error

**Nominatim reverse-geocoding**

- Endpoint: `GET https://nominatim.openstreetmap.org/reverse`
- Query: `lat`, `lon`, `format=json`
- Headers: `User-Agent: <app_name>/0.1.0 (contact@example.com)` - **required**. Nominatim rejects requests without a real User-Agent.
- Rate limit: 1 request per second
- Refresh policy: cache result until the user moves more than 1 km
- On failure: show the raw coordinates instead of a place name

**Room caching policy**

- Table holds 168 rows per (lat, lon) - 7 days × 24 hours.
- On a fresh API response, delete rows for that (lat, lon) with `timestampEpochHour < now`, then insert the new rows.
- On network failure, read the newest row for the current hour; if empty, show "UV data unavailable".

## Sensor pattern

Every sensor module follows the same shape. Concrete example for the light sensor:

```kotlin
class AndroidLightSensor(context: Context) : LightSensor, SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val recent = ArrayDeque<Float>()
    private val _readings = MutableStateFlow(LightReading(0f, LightContext.UNKNOWN, Instant.now()))

    override val readings: StateFlow<LightReading> = _readings.asStateFlow()

    override fun start() {
        sensor ?: return
        sensorManager.registerListener(this, sensor, TimeUnit.SECONDS.toMicros(3).toInt())
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values[0]
        recent.addLast(lux)
        while (recent.size > 10) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]
        _readings.value = LightReading(median, classify(median), Instant.now())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun classify(lux: Float): LightContext = when {
        lux < 200f -> LightContext.INDOOR
        lux < 10_000f -> LightContext.SHADE
        else -> LightContext.DIRECT_SUN
    }
}
```

Rules for all sensor modules:

- `start()` is called from the ViewModel `init` or `onStart` of the hosting screen.
- `stop()` is called from the ViewModel `onCleared()` or `onStop`.
- Never register a listener without pairing it to an `unregister` call.
- All state is exposed through `StateFlow`. No callbacks leak out.

## Permissions

The app declares and requests these runtime permissions.

| Permission | When | Why |
|---|---|---|
| `ACCESS_FINE_LOCATION` | First launch | Fetch the UV forecast for the user's exact location |
| `CAMERA` | First launch (if camera module is included) | Read a preview frame every 10 s for luminance cross-check |
| `POST_NOTIFICATIONS` | First launch, Android 13+ | Send the reapply-sunscreen alert |

Manifest declaration (`AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.sensor.light" android:required="true" />
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="true" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

First-launch onboarding flow (three screens):

1. Welcome - explain what the app does in one sentence.
2. Location - explain why the app needs location, then request `ACCESS_FINE_LOCATION`.
3. Skin type - the user picks a Fitzpatrick type from I to VI, then lands on the main screen.

The camera and notification permissions are requested lazily: camera on the first background scan, notifications on the first "reapply" event.

---

# Part VII - UI

## Design system

**Colours (UV band)**

| UV range | Band | Hex |
|---|---|---|
| 0 – 2.9 | Low (green) | `#4CAF50` |
| 3 – 5.9 | Moderate (yellow) | `#FFC107` |
| 6 – 7.9 | High (orange) | `#FF9800` |
| 8 – 10.9 | Very high (red) | `#F44336` |
| 11+ | Extreme (purple) | `#7B1FA2` |

**Typography**

- Hero UV number: `displayLarge` (Material 3), 96 sp bold.
- Context label: `titleLarge`, 22 sp semi-bold.
- Countdown: `headlineMedium`, 28 sp regular.
- Body text: `bodyMedium`, 14 sp regular.

**Theme**

- Material 3 `MaterialTheme` with dynamic colour disabled (so the UV band shows consistently on every device).
- Light and dark mode via `isSystemInDarkTheme()`.
- Corner radius 12 dp on all cards.

## Screens and navigation

Three top-level destinations, wired with Navigation Compose.

| Route | Composable | Purpose |
|---|---|---|
| `main` | `MainScreen()` | Hero UV number, context label, countdown, forecast chart |
| `settings` | `SettingsScreen()` | Skin type, SPF, notification toggle |
| `about` | `AboutScreen()` | Data attribution, app version, GitHub link |

Navigation graph:

```kotlin
NavHost(navController = navController, startDestination = "main") {
    composable("main") { MainScreen(onOpenSettings = { navController.navigate("settings") }, onOpenAbout = { navController.navigate("about") }) }
    composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
}
```

## Content copy

All user-visible strings live in `app/src/main/res/values/strings.xml`. No hard-coded strings in Kotlin.

```xml
<resources>
    <string name="app_name"><![CDATA[<app_name>]]></string>

    <!-- Contexts -->
    <string name="context_indoor">Indoor</string>
    <string name="context_shade">In shade</string>
    <string name="context_direct_sun">Direct sun</string>

    <!-- Countdown -->
    <string name="safe_minutes_label">Safe minutes</string>
    <string name="countdown_paused">Paused (phone in pocket)</string>
    <string name="reapply_now">Reapply sunscreen now.</string>

    <!-- Permissions -->
    <string name="perm_location_rationale">We use your location to fetch the UV forecast for exactly where you are.</string>
    <string name="perm_camera_rationale">The camera confirms the light sensor reading. You can skip this and use only the light sensor.</string>
    <string name="perm_notification_rationale">Notifications remind you when to reapply sunscreen.</string>

    <!-- Attribution -->
    <string name="attrib_open_meteo">Weather data by Open-Meteo.com (CC-BY 4.0)</string>
    <string name="attrib_nominatim">Reverse geocoding by Nominatim / OpenStreetMap</string>
</resources>
```

---

# Part VIII - Quality

## Coding standards

**Kotlin**

- Ktlint enforces formatting. The config is in `config/.editorconfig` (Appendix D).
- 4-space indent. 120-char line limit.
- Trailing commas on multi-line lists.
- Prefer `val`, use `var` only where mutation is essential.
- No `!!` operator except in tests.
- Prefer `data class`, `sealed class`, and enums over open inheritance.

**Compose**

- One top-level `@Composable` per file for a screen (`MainScreen.kt`, `SettingsScreen.kt`).
- Stateless composables where possible. Hoist state to the caller.
- Pass callbacks (`onClick: () -> Unit`) rather than ViewModels down the tree.
- Use `remember { … }` for local state, `rememberSaveable { … }` for state that must survive process death.
- Preview every top-level composable with `@Preview`.

**Comments**

- Default: no comments. Names carry the meaning.
- Add a comment only when the *why* is non-obvious (a subtle invariant, a workaround, a hidden constraint).
- No task-referencing comments ("added for issue #123").
- No commented-out code. Delete it. Git remembers.

## Testing strategy

**Unit tests (JVM, fast)**

| Target | Test |
|---|---|
| `BurnTimeCalculator` | Every skin type × UV values `[1, 4, 8, 12]` × context factors `[1.0, 0.3, 0.0]` |
| `LightContextClassifier` | Thresholds at 199, 200, 9,999, 10,000, 10,001 lux |
| `PostureDetector` | Face-up, in-pocket (low variance + low light), in-hand (high variance) |
| Open-Meteo DTO parsing | Golden JSON fixture in `test/resources` |
| Nominatim DTO parsing | Golden JSON fixture in `test/resources` |

**Instrumented tests (device / emulator)**

| Target | Test |
|---|---|
| Room `UvDao` | Insert 168 rows, read latest, delete stale |
| Location permission flow | Grant + deny path, using `UiAutomator` |

**Manual test matrix (per sensor)**

| Sensor | Test | Expected |
|---|---|---|
| Light | Cover the sensor with a finger | Context → Indoor within 3 s |
| Light | Point at a bright window | Context → Direct sun within 3 s |
| Accelerometer | Lay phone face-up on table for 30 s | Posture → face-up |
| Accelerometer | Put phone in pocket for 30 s | Posture → in-pocket, countdown paused |
| Camera | Cover the lens | Luminance drops, matches Indoor |

## CI/CD

The GitHub Actions workflow (Section 10, Appendix D) runs on every PR:

- `build` - Gradle assembles the debug APK. Failure blocks the PR.
- `test` - All JVM unit tests. Failure blocks the PR.
- `ktlint` - Style check. Failure blocks the PR.

Merge to `main` after all three are green and one review is approved. `main` build also uploads the debug APK as an artifact for the team to download and install manually.

## Definition of Done

**A module is done when**:

1. Its `interface` and implementation are in `main`.
2. It ships a fake for testing.
3. Unit tests cover every public method with at least the happy path and one failure path.
4. Its owner has written a `README.md` in the module directory (1 paragraph, what and why).
5. Ktlint passes.
6. One other member has reviewed and approved a PR.

**A feature is done when**:

1. Every module it depends on is done.
2. It is wired into the UI.
3. It works end-to-end on the reference physical device.
4. It appears in the demo script for the video.

**The app is done when**:

1. Every feature marked "must" in Section 1 is done.
2. The final video (up to 10 minutes) is uploaded to YouTube.
3. The final report (up to 10 pages PDF) is written and reviewed.
4. The submission ZIP is built per Section 45.
5. Every member has rehearsed their viva answers.

---

# Part IX - Deliverables

## Milestone 1 report (10 pages)

Due: end of week 2. Written by the team, coordinated by the UI/coordination owner.

| Page(s) | Section | Owner |
|---|---|---|
| 1 | Cover: group number, member details, publicity opt-in statement |  |
| 2 | Itemised 1-page contribution breakdown (every member × concrete tasks) |  |
| 3 | Section: Material rubric coverage - how the video, report, screenshot, and commit log will meet the criteria |  |
| 4 | Section: Implementation rubric - Quality, Sensors, Connectivity |  |
| 5 | Section: Implementation rubric - Responsiveness, Technical depth |  |
| 6 | Section: User Interface rubric - Appeal, Guidelines, Flow |  |
| 7 | Section: User Interface rubric - Language, Reactiveness |  |
| 8 | Section: Innovation rubric - Novelty, Surprise, Tech Knowledge |  |
| 9 | Section: Innovation rubric - Cross-Disciplinary, Impact |  |
| 10 | Justification + feasibility summary + citations |  |

Content sources:

- Rubric mapping table: `analysis/OPTION_A.md` → "How this meets the rubric" section
- Module feasibility: `analysis/03_sunsafe_deep_dive.md` (rename SunSafe → `<app_name>`)
- Verified data sources: `analysis/01_data_sources_verified.md`

## Video plan (up to 10 minutes)

**Shot list**

| Shot | Duration | Content |
|---|---|---|
| 1 | 0:00–0:15 | Title card: app name, group number, one-line pitch |
| 2 | 0:15–0:45 | Voice-over: what the app does, why UV matters in Australia |
| 3 | 0:45–1:45 | Screen recording: open the app, first-launch onboarding, grant permissions, pick skin type |
| 4 | 1:45–3:00 | Live camera: walk from indoors to a doorway with the phone. The context label changes from Indoor to Direct sun. The countdown speeds up. |
| 5 | 3:00–4:00 | Live camera: put the phone in a pocket. Show the "paused" state. Take it out. Countdown resumes. |
| 6 | 4:00–5:30 | Screen recording: show the forecast chart, change SPF and skin type in Settings, watch the safe minutes recompute |
| 7 | 5:30–7:00 | Live camera: cover the rear camera lens, the "in shade" state fires. Uncover it, "direct sun" returns. |
| 8 | 7:00–8:00 | Screen recording: countdown hits zero, notification fires |
| 9 | 8:00–9:00 | Architecture explanation: brief diagram, "sensor fusion" summary, one slide on the Fitzpatrick model |
| 10 | 9:00–10:00 | Team credits, code repo link, data source attribution |

**Staging rules**

- Charge the demo phone to 100% before every shoot.
- Set the demo phone to Do Not Disturb (silence unrelated notifications).
- Use a fresh install for the "grant permissions" scene.
- Film on a partly-sunny day for the outdoor scenes. Backup: film indoor-to-doorway on any weather.

**Backup plans**

- Cloudy day: film the indoor-to-doorway transition; narrate the "direct sun" scenario over an earlier take.
- Camera module cut: skip shots 7 and 8. Replace with a longer look at posture detection.

## Milestone 2 report (up to 10 pages)

Due: week 11. Same structure as Milestone 1, updated with actual (not planned) outcomes.

- Every rubric criterion cross-references a section of the video (timestamped) and a code location (file:line).
- Include the final Android Studio build console screenshot.
- Include the YouTube link.
- Include compile-and-run instructions (one paragraph).

## Viva preparation

Each member must be able to answer, without notes, questions about **their own module**. See `analysis/03_sunsafe_deep_dive.md` Section 2 for the full list of sample questions per module.

**Preparation checklist per member**

1. Re-read your module code end-to-end. Understand every line.
2. Read the docs for every Android API you use. Bookmark them.
3. Write your own 3-question quiz for your module. Answer it out loud.
4. Pair with a teammate and quiz each other in week 4.
5. Prepare a 60-second summary of your module: what, why, key design choices.

**Extra prep for the two hard modules**

- **`sensing/camera/`** - read the CameraX ImageAnalysis docs. Understand YUV_420_888 and why the Y plane is enough for luminance. Understand `bindToLifecycle` and `unbindAll`.
- **`sensing/posture/`** - understand the physical meaning of accelerometer axes on Android. Read about `SensorManager` sensor delay levels. Explain the variance heuristic.

## Submission checklist

Due: week 11.

- [ ] Report PDF, up to 10 pages, all sections complete.
- [ ] YouTube video, up to 10 minutes, unlisted or public, link in the report cover page.
- [ ] Android Studio build console screenshot in the report.
- [ ] Code ZIP: the full project directory, `git clean -fdx` first, then zip.
- [ ] Commit log ZIP: `git log --all --pretty=fuller > commit_log.txt` then zip.
- [ ] Filename: `COMP90018 - Assignment - Group <N>.zip`.
- [ ] Every member's name, student number, email, and GitHub handle are on the cover page.
- [ ] Every member has attended the viva.

---

# Part X - Risk and backup

## Risks and mitigations

| # | Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| 1 | Camera lifecycle bugs waste 2+ days | Medium | Low | Assign camera module to the strongest developer. Hard deadline end of week 3: if not working, drop and ship without it. |  |
| 2 | Cloudy weather blocks outdoor filming | Medium (August in Melbourne) | Low-Medium | Shoot two videos on two different days. Also film an indoor-to-doorway transition that works in any weather. |  |
| 3 | One member cannot debug their own code at viva | Low-Medium | Medium (individual grade) | Pair-program the hardest modules in week 3. Every module has a primary owner and a secondary reviewer who has read the code. |  |

## Backup plan (cut list)

If at the end of week 3 the app is behind schedule, cut in this order:

1. **Drop the camera cross-check** (`sensing/camera/`). Save $\approx$ 140 LOC + camera permission complexity. The app still meets the rubric with 2 sensors + accel-derived posture.
2. **Simplify the forecast chart** to a single line (drop the "clear sky" reference line). Save $\approx$ 50 LOC.
3. **Simplify the posture detector** to face-up vs in-hand only (drop the pocket-detection variance analysis). Save $\approx$ 40 LOC. Countdown never auto-pauses.
4. **Drop the About screen**. Merge attribution into the Settings screen footer. Save $\approx$ 60 LOC.

Do not cut any further. Below this line the app stops meeting the rubric.

## Escalation

If a member is blocked for more than 4 hours:

1. Post in the team chat with `@channel` and the word "blocker".
2. Include: what you tried, exact error message, what you expect to happen.
3. Any other member joins a call within 30 minutes to pair-debug.
4. If not resolved in 24 hours, the group decides in the next meeting to reassign, refactor, or cut the feature.

---

# Appendix A - Glossary

| Term | Definition |
|---|---|
| UV index | Standard scale (0 to 11+) for solar UV strength at the ground, published by the WMO. |
| Fitzpatrick scale | Six categories of skin type (I to VI) by response to UV, published by dermatologist T.B. Fitzpatrick in 1975. |
| Lux | SI unit for illuminance. Roughly: office indoor 300, overcast day 1,000, direct sun 100,000. |
| YUV | Colour encoding: Y = luminance (brightness), U + V = chrominance (colour). The Y plane alone tells you brightness. |
| StateFlow | Kotlin coroutines primitive. A hot flow that always has a current value. Ideal for UI state. |
| Compose recomposition | The process of rebuilding a slice of the UI tree when its input state changes. Cheap when done right, expensive if done wrong. |
| CI | Continuous Integration. Automated build + test on every push. |
| CODEOWNERS | GitHub file that assigns default reviewers to files by path. |

# Appendix B - Reference links

- Android developer docs - https://developer.android.com/docs
- Jetpack Compose docs - https://developer.android.com/jetpack/compose
- Material 3 Compose - https://developer.android.com/jetpack/androidx/releases/compose-material3
- Room - https://developer.android.com/training/data-storage/room
- Kotlin coroutines - https://kotlinlang.org/docs/coroutines-overview.html
- Retrofit - https://square.github.io/retrofit/
- OkHttp - https://square.github.io/okhttp/
- CameraX - https://developer.android.com/training/camerax
- WorkManager - https://developer.android.com/topic/libraries/architecture/workmanager
- Open-Meteo - https://open-meteo.com/en/docs
- Nominatim - https://nominatim.org/release-docs/latest/
- Fitzpatrick scale reference - https://en.wikipedia.org/wiki/Fitzpatrick_scale

# Appendix C - Commit message convention

Conventional Commits: `<type>(<scope>): <subject>`

| Type | Use for |
|---|---|
| `feat` | A new user-facing feature |
| `fix` | A bug fix |
| `chore` | Tooling, scaffolding, dependency updates |
| `docs` | Documentation only |
| `test` | Add or modify tests only |
| `refactor` | Code change that does not add a feature or fix a bug |
| `style` | Formatting only (ktlint auto-fixes) |
| `ci` | GitHub Actions or CI config |

Scope is a module directory: `openmeteo`, `light`, `ui`, etc.

Examples:

- `feat(uvmodel): implement Fitzpatrick burn calculator`
- `fix(light): median filter dropped last sample on rotation`
- `chore(deps): bump kotlinx-coroutines to 1.9.0`
- `docs(readme): add build badge`
- `test(uvmodel): cover skin type VI at UV 12`

The subject:

- Uses the imperative mood ("add", not "added" or "adds").
- No trailing period.
- Under 60 characters.

Body (optional, separated by a blank line): explain the *why*. Wrap at 72 chars.

# Appendix D - Full configuration file contents

## D.1 `.gitignore`

```
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Android Studio / IntelliJ
.idea/
*.iml
.DS_Store
local.properties
captures/

# Kotlin
.kotlin/

# Android
*.apk
*.aab
*.ap_
*.dex
*.class
bin/
gen/
out/
release/
proguard/

# Signing
*.jks
*.keystore

# Log files
*.log

# macOS
.DS_Store

# Windows
Thumbs.db
Desktop.ini

# Node (in case of any tooling)
node_modules/

# Secrets
.env
```

## D.2 `CODEOWNERS`

Update the `@handle` placeholders with real GitHub usernames on day 1.

```
# CODEOWNERS - file at .github/CODEOWNERS

# Default: everyone reviews everything unless a more specific rule matches
*                              @member-1 @member-2

# Networking + data layer
app/src/main/java/**/data/openmeteo/**   @member-1
app/src/main/java/**/data/nominatim/**   @member-1
app/src/main/java/**/data/db/**          @member-1

# Location + notifications
app/src/main/java/**/platform/location/**   @member-2
app/src/main/java/**/platform/notify/**     @member-5

# Sensors
app/src/main/java/**/sensing/light/**     @member-3
app/src/main/java/**/sensing/posture/**   @member-3
app/src/main/java/**/sensing/camera/**    @member-4

# Domain model
app/src/main/java/**/domain/uvmodel/**   @member-5

# UI + ViewModels
app/src/main/java/**/ui/**          @member-6
app/src/main/java/**/viewmodel/**   @member-6
app/src/main/res/**                 @member-6

# Build files
gradle/libs.versions.toml           @member-1
app/build.gradle.kts                @member-1
build.gradle.kts                    @member-1

# CI
.github/**                          @member-1 @member-6
```

## D.3 `.github/pull_request_template.md`

```markdown
## What

<!-- One sentence: what this PR does -->

## Why

<!-- Link to the issue or explain the motivation -->

## How

<!-- Bullet the key design choices -->

## Checklist

- [ ] Code compiles (`./gradlew assembleDebug` passes)
- [ ] Unit tests pass (`./gradlew testDebugUnitTest`)
- [ ] Ktlint passes (`./gradlew ktlintCheck`)
- [ ] New public functions have doc comments (or names carry the meaning)
- [ ] No committed secrets
- [ ] Screenshots attached for UI changes

## Notes for reviewers

<!-- Anything the reviewer should know before starting -->
```

## D.4 `.github/ISSUE_TEMPLATE/bug.md`

```markdown
---
name: Bug report
about: Something is broken
labels: bug
---

## Steps to reproduce

1.
2.
3.

## Expected

<!-- What you expected -->

## Actual

<!-- What actually happened. Include logcat output if any. -->

## Environment

- Device / emulator:
- Android version:
- App version / commit:
```

## D.5 `.github/ISSUE_TEMPLATE/feature.md`

```markdown
---
name: Feature request
about: Propose a new feature
labels: enhancement
---

## Problem

<!-- What user need does this address? -->

## Proposal

<!-- One or two sentences describing the change -->

## Alternatives considered

<!-- Optional -->

## Rubric alignment

<!-- Which rubric criterion this improves, if any -->
```

## D.6 `.github/workflows/android-ci.yml`

```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      - name: Assemble debug
        run: ./gradlew assembleDebug
      - name: Upload APK
        if: github.ref == 'refs/heads/main'
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-${{ github.sha }}
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30

  test:
    name: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v3
      - name: Unit tests
        run: ./gradlew testDebugUnitTest
      - name: Upload test report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-report-${{ github.sha }}
          path: app/build/reports/tests/

  ktlint:
    name: ktlint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v3
      - name: Ktlint check
        run: ./gradlew ktlintCheck
```

## D.7 `.github/dependabot.yml`

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 5
    groups:
      minor-and-patch:
        update-types:
          - "minor"
          - "patch"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 3
    groups:
      minor-and-patch:
        update-types:
          - "minor"
          - "patch"
```

## D.8 `gradle/libs.versions.toml`

Pin every version here. Update via Dependabot PRs.

```toml
[versions]
agp = "8.5.2"
kotlin = "1.9.24"
ksp = "1.9.24-1.0.20"

# Compose
composeBom = "2024.09.00"
material3 = "1.3.0"
navigationCompose = "2.8.0"

# Lifecycle / ViewModel
lifecycle = "2.8.4"

# Networking
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.1"
retrofitKotlinxSerializationConverter = "1.0.0"

# Room
room = "2.6.1"

# Coroutines
coroutines = "1.9.0"

# Location
playServicesLocation = "21.3.0"

# CameraX
camerax = "1.3.4"

# WorkManager
work = "2.9.1"

# Charts
vico = "2.0.0-alpha.28"

# Testing
junit = "4.13.2"
mockk = "1.13.12"
turbine = "1.1.0"
androidxJunit = "1.2.1"
espresso = "3.6.1"

# Ktlint
ktlintGradle = "12.1.1"

[libraries]
# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Lifecycle
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.1" }

# Networking
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofitKotlinxSerializationConverter" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }

# Location
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }

# CameraX
camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# Charts
vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }

# Testing (unit)
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Testing (android)
androidx-test-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-test-espresso = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
androidx-compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintGradle" }
```

## D.9 `config/.editorconfig`

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{kt,kts}]
max_line_length = 120
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ktlint_standard_no-wildcard-imports = enabled
ktlint_standard_filename = enabled
ktlint_standard_import-ordering = enabled

[*.{yml,yaml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

## D.10 `README.md`

```markdown
# <app_name>

[![Android CI](https://github.com/<owner>/<app_name>-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/<owner>/<app_name>-android/actions/workflows/android-ci.yml)

Live UV index and personalised sun-protection advisor for Android.
COMP90018 Mobile Computing project - Group <N>.

## Build

`git clone git@github.com:<owner>/<app_name>-android.git`
`cd <app_name>-android`
`./gradlew assembleDebug`

Open in Android Studio Ladybug (2024.2.1) or newer. Run on an emulator (API 34) or a physical device.

## Data attribution

- Weather data by Open-Meteo.com (CC-BY 4.0)
- Reverse geocoding by Nominatim / OpenStreetMap contributors

## Licence

MIT - see `LICENSE`.
```

---

**End of PLAN.md**
