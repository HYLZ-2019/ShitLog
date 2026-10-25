# 备份格式 v1

UTF-8 JSON，不是 SQLite 数据库文件。导出在 Room 事务中读取一致快照，包含所有记录（包括取消、软删除、进行中）、详情、类型、原始来源和导入批次。设备单调时钟基准不导出，界面偏好不属于业务备份。

顶层字段：

| 字段 | 类型／含义 |
|---|---|
| `format_version` | 整数，当前 1 |
| `app_version` | 导出应用版本 |
| `exported_at` | ISO 8601 UTC 时间 |
| `record_types` | 模块定义数组，当前支持 `bowel` v1 |
| `records` | 公共记录数组 |
| `bowel_details` | 排便详情数组，与记录一对一 |
| `record_sources` | 记录来源数组，保留 `raw_json` 与映射版本 |
| `import_batches` | 导入批次数组 |

公共记录使用 snake_case。时间戳为 UTC 毫秒；时长单位毫秒；偏移单位秒。`event_local_date` 为发生时的当地日期，不随当前手机时区改变。UUID 使用小写标准格式。所有可空字段都显式输出 null。

五个布尔字段：`abdominal_pain`、`constipation`、`anal_pain`、`blood_excluding_menstruation`、`sticky_stool`。值严格为 true、false 或 null，不接受字符串 `"false"`。

性状为 `bristol_1` 至 `bristol_7` 或 null；量为 `very_small`、`small`、`medium`、`large` 或 null。

`status` 为 `in_progress`、`completed`、`cancelled`。`details_status` 为 `pending`、`completed`。`duration_source` 为 `monotonic`、`wall_clock`、`manual`、`imported` 或 null；有时长时必须同时有来源。日期级历史数据允许没有精确起止时间与时区，不伪造。

## 恢复语义

1. 文件限 20 MB，验证格式版本、字段类型、枚举、唯一 ID、日期和外键引用；不支持的模块／版本拒绝导入。
2. 预览新增、重复、冲突、进行中跳过数量。
3. 同 ID 相同内容跳过；同 ID 不同内容保留本地。删除标记也属于内容差异，不自动删除本地现有记录。
4. 进行中记录在确认中明确跳过，用户可以自行补记，不跨设备恢复设备计时基准。
5. 事务合并；失败回滚；记录和来源不重复。文件 SHA-256 用于识别相同恢复批次。
6. 导入批次本身若出现相同 ID 不同内容，拒绝整批恢复。恢复确认时重新计算记录冲突，避免预览后本地变化导致覆盖。

示例见 `sample-backup-v1.json`，只含虚构数据。

## CSV

UTF-8 BOM、CRLF 行结束、每格加双引号，内部引号转义为两个引号，保留中文和换行。导出未删除的已完成排便记录，缺失留空。为避免电子表格将备注解释成公式，对潜在公式前缀添加单引号，因此 CSV 不用于无损恢复。
