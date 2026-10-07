# 曝光历史：保存接口与接入说明

这个模块负责本地保存曝光记录，提供历史列表、每日和每周统计。计算模块提供剂量和各状态时长，MainViewModel 按日期组织累计记录并调用保存接口；前端负责展示。

当前 MainViewModel 已接入保存：会话建立、状态变化及运行中约每分钟保存完整累计记录，SunLog 页面读取历史统计。创建 Repository 本身不会自动产生记录。

## 分工边界

| 存储模块 | 计算模块及 MainViewModel |
| --- | --- |
| 基础字段检查、事务保存、更新和删除 | UV 到剂量的计算及环境修正 |
| 按已传入的日期做每日/每周汇总 | 排除暂停时间，生成各日有效时长和剂量 |
| 查询、分页、缺失日期补零 | 根据会话时区拆分午夜边界，处理夏令时 |
| 保持会话身份、防止较旧记录覆盖新记录 | 检查日期是否属于会话、有效时长是否超过实际经过时间、剂量与时长是否一致 |
| 数据库迁移和写入失败处理 | 生成完整累计记录，决定保存时机和顺序重试 |

存储层直接保存调用方提供的每日结果，不重新计算日界线或判断曝光结果是否合理。日期分配、时长与剂量的一致性属于调用方责任，不能将这些规则视为存储层已经提供的校验。

## 接入流程

1. 开始新会话时生成一次 `sessionId`，记住开始时间和时区。
2. 运行过程中定期保存，也可在暂停和结束时保存。
3. 每次传入这个会话截至当前的**完整每日累计记录**，不是本次新增值。
4. 暂停和继续使用同一个 ID。重置时，选择保存会以 `COMPLETED` 保存旧会话；选择不保存会删除该会话已写入的历史。下一会话使用新 ID。
5. 写入需要串行执行。当前 MainViewModel 使用 `historySaveMutex` 串行保存和删除；单次保存失败后，后续检查点仍携带完整累计记录，当前没有显式自动重试循环。

数据层按 ID 整体替换。同一会话重复保存不会增加记录数，也不会重复累加统计。相同保存时间允许修改状态、修正剂量/时长、删除错误的每日行；已完成记录也允许修正。

失败重试应在同一个保存流程中处理：先重试当前记录，或用更新的完整记录替代它。更新记录保存成功后，不要再重试旧记录。时间较早的写入会失败；相同时间的写入按数据库执行顺序覆盖，因此仍需要顺序调用。

## 需要传入的字段

| 字段 | 含义 |
| --- | --- |
| `sessionId` | 会话唯一 ID；同一会话及重试保持不变，新会话换新 ID |
| `startedAtMillis` | UTC 毫秒时间戳，会话开始后固定 |
| `recordedThroughMillis` | 本记录累计到的 UTC 毫秒时间戳，不能早于已保存的时间 |
| `zoneId` | 开始时的时区，例如 `Australia/Melbourne`，同一会话固定 |
| `status` | `ACTIVE`、`PAUSED`、`COMPLETED` |
| `days` | 会话内各日期的完整累计数据；没有活动的日期可以省略 |
| `days[].date` | 本会话时区中的本地日期，不能重复 |
| `days[].activeDurationMillis` | 当前调用方写入的当日非室内累计时长，即下列三种状态时长之和；毫秒，排除暂停时间，不是剂量加权时长 |
| `days[].directSunDurationMillis` | 当日直射光状态累计时长，毫秒 |
| `days[].shadeDurationMillis` | 当日阴影状态累计时长，毫秒 |
| `days[].unknownDurationMillis` | 当日未知环境状态累计时长，毫秒 |
| `days[].doseSed` | 当日累计剂量，SED；有限且非负 |

`ExposureDayTotal` 的六个参数都必须传入，三个新增时长字段没有构造默认值。时长来自计算器的 elapsed 时钟；开发模式的加速时钟不能直接当作真实墙钟时长。剂量由计算模块另行累计，存储层不根据这些时长重算剂量。

保存接口不需要快照版本号；数据库版本由 Room 管理。每次保存都替换整份记录，省略的旧日期也会被移除，因此正常更新必须包含以前仍有效的每日统计。

跨午夜时由 MainViewModel 按会话时区拆分。例如 23:50 到次日 00:10，需要两个日期的统计。当前实现按两次检查点之间的墙钟时间比例分配剂量及各状态时长增量，并对时长取整；这是区间分配近似，不会重新计算午夜两侧各自的 UV 或环境。

## 调用示例

