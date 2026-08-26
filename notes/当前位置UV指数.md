# 当前位置 UV 指数开发记录

- 负责人：Zack
- 日期：2026-08-26
- 状态：已完成

---

## 1. 需求说明

获取真机或 Android 虚拟设备的当前位置，将经纬度传给 Open-Meteo Forecast API，并在首页显示当前位置的 UV 指数和墨尔本时区的观测时间。

---

## 2. 技术方案

### 2.1 方案概述

- Compose 页面负责运行时定位权限请求和状态展示。
- `FusedLocationProviderClient` 负责一次性获取当前坐标。
- `MainViewModel` 先获取位置，再调用现有 `CurrentUvRepository`。
- `OpenMeteoCurrentUvRepository` 请求 Open-Meteo，并将 DTO 转换为 `UvReading`。
- 请求固定传入 `timezone=Australia/Melbourne`，让 API 直接返回墨尔本本地时间。

没有引入 Service、Use Case、DI 框架、后台定位、缓存或持续位置监听。

### 2.2 架构设计

```text
MainScreen
  ├─ 请求 ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION
  └─ 展示 MainUiState
          ↑
MainViewModel
  ├─ CurrentLocationProvider
  │      └─ FusedCurrentLocationProvider
  └─ CurrentUvRepository
         └─ OpenMeteoCurrentUvRepository
                └─ OpenMeteoApi
```

数据流：

```text
设备或模拟器位置
  → Fused Location
  → 经纬度
  → MainViewModel
  → Open-Meteo Repository
  → UvReading
  → MainUiState
  → Compose UI
```

### 2.3 涉及的技术栈/库

- 语言/框架：Kotlin、Android、Jetpack Compose、MVVM、Coroutines、StateFlow
- 关键依赖：Google Play Services Location 21.4.0、Retrofit 3.0.0、Kotlinx Serialization

### 2.4 数据结构 / 接口设计

**数据库变更：**

无数据库变更。

**关键数据结构：**

```kotlin
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

data class UvReading(
    val index: Double,
    val observedAt: String,
)
```

**API 设计：**

| 方法 | 路径 | 说明 | 请求参数 | 返回值 |
|------|------|------|----------|--------|
| GET | `https://api.open-meteo.com/v1/forecast` | 获取当前位置的当前 UV 指数 | `latitude`、`longitude`、`current=uv_index`、`timezone=Australia/Melbourne` | `current.uv_index`、`current.time` |

---

## 3. 开发过程 / 踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 初版只能显示 Melbourne CBD 的 UV | ViewModel 使用写死的 Melbourne 经纬度，项目没有设备定位权限或定位实现 | 加入定位权限、`CurrentLocationProvider` 和 Fused Location 实现，删除固定坐标 |
| 模拟器网络和数据已开启，但返回 `Current location is unavailable` | `BALANCED_POWER_ACCURACY` 在该模拟器上偏向没有位置的 network provider，结果返回 `null` | 一次性定位请求改为 `PRIORITY_HIGH_ACCURACY`，使用模拟器 GPS fix |
| 观测时间比墨尔本时间早 10 小时 | Open-Meteo 未指定 `timezone` 时默认返回 GMT 时间 | 请求增加 `timezone=Australia/Melbourne` |
| Compose lint 报告 `NonObservableLocale` | Composable 中直接调用 `Locale.getDefault()` 不会响应 locale 状态变化 | 坐标固定使用 `Locale.US` 格式化小数点 |

**关键代码片段：**

```kotlin
val location = locationProvider.getCurrentLocation()

val reading = repository.getCurrentUv(
    latitude = location.latitude,
    longitude = location.longitude,
)
```

Open-Meteo 请求参数：

```kotlin
api.getCurrentUv(
    latitude = latitude,
    longitude = longitude,
    current = "uv_index",
    timezone = "Australia/Melbourne",
)
```

---

## 4. 测试记录

### 4.1 测试用例

| 用例编号 | 测试场景 | 输入 | 预期输出 | 实际结果 | 状态 |
|----------|----------|------|----------|----------|------|
| TC01 | Open-Meteo JSON 解析 | UV fixture JSON | 转换为正确的 `UvReading` | UV 和观测时间映射正确 | ✅ |
| TC02 | Repository 请求参数 | Melbourne 经纬度 | 传递坐标、`uv_index` 和 Melbourne 时区 | 参数断言通过 | ✅ |
| TC03 | ViewModel 使用设备坐标 | Fake Sydney 坐标 | 用当前位置请求 UV 并更新 UI state | 坐标、UV、时间断言通过 | ✅ |
| TC04 | 模拟器端到端流程 | 模拟 GPS 坐标、粗略定位权限、网络连接 | 页面显示坐标、UV 和 Melbourne 时间 | 显示坐标 `-37.8919, 144.7522`、UV `0.25`、时间 `2026-08-26T17:30` | ✅ |
| TC05 | 项目构建和静态检查 | Debug variant | 测试、lint、APK 构建成功 | `testDebugUnitTest`、`lintDebug`、`assembleDebug` 通过 | ✅ |

### 4.2 Bug 记录

| Bug编号 | 描述 | 严重程度 | 状态 | 修复方式 |
|---------|------|----------|------|----------|
| BUG-01 | 模拟器已有 GPS 坐标但 App 获取位置为 `null` | 高 | 已修复 | 定位优先级改为 `PRIORITY_HIGH_ACCURACY` |
| BUG-02 | API 观测时间显示为 GMT | 中 | 已修复 | Open-Meteo 请求增加 `timezone=Australia/Melbourne` |

---

## 5. 备注

- 当前只显示经纬度，没有 suburb/city 反向地理编码。
- App 每次进入页面或点击 Refresh 时获取一次位置，不进行持续后台定位。
- 用户只授权 approximate location 时，Android 会对坐标做模糊处理；需要更准确坐标时应授权 Precise location。
