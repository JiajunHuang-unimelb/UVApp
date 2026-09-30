# 曝光历史：保存接口与接入说明

这个模块负责本地保存曝光记录，提供历史列表、每日和每周统计。计算模块负责计算剂量和有效时长、按午夜拆分日期，并调用保存接口；前端负责展示。

当前主流程尚未接入自动保存。创建 Repository 不会自动产生记录。

## 分工边界

| 存储模块 | 计算模块（队友接入时完成） |
| --- | --- |
| 基础字段检查、事务保存、更新和删除 | UV 到剂量的计算，SPF/环境修正 |
| 按已传入的日期做每日/每周汇总 | 排除暂停时间，生成各日有效时长和剂量 |
| 查询、分页、缺失日期补零 | 根据会话时区拆分午夜边界，处理夏令时 |
| 保持会话身份、防止较旧记录覆盖新记录 | 检查日期是否属于会话、有效时长是否超过实际经过时间、剂量与时长是否一致 |
| 数据库迁移和写入失败处理 | 生成完整累计记录，决定保存时机和顺序重试 |

存储层直接保存计算模块提供的每日结果，不重新计算日界线或判断曝光结果是否合理。计算侧规则及相应测试由队友在计算模块实现，当前存储模块不提供这些校验。

## 接入流程

1. 开始新会话时生成一次 `sessionId`，记住开始时间和时区。
2. 运行过程中定期保存，也可在暂停和结束时保存。
3. 每次传入这个会话截至当前的**完整每日累计记录**，不是本次新增值。
4. 暂停和继续使用同一个 ID。结束或重置前，先保存旧会话，再为新会话生成新 ID。
5. 通过一个保存入口顺序调用并等待 `save`，包括重试；不要为每份记录单独启动一个并行协程。

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
| `days[].activeDurationMillis` | 当日有效曝光时长，毫秒；排除暂停时间 |
| `days[].doseSed` | 当日累计剂量，SED；有限且非负 |

不需要版本号。每次保存都替换整份记录，省略的旧日期也会被移除，因此正常更新必须包含以前仍有效的每日统计。

跨午夜时由计算模块拆分。例如 23:50 到次日 00:10，需要两个日期的统计；不能把总剂量简单按时长比例拆分，因为两个区间的 UV 和环境可能不同。

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
        ExposureDayTotal(LocalDate.of(2026, 9, 22), 600_000L, 0.30),
        ExposureDayTotal(LocalDate.of(2026, 9, 23), 300_000L, 0.05),
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
- 时间戳基本范围和时区标识有效；日期不重复，每日时长非负，剂量有限且非负，合计不溢出。
- 每日日期分配、时长上限、零时长与剂量的关系由计算模块检查，存储层按传入结果保存。
- 会话和每日行在一个事务中替换；中途失败会回滚整次写入。
- 写入失败返回 `Result.failure`；协程取消继续抛出。读取失败不会伪装成零曝光。
- 历史按开始时间倒序排列；查询支持分页。每日查询范围为 `[start, endExclusive)`，最多 366 天，缺失日期补零。
- 每周为周一到周日；统计包含运行中、暂停和已完成记录。每日会话数只统计当天有有效曝光的会话，不能相加当作每周独立会话数。
- 历史使用会话开始时的时区；后续手机时区变化不重新划分旧数据。

`getSession` 返回最后一次保存的记录。它不负责补算未观察到的曝光，也不包含完整的计算器恢复状态；进程退出前尚未保存的部分可能丢失。

## 存储与验证

数据库为 `exposure_history.db`，版本 2，与可清空的 UV 缓存分开。版本 1 到 2 的迁移删除旧版本号字段，保留会话和每日记录；不使用破坏性迁移。旧版本的 schema 文件必须保留用于生成迁移及测试。

不存位置坐标或账号信息，不自动过期，不提供云同步。记录保留至用户删除、清除应用数据或卸载；当前备份规则排除数据库。

- `ExposureHistoryRepositoryTest`：重复保存、统计修正、基础字段和数值检查、身份/时间保护、传入每日结果原样保存、查询、删除、错误及取消。
- `ExposureHistoryDatabaseTest`：旧版本迁移保留数据、重启后持久化、较旧时间不能覆盖新记录、删除级联、事务回滚。

这些测试验证数据模块。计算模块保存入口、触发时机和历史 UI 仍需接入并验证。
