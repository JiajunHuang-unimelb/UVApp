# UVApp — Android UV forecasts and exposure tracking

UVApp uses MVVM and Jetpack Compose to display location-specific UV forecasts,
place search, a personal exposure countdown and local exposure history. The app
connects to Open-Meteo and Nominatim through Retrofit repositories, uses Room for
forecast/place caches and history, and persists settings and saved indoor places
with DataStore.

## Pages

| Page | Screen |
|---|---|
| Home — light & dark | `ui/screens/HomeScreen.kt` |
| Home — states (error banner, cached data, <15 min warning) | via dev card: *Force offline* + countdown |
| Forecast — day chips, drag-on-curve time picker, 24 h UV chart | `ui/screens/ForecastScreen.kt` |
| Settings — skin type, SPF, notifications, theme, dev mode | `ui/screens/SettingsScreen.kt` |
| Search dialog (scrim + Nominatim place search) | `ui/components/SearchDialog.kt` |
| Developer-mode card | `ui/components/DevCard.kt` |
| SunLog — daily and weekly exposure history | `ui/screens/SunLogScreen.kt` |

## Data interfaces and integration

`ui/UVAppRoot.kt` creates the production repositories and injects them into the
ViewModels. Repository contracts are in `domain/repository`, with implementations
under `data`.

| Interface | Current contract and implementation |
|---|---|
| `UvRepository` | `observeForecast(latitude, longitude): Flow<UvForecastState>` and suspend `refresh(latitude, longitude): Result<Unit>`; `UvRepositoryFactory` supplies Open-Meteo networking with Room caching |
| `PlaceRepository` | Submitted `searchPlaces(query)` and `reverseGeocode(coordinates)`; `PlaceRepositoryFactory` supplies Nominatim networking |
| `ExposureHistoryRepository` | Saves complete cumulative session snapshots and queries history, daily and weekly totals through Room |
| `UserPreferencesRepository` | Settings persisted by `DataStoreUserPreferencesRepository` |

Search is already connected to the dialog: an explicit submission requests
results, and selecting a result refreshes the forecast at its coordinates.
Forecast observation exposes cache/network state and refresh errors; network
failure can retain existing cached readings. Uncached locations still need a
successful fetch to obtain forecast data.

Exposure calculations and environment classification belong to the domain and
platform modules, rather than the UV repository. MainViewModel organizes and
saves daily history. See [place search](docs/place-search-api.md),
[history storage](docs/exposure-tracking-api.md) and
[sensor implementation](docs/multi-sensor-implementation.md) for their contracts
and limitations.

## Architecture

```
View (Compose) ──events──▶ ViewModel ──StateFlow──▶ immutable UiState ──▶ View
                               │
                               ▼
                    Repository interfaces
                               │
                    Retrofit APIs / Room / DataStore
```

- `viewmodel/MainViewModel.kt` — Home + shared chrome: live countdown ticker,
  refresh, search dialog, dev overrides.
- `viewmodel/ForecastViewModel.kt` — day/hour selection and forecast presentation
  from MainViewModel's shared forecast state.
- `viewmodel/SettingsViewModel.kt` — profile, theme and dev-mode settings backed
  by DataStore.
- `ui/theme/UvTheme.kt` — Solar palette + tokens, taken verbatim from the
  mockups; the main (accent) colour is user-selectable in
  Settings → Theme colour (6 presets, default Amber).
- `ui/icons/UvIcons.kt` — facade over the vector drawables in
  `app/src/main/res/drawable/ic_*.xml`, which can be previewed and edited in
  Android Studio.

## Build

Use JDK 21 and an Android SDK matching `app/build.gradle.kts` (currently compile
and target SDK 37). Configure the SDK location in untracked `local.properties`.

Database version 4 uses one `uvapp.db` for UV/place caches and exposure history.
The registered 3→4 migration adds direct-sun, shade and unknown duration columns
with SQL defaults of zero, without backfilling old duration breakdowns. Versions
1/2 and the former separate `exposure_history.db` have no migration/import path.
Migration compatibility and old-data preservation still need dedicated migration
tests; clearing app data is not migration validation. See the
[history storage guide](docs/exposure-tracking-api.md) before testing upgrades.

```powershell
.\gradlew.bat assembleDebug          # APK -> app/build/outputs/apk/debug/
.\gradlew.bat testDebugUnitTest      # Enabled JVM tests
.\gradlew.bat assembleDebugAndroidTest # Compile Android instrumentation tests
```

Install the APK on a device/emulator to run the app. Initial dependency resolution
may require network access; offline builds require the necessary dependencies
and SDK packages to already be installed. Android instrumentation tests require
a connected device/emulator and are separate from JVM tests. MainViewModelTest
is currently commented out; a successful JVM test task does not validate disabled
tests or Android database/migration scenarios.