```kotlin
val history = ExposureHistoryRepositoryFactory.create(applicationContext)
val zone = ZoneId.of("Australia/Melbourne")
val start = LocalDate.of(2026, 9, 22).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
val through = LocalDate.of(2026, 9, 23).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
val record = ExposureRecord(
    sessionId = savedSessionId, // 开始时生成一次
    startedAtMillis = start,
    recordedThroughMillis = through,
    zoneId = zone.id,
    status = ExposureRecordStatus.PAUSED,
    days = listOf(
        ExposureDayTotal(
            date = LocalDate.of(2026, 9, 22),
            activeDurationMillis = 600_000L,
            directSunDurationMillis = 600_000L,
            shadeDurationMillis = 0L,
            unknownDurationMillis = 0L,
            doseSed = 0.30,
        ),
        ExposureDayTotal(
            date = LocalDate.of(2026, 9, 23),
            activeDurationMillis = 300_000L,
            directSunDurationMillis = 0L,
            shadeDurationMillis = 300_000L,
            unknownDurationMillis = 0L,
            doseSed = 0.05,
        ),
    ),
)
// 在同一个顺序保存流程中调用，检查结果后再处理下一份记录。
val result = history.save(record)
if (result.isFailure) {
    // 展示或记录错误；在此流程中决定重试或用更新的完整记录替代。
}

val records = history.observeHistory(limit = 50, offset = 0)
val today = LocalDate.now(zone)
val daily = history.observeDaily(today, today.plusDays(1))
val week = history.observeWeek(today)
val checkpoint = history.getSession(savedSessionId)
```

模型位于 `domain.model`，Factory 位于 `data.history`。日期使用 `java.time`。挂起方法应从协程调用；UI 收集查询返回的 Flow。用户确认删除后调用 `deleteSession` 或 `clearHistory`，并先停止相关待处理保存，避免删除后又写回来。

## 存储层保留的检查和查询规则

- 会话 ID 有效，开始时间/时区固定，保存时间不倒退。
- 时间戳基本范围和时区标识有效；日期不重复，每日四个时长字段非负，剂量有限且非负；保存时检查总 active 时长不溢出、总剂量有限。
- 存储层不校验 active 时长是否等于三个状态时长之和，也不校验每日日期分配、时长上限或零时长与剂量的关系；这些属于调用方责任。
- 会话和每日行在一个事务中替换；中途失败会回滚整次写入。
- 写入失败返回 `Result.failure`；协程取消继续抛出。读取失败不会伪装成零曝光。
- 历史按开始时间倒序排列；查询支持分页。每日查询范围为 `[start, endExclusive)`，最多 366 天，缺失日期补零。
- 每周为周一到周日；统计包含运行中、暂停和已完成记录。每日会话数只统计当天有有效曝光的会话，不能相加当作每周独立会话数。
- 每日和每周查询分别汇总四个时长字段及剂量；无记录日期的各字段补零。会话数仍以 `activeDurationMillis > 0` 判断。
- 历史使用会话开始时的时区；后续手机时区变化不重新划分旧数据。

`getSession` 返回最后一次保存的记录。它不负责补算未观察到的曝光，也不包含完整的计算器恢复状态；进程退出前尚未保存的部分可能丢失。

## 存储与验证

数据库统一为 `uvapp.db`，版本 4，包含 `uv_readings`、`place_names`、`exposure_sessions` 和 `exposure_days`。缓存更新与清空曝光历史仅操作各自的表，不使用整库删除重建。以后结构变更必须提供显式迁移。

当前已注册 `MIGRATION_3_4`：为 `exposure_days` 增加 `directSunDurationMillis`、`shadeDurationMillis`、`unknownDurationMillis` 三个非空整数列，SQL 默认值均为 0；迁移 SQL 不改写原有 active 时长和剂量。旧记录的 active 时长原先仅累计直射光，新记录则累计三种非室内状态。迁移没有回填旧记录的状态明细，因此旧记录可能出现 active 非零而三个新增字段均为零，不能按新记录的等式反推旧数据。

代码未提供版本 1/2 到版本 4 的迁移，也不复制旧 `exposure_history.db` 中的记录。3→4 的 Room schema 兼容性及旧数据保留仍需迁移测试确认；清除应用数据不能替代迁移验证。清除数据或卸载会删除曝光记录、缓存、设置及保存的室内地点。DataStore 仍保存设置和室内地点；历史 schema 文件仅作结构参考，不能代替迁移测试结果。

曝光历史表不存位置坐标或账号信息，不自动过期，不提供云同步。曝光记录保留至用户删除、清除应用数据或卸载；当前备份规则排除数据库。

- `ExposureHistoryDatabaseTest`（Android 模拟器/设备）的既有测试覆盖目标包括四张表创建、缓存与历史隔离、重启后持久化、重复保存与修正、基础输入/时间保护、每日/每周汇总、删除级联及事务回滚。目前仍有版本 3 断言和旧三参数构造调用，需适配 v4 字段并补充 3→4 迁移测试后再运行。

`MainViewModelTest` 当前被整体注释，不能视为保存入口已通过测试。主流程和历史 UI 已接入，但 v4 的字段汇总、保存/丢弃行为与迁移仍需完成回归验证；本说明不代表这些测试已通过。
