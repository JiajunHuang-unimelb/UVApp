# 当前位置与 UV 数据模块

- 模块：Foreground Location / GPS
- 分支：`feat/gps-integration`
- 状态：已接入正式 App 数据流

## 功能范围

该模块在用户点击定位按钮后申请前台位置权限，通过 Google Fused Location 获取一次当前位置，并将坐标交给已有的 Open-Meteo + Room Repository。模块不持续监听位置，也不申请后台定位权限。

```text
Home / Forecast / Search 的定位按钮
  → UVAppRoot 权限边界
  → MainViewModel
  → CurrentLocationProvider
  → FusedCurrentLocationProvider
  → LocationFix
  ├→ UvRepository.observeForecast + refresh → Room 缓存 / Open-Meteo
  └→ PlaceRepository.reverseGeocode → Room 缓存 / Nominatim
  → MainUiState
```

## 定位策略

- 同时支持 Approximate 与 Precise location。
- 首先使用 balanced-power 请求；没有结果时回退到 high-accuracy 请求。
- 最多接受五分钟内的缓存位置。
- 单次尝试最长六秒，整个定位流程最长十三秒。
- 新的定位请求会取消旧请求，避免旧位置覆盖新位置。
- 获取 UV 时使用已有的一小时缓存策略；手动 Refresh 会强制刷新当前坐标的数据。
- 定位成功后并行查询 Nominatim；成功时显示地点名称，失败时保留 `Current location` / `Current area`。

`LocationFix` 保存以下质量信息：

- 经纬度
- 水平精度（米）
- 采集时间
- 是否为 approximate permission
- 是否为模拟位置

## 权限与恢复

权限只在用户点击定位功能时申请，不在首次启动时自动弹窗。

- 未授权：请求 `ACCESS_COARSE_LOCATION` 与 `ACCESS_FINE_LOCATION`。
- 仅授权 approximate：功能继续工作，界面显示 `Current area`。
- 普通拒绝：提示重试或手动选地点。
- 永久拒绝：再次点击定位会打开 App Settings。
- 从 Settings 返回：重新读取权限；授权成功后只继续一次待处理的定位请求。
- 定位服务关闭、超时和不可用分别返回不同的领域状态。

## 隐私约束

- 正式 UI 不显示完整经纬度。
- 不使用后台位置权限，不持续跟踪用户。
- Room 数据库属于可重建缓存，已从云备份和设备迁移中排除。
- 坐标仅用于查询当前位置的 UV 预报；后续 About/Privacy 页面应说明第三方数据传输。
- 坐标也会发送给 Nominatim 以取得可读地点名称；UV 与地点缓存均不会进入系统备份。

## 自动化测试

JVM 测试覆盖：

- 精确位置成功并传入 UV Repository
- 定位成功后将坐标传入 Place Repository 并显示地点名称
- approximate location 成功
- 定位超时后保留原有 UV 数据并退出 Loading
- 新定位请求取消旧请求
- 非法经纬度和无效精度被拒绝

设备测试仍需手动验证：首次授权、拒绝、永久拒绝、系统定位关闭、从 Settings 返回、模拟器位置注入及真机定位。

## 已知边界

- Nominatim 查询失败时使用 `Current location` / `Current area`，不会影响 UV 查询。
- Forecast 页的日期卡片仍由现有前端 Repository 提供；当前位置 UV 数值和缓存来自真实数据层。
- Fused Location 依赖 Google Play services；不可用时会退化为 `Unavailable`，用户仍可手动选择地点。
