# UVApp — Front-end (MVVM + Jetpack Compose, mock data only)

Front-end-only build of the UVApp high-fidelity design (`high-fi/` mockups).
The UI is complete; **no backend code** — all data comes from `MockUvRepository`.

## Pages (identical to `high-fi/`)

| Page | Screen |
|---|---|
| Home — light & dark | `ui/screens/HomeScreen.kt` |
| Home — states (error banner, cached data, <15 min warning) | via dev card: *Force offline* + countdown |
| Forecast — day chips, drag-on-curve time picker, 24 h UV chart | `ui/screens/ForecastScreen.kt` |
| Settings — skin type, SPF, notifications, theme, dev mode | `ui/screens/SettingsScreen.kt` |
| Search dialog (scrim + live-filtered suburbs) | `ui/components/SearchDialog.kt` |
| Developer-mode card (8 override toggles) | `ui/components/DevCard.kt` |

## Connecting the backend (2 steps)

The UI depends on exactly one contract: `data/UvRepository.kt`.

1. **Implement `UvRepository`** with the real stack (Retrofit + Open-Meteo +
   Nominatim + Room). The mock's return shapes are the contract:

   | Method | Returns |
   |---|---|
   | `getCurrentUv()` | `UvReading(index)` — current UV (e.g. 8.4) |
   | `getPlaceName()` | `String` — e.g. "Southbank, Melbourne" |
   | `getForecastDays()` | `List<ForecastDay>` — 7 days; each has weekday, day-of-month, max UV, sunrise/sunset minutes, `hourly: List<HourlyUv>` (hour + uv or null for gaps) |
   | `getSensorContext()` | `SensorContext(lightContext, lux, stepsPerMinute)` |
   | `getBurnMinutes(skin, spf, uv, context)` | burn minutes (the front end also has the formula in `BurnCalculator` as a fallback) |
   | `getApiStatuses()` | `List<ApiStatus>` — shown in the developer card |

2. **Swap the provider** — change one line in `data/UvRepository.kt`:

   ```kotlin
   object UvRepositoryProvider {
       val instance: UvRepository by lazy { YourRealUvRepository() }
   }
   ```

   That's it. The ViewModels observe this instance; the UI updates
   automatically. The refresh button re-reads all of it.

### Data semantics the backend must honour

- UV bands (WHO): `<3 Low, <6 Moderate, <8 High, <11 Very High, ≥11 Extreme`
  (computed from the index — you only supply the number).
- Burn time: `baseMinutesAtUv1(skin) × SPF ÷ (uvIndex × contextFactor)`;
  indoor/zero dose ⇒ infinite (`Int.MAX_VALUE`) ⇒ timer shows `--:--`.
- Forecast hourly: hours without data must be `null` (chart draws a gap).

## Architecture

```
View (Compose) ──events──▶ ViewModel ──StateFlow──▶ immutable UiState ──▶ View
                               │
                               ▼
                    UvRepository (interface)
                               │
                    MockUvRepository (fake)
```

- `viewmodel/MainViewModel.kt` — Home + shared chrome: live countdown ticker,
  refresh, search dialog, dev overrides.
- `viewmodel/ForecastViewModel.kt` — day/hour selection, forecast refresh.
- `viewmodel/SettingsViewModel.kt` — profile, theme, dev-mode (in-memory mock).
- `ui/theme/UvTheme.kt` — Solar palette + tokens, taken verbatim from the
  mockups; the main (accent) colour is user-selectable in
  Settings → Theme colour (6 presets, default Amber).
- `ui/icons/UvIcons.kt` — facade over the vector drawables in
  `app/src/main/res/drawable/ic_*.xml`. A visual gallery of all nine icons
  (light + dark tiles, on-device tints) lives at `docs/icons/index.html`,
  with standalone `ic_*.svg` previews beside it — regenerate both after
  editing an icon via `node docs/icons/gen_icons.js` (the XML drawables are
  the single source of truth the app loads).

## Build

```powershell
.\gradlew.bat assembleDebug          # APK -> app/build/outputs/apk/debug/
.\gradlew.bat testDebugUnitTest      # JVM tests (bands, burn math)
```

Offline-safe: all dependencies are pinned to versions already resolved in the
local Gradle cache. Install the APK on a device/emulator to run the app.
