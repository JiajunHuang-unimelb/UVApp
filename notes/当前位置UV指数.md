# 当前位置与 UV 数据模块

- 模块：Foreground Location / GPS
- 初始开发分支：`feat/gps-integration`（当前实现以 `main` 为准）
- 状态：已接入正式 App 数据流

## 功能范围

当前位置功能通过 Google Fused Location 获取一次前台位置，并将坐标用于 Open-Meteo UV 预报和 Nominatim 反向地理编码。`UVAppRoot` 在首次进入 App、尚无 `locationFix` 时会主动调用定位权限入口；用户也可在 Home、Forecast 和 Search 中手动请求当前位置。

需要区分两条位置数据流：

- **当前位置查询**：`FusedCurrentLocationProvider` 执行一次性定位，不持续订阅位置更新。
- **曝光环境监测**：曝光会话运行期间，`ExposureMonitoringService` 可通过前台服务持续或自适应获取位置更新，用于判断是否接近用户保存的室内地点。这不是 `FusedCurrentLocationProvider` 的一次性定位流程。

App 声明了前台位置和前台服务权限，但没有声明 `ACCESS_BACKGROUND_LOCATION`。

```text
App 启动时无 locationFix，或在 Home / Forecast / Search 点击定位
  → UVAppRoot 的定位权限入口
  → MainViewModel.onUseCurrentLocation()
  → CurrentLocationProvider
  → FusedCurrentLocationProvider
  → LocationFix
  ├→ UvRepository.observeForecast() / refresh()
  │    → Room 缓存 / Open-Meteo
  └→ PlaceRepository.reverseGeocode()
       → Room 缓存 / Nominatim
  → MainUiState → Home / Forecast

用户在 Search 中选择搜索结果
  → MainViewModel.onPlaceSelected()
  → 使用所选地点坐标查询预报（不视为实际 GPS 位置）
```

## 定位策略

- 支持 Approximate 与 Precise location；仅获得粗略位置权限时，当前位置查询仍可继续。
- 普通当前位置查询优先尝试 Google Fused Location 的 `lastLocation`；如果没有五分钟内的可用缓存，则直接使用 `PRIORITY_HIGH_ACCURACY` 请求位置，不再采用 balanced-power 后切换 high-accuracy 的两阶段流程。
- `freshOnly = true` 的调用跳过 `lastLocation`，请求新的高精度位置，主要用于需要新位置证据的室内地点保存流程。
- `CurrentLocationRequest` 的持续时间设为 **6 秒**，`getCurrentLocation()` 的整体获取流程设置 **7 秒**的协程超时；不能再描述为 13 秒。
- `MainViewModel` 会取消先前的定位和关联预报、地名查询任务，避免旧请求覆盖新的用户选择。
- `DefaultUvRepository` 会复用 **1 公里内**已有位置的预报缓存；如果缓存获取时间仍在 **1 小时**内，点击 Refresh 也不会重新请求 Open-Meteo。没有可用缓存或缓存过期时才尝试网络刷新。
- 定位成功后启动 UV 预报查询，并异步执行 Nominatim 反向地理编码。地名查询失败不阻止 UV 查询；当前 `MainViewModel` 会保留 `coordinateLabel()` 生成的经纬度字符串作为地名回退值。

`LocationFix` 保存以下信息：

- `latitude`、`longitude`：经纬度。
- `accuracyMeters`：水平定位精度，单位米。
- `capturedAtMillis`：位置采集时间，Unix 毫秒时间戳。
- `isApproximate`：是否只有粗略定位权限，或是否被作为非真实当前位置处理。
- `isMock`：Android 是否将该位置标记为模拟位置。

## 权限与恢复

定位权限不只在用户点击按钮时请求。`UVAppRoot` 在首次进入 App 且当前 `locationFix == null` 时会主动调用 `requestCurrentLocation()`，因此首次启动可能弹出系统定位权限窗口。

- **未授权**：通过 `RequestMultiplePermissions` 请求 `ACCESS_COARSE_LOCATION` 与 `ACCESS_FINE_LOCATION`。
- **仅授权 Approximate**：可继续获得位置与 UV 数据，`LocationFix.isApproximate` 为 `true`。
- **普通拒绝**：向界面返回拒绝信息，用户可以再次点击定位或手动搜索地点。
- **永久拒绝**：再次点击定位时打开应用的系统设置页。
- **从系统设置返回**：在 `ON_RESUME` 时重新检查权限；如果此前正在等待授权，获得权限后继续一次待处理定位。
- **设备定位关闭、超时、位置不可用**：通过 `LocationDisabled`、`Timeout`、`Unavailable` 等结果分别处理。

在 Search 中手动选择地点不等于获得设备当前位置：`MainViewModel.onPlaceSelected()` 使用搜索结果的坐标，并将创建的 `LocationFix` 标记为 `isApproximate = true`，避免把搜索地点当作用户实际所在位置参与室内判断。

## 隐私与数据保存

- `AndroidManifest.xml` 没有声明 `ACCESS_BACKGROUND_LOCATION`；不过曝光监测服务可能在 App 界面不可见时作为前台服务继续进行位置监测，因此不能把整个 App 描述为“绝不持续获取位置”。
- 当前位置坐标会被发送给 Open-Meteo 用于 UV 预报，并可能发送给 Nominatim 用于地名查询；服务的数据来源和隐私说明应如实告知用户。
- `uvapp.db` 同时保存 UV / 地名缓存以及曝光历史。**曝光历史不是可随意清除的临时缓存**。
- 当前 `backup_rules.xml` 和 `data_extraction_rules.xml` 排除了整个数据库的云备份，并在设备迁移规则中排除了数据库。数据库清除、卸载和升级迁移对历史数据的影响参见 `docs/exposure-tracking-api.md`。
- 当前 `MainViewModel.coordinateLabel()` 在地名查询成功前或失败时使用经纬度文本，因此不能再写“正式 UI 一定不显示完整经纬度”。如果产品要求隐藏坐标，应另外修改相应的回退展示逻辑。

## 自动化测试与设备验证

当前已启用的 JVM 测试中，与位置相关的主要包括：

- `LocationFixTest`：验证经纬度、精度和时间戳的有效范围及边界。
- `LocationFixValidatorTest`：验证曝光监测所用位置证据的精度、时效性与异常时间戳处理。

`MainViewModelTest` 当前整类被注释，不能将其原有测试场景当作已运行并通过的结果。例如，精确/粗略位置接入、定位超时后保留 UI 数据、新请求取消旧请求等端到端行为，仍需恢复相关测试并重新验证。

以下行为还应在 Android 模拟器或真机上验证：首次权限请求、只授权 Approximate、拒绝与永久拒绝、从系统设置返回、关闭设备定位服务、首次无缓存定位、五分钟内缓存复用、搜索地点切换，以及曝光会话期间的后台位置监测。此处列的是验证清单，不代表这些场景均已通过。

## 已知边界

- Nominatim 失败不会直接导致 UV 预报查询失败；当前地名回退可能显示经纬度字符串。
- Home 与 Forecast 使用共享预报状态；实时获取的预报及 Room 缓存来自真实数据层，`ForecastViewModel` 负责日期和时间选择。
- `FusedCurrentLocationProvider` 依赖 Google Play services；如果定位服务不可用，当前位置查询可能返回 `Unavailable`，用户仍可通过 Search 手动选择地点。
- 手动 Refresh 不保证每次发生网络请求，因为一小时内的缓存会被优先复用。
- 持续位置监测属于 `ExposureMonitoringService` 的曝光功能，不应与当前位置按钮触发的一次性定位混为一谈。
