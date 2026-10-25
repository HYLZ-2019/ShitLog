package com.lunhui.log.core.backup

import com.lunhui.log.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class BackupSnapshot(val types: List<RecordType>, val entries: List<BowelEntry>,
    val sources: List<RecordSource>, val batches: List<ImportBatch>)

object BackupCodec {
    const val VERSION = 1
    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
    }
    private fun arr(items: List<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }
    private fun LogRecord.json() = obj("id" to id, "type_id" to typeId, "status" to status, "details_status" to detailsStatus,
        "started_at_utc_ms" to startedAt, "ended_at_utc_ms" to endedAt, "event_local_date" to eventLocalDate,
        "zone_id" to zoneId, "start_offset_seconds" to startOffset, "end_offset_seconds" to endOffset,
        "time_precision" to timePrecision, "duration_ms" to durationMs, "duration_source" to durationSource,
        "time_review_required" to timeReviewRequired, "note" to note, "created_at_utc_ms" to createdAt,
        "updated_at_utc_ms" to updatedAt, "deleted_at_utc_ms" to deletedAt)
    private fun BowelDetails.json() = obj("record_id" to recordId, "abdominal_pain" to abdominalPain,
        "constipation" to constipation, "anal_pain" to analPain, "blood_excluding_menstruation" to blood,
        "sticky_stool" to sticky, "stool_form_code" to stoolForm, "amount_code" to amount)
    fun encode(snapshot: BackupSnapshot, now: Long, appVersion: String): String = obj(
        "format_version" to VERSION, "app_version" to appVersion, "exported_at" to Instant.ofEpochMilli(now).toString(),
        "record_types" to arr(snapshot.types.map { obj("id" to it.id, "name" to it.name, "enabled" to it.enabled, "data_version" to it.dataVersion) }),
        "records" to arr(snapshot.entries.map { it.record.json() }),
        "bowel_details" to arr(snapshot.entries.mapNotNull { it.details?.json() }),
        "record_sources" to arr(snapshot.sources.map { obj("id" to it.id, "record_id" to it.recordId, "source" to it.source,
            "source_record_id" to it.sourceRecordId, "batch_id" to it.batchId, "raw_json" to it.rawJson, "mapping_version" to it.mappingVersion) }),
        "import_batches" to arr(snapshot.batches.map { obj("id" to it.id, "source" to it.source, "digest" to it.digest,
            "imported_at" to it.importedAt, "added" to it.added, "skipped" to it.skipped, "conflicts" to it.conflicts) })
    ).toString(2)

    private fun JSONObject.str(key: String): String = get(key).let { require(it is String) { "$key 必须是文本" }; it }
    private fun JSONObject.num(key: String): Long = get(key).let { require(it is Long || it is Int) { "$key 必须是整数" }; (it as Number).toLong() }
    private fun JSONObject.int(key: String): Int = num(key).let { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$key 超出范围" }; it.toInt() }
    private fun JSONObject.bool(key: String): Boolean = get(key).let { require(it is Boolean) { "$key 必须是布尔值" }; it }
    private fun JSONObject.ns(key: String) = if (get(key) == JSONObject.NULL) null else str(key)
    private fun JSONObject.nl(key: String) = if (get(key) == JSONObject.NULL) null else num(key)
    private fun JSONObject.ni(key: String) = if (get(key) == JSONObject.NULL) null else int(key)
    private fun JSONObject.nb(key: String) = if (get(key) == JSONObject.NULL) null else bool(key)
    private fun JSONObject.objects(key: String): List<JSONObject> = getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun uuid(value: String) { require(UUID.fromString(value).toString() == value) { "无效 UUID" } }
    private fun <T> unique(items: List<T>, id: (T) -> String) { require(items.map(id).toSet().size == items.size) { "文件包含重复 ID" } }

    fun decode(text: String): BackupSnapshot {
        require(text.length <= 20_000_000) { "备份超过 20 MB 限制" }
        val root = JSONObject(text)
        require(root.int("format_version") == VERSION) { "不支持此备份格式版本" }
        val types = root.objects("record_types").map { RecordType(it.str("id"), it.str("name"), it.bool("enabled"), it.int("data_version")) }
        require(types.all { it.id == "bowel" && it.dataVersion == 1 }) { "备份含当前版本不支持的模块" }
        unique(types) { it.id }
        val records = root.objects("records").map { o ->
            LogRecord(id = o.str("id"), typeId = o.str("type_id"), status = o.str("status"), detailsStatus = o.str("details_status"),
                startedAt = o.nl("started_at_utc_ms"), endedAt = o.nl("ended_at_utc_ms"), eventLocalDate = o.str("event_local_date"),
                zoneId = o.ns("zone_id"), startOffset = o.ni("start_offset_seconds"), endOffset = o.ni("end_offset_seconds"),
                timePrecision = o.str("time_precision"), durationMs = o.nl("duration_ms"), durationSource = o.ns("duration_source"),
                timeReviewRequired = o.bool("time_review_required"), note = o.str("note"), createdAt = o.num("created_at_utc_ms"),
                updatedAt = o.num("updated_at_utc_ms"), deletedAt = o.nl("deleted_at_utc_ms"))
        }
        unique(records) { it.id }
        records.forEach { r ->
            uuid(r.id)
            require(types.any { it.id == r.typeId }) { "记录类型引用不存在" }
            require(r.status in listOf("in_progress", "completed", "cancelled")) { "无效记录状态" }
            require(r.detailsStatus in listOf("pending", "completed")) { "无效详情状态" }
            require(r.timePrecision in listOf("instant", "minute", "date")) { "无效时间精度" }
            require(r.durationMs == null || r.durationMs >= 0) { "时长不能为负" }
            require((r.durationMs == null) == (r.durationSource == null)) { "时长与来源不匹配" }
            require(r.durationSource == null || r.durationSource in listOf("monotonic", "wall_clock", "manual", "imported")) { "无效时长来源" }
            LocalDate.parse(r.eventLocalDate)
            r.zoneId?.let { ZoneId.of(it) }
            r.startOffset?.let { ZoneOffset.ofTotalSeconds(it) }; r.endOffset?.let { ZoneOffset.ofTotalSeconds(it) }
            listOfNotNull(r.startedAt, r.endedAt, r.createdAt, r.updatedAt, r.deletedAt).forEach { Instant.ofEpochMilli(it) }
            require(r.startedAt == null || r.endedAt == null || r.endedAt >= r.startedAt || r.timeReviewRequired) { "结束时间早于开始时间" }
            require(r.status != "in_progress" || (r.startedAt != null && r.endedAt == null && r.durationMs == null)) { "进行中记录时间无效" }
        }
        val details = root.objects("bowel_details").map { o -> BowelDetails(o.str("record_id"), o.nb("abdominal_pain"),
            o.nb("constipation"), o.nb("anal_pain"), o.nb("blood_excluding_menstruation"), o.nb("sticky_stool"),
            o.ns("stool_form_code"), o.ns("amount_code")) }
        unique(details) { it.recordId }
        details.forEach { d ->
            require(records.any { it.id == d.recordId }) { "详情引用不存在" }
            require(d.stoolForm == null || d.stoolForm in (1..7).map { "bristol_$it" }) { "无效性状" }
            require(d.amount == null || d.amount in amountLabels) { "无效排便量" }
        }
        require(records.all { r -> details.any { it.recordId == r.id } }) { "记录缺少详情行" }
        val batches = root.objects("import_batches").map { o -> ImportBatch(o.str("id"), o.str("source"), o.str("digest"),
            o.num("imported_at"), o.int("added"), o.int("skipped"), o.int("conflicts")) }
        unique(batches) { it.id }
        batches.forEach { uuid(it.id); require(it.added >= 0 && it.skipped >= 0 && it.conflicts >= 0) { "批次数量无效" } }
        val sources = root.objects("record_sources").map { o -> RecordSource(o.str("id"), o.str("record_id"), o.str("source"),
            o.ns("source_record_id"), o.ns("batch_id"), o.ns("raw_json"), o.int("mapping_version")) }
        unique(sources) { it.id }
        sources.forEach { s ->
            uuid(s.id)
            require(records.any { it.id == s.recordId }) { "来源引用的记录不存在" }
            require(s.batchId == null || batches.any { it.id == s.batchId }) { "来源引用的批次不存在" }
            require(s.mappingVersion > 0) { "无效映射版本" }
        }
        return BackupSnapshot(types, records.map { r -> BowelEntry(r, details.single { it.recordId == r.id }) }, sources, batches)
    }

    private fun cell(value: Any?): String {
        var text = value?.toString() ?: ""
        if (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || text.startsWith('\t') || text.startsWith('\r')) text = "'" + text
        return "\"${text.replace("\"", "\"\"")}\""
    }
    fun csv(snapshot: BackupSnapshot): String {
        val headers = listOf("ID", "日期", "开始UTC毫秒", "结束UTC毫秒", "时区", "开始偏移秒", "结束偏移秒", "时间精度", "时长毫秒", "时长来源", "需检查时间",
            "详情状态", *symptomLabels.toTypedArray(), "性状", "排便量", "备注", "来源")
        val rows = snapshot.entries.filter { it.isCompleted() }.map { e ->
            val r = e.record; val d = e.details
            listOf(r.id, r.eventLocalDate, r.startedAt, r.endedAt, r.zoneId, r.startOffset, r.endOffset, r.timePrecision,
                r.durationMs, r.durationSource, r.timeReviewRequired, r.detailsStatus) +
                (d?.flags() ?: List(5) { null }).map { it?.let { b -> if (b) "是" else "否" } } +
                listOf(d?.stoolForm, d?.amount, r.note, snapshot.sources.filter { it.recordId == r.id }.joinToString(";") { it.source })
        }
        return "\uFEFF" + (listOf(headers) + rows).joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } } + "\r\n"
    }
}
