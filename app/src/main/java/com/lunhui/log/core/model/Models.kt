package com.lunhui.log.core.model

import androidx.room.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Entity(tableName = "record_types")
data class RecordType(@PrimaryKey val id: String = "bowel", val name: String = "排便", val enabled: Boolean = true, val dataVersion: Int = 1)

@Entity(tableName = "records",
    foreignKeys = [ForeignKey(entity = RecordType::class, parentColumns = ["id"], childColumns = ["typeId"])],
    indices = [Index(value = ["typeId", "eventLocalDate"]), Index(value = ["typeId", "status", "endedAt"])])
data class LogRecord(
    @PrimaryKey val id: String,
    val typeId: String = "bowel",
    val status: String = "in_progress",
    val detailsStatus: String = "pending",
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val eventLocalDate: String,
    val zoneId: String? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val timePrecision: String = "instant",
    val durationMs: Long? = null,
    val durationSource: String? = null,
    val timeReviewRequired: Boolean = false,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "bowel_details", foreignKeys = [ForeignKey(entity = LogRecord::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class BowelDetails(
    @PrimaryKey val recordId: String,
    val abdominalPain: Boolean? = null,
    val constipation: Boolean? = null,
    val analPain: Boolean? = null,
    val blood: Boolean? = null,
    val sticky: Boolean? = null,
    val stoolForm: String? = null,
    val amount: String? = null,
) {
    fun flags() = listOf(abdominalPain, constipation, analPain, blood, sticky)
}

@Entity(tableName = "active_timers", foreignKeys = [ForeignKey(entity = LogRecord::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["recordId"], unique = true)])
data class ActiveTimer(@PrimaryKey val typeId: String = "bowel", val recordId: String, val elapsedStart: Long, val bootId: String?)

@Entity(tableName = "import_batches")
data class ImportBatch(@PrimaryKey val id: String, val source: String, val digest: String, val importedAt: Long,
    val added: Int, val skipped: Int, val conflicts: Int)

@Entity(tableName = "record_sources", foreignKeys = [
    ForeignKey(entity = LogRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ImportBatch::class, parentColumns = ["id"], childColumns = ["batchId"])
], indices = [Index("recordId"), Index("batchId")])
data class RecordSource(@PrimaryKey val id: String, val recordId: String, val source: String,
    val sourceRecordId: String? = null, val batchId: String? = null, val rawJson: String? = null, val mappingVersion: Int = 1)

data class BowelEntry(@Embedded val record: LogRecord,
    @Relation(parentColumn = "id", entityColumn = "recordId") val details: BowelDetails?)

val symptomLabels = listOf("肠胃疼", "便秘", "肛门疼", "带血（经血除外）", "黏马桶")
val stoolLabels = listOf("分散硬颗粒", "结块条状", "条状，表面有裂纹", "光滑柔软条状", "柔软块状，边缘清晰", "糊状，边缘松散", "水样，无固体")
val amountLabels = linkedMapOf("very_small" to "非常少", "small" to "少量", "medium" to "适中", "large" to "大量")
fun formLabel(code: String?): String = code?.removePrefix("bristol_")?.toIntOrNull()?.takeIf { it in 1..7 }
    ?.let { "$it 型 · ${stoolLabels[it - 1]}" } ?: "性状未记录"
fun BowelEntry.isVisible() = record.deletedAt == null && record.status != "cancelled"
fun BowelEntry.isCompleted() = isVisible() && record.status == "completed"

fun latestCompleted(entries: List<BowelEntry>): BowelEntry? {
    val completed = entries.filter { it.isCompleted() }
    val precise = completed.filter { it.record.endedAt != null }.maxByOrNull { it.record.endedAt!! }
    val dateOnly = completed.filter { it.record.endedAt == null }.maxByOrNull { it.record.eventLocalDate }
    if (precise == null) return dateOnly
    if (dateOnly == null) return precise
    val r = precise.record
    val zone = r.zoneId?.let { ZoneId.of(it) } ?: r.endOffset?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneOffset.UTC
    val preciseDate = Instant.ofEpochMilli(r.endedAt!!).atZone(zone).toLocalDate()
    return if (LocalDate.parse(dateOnly.record.eventLocalDate) > preciseDate) dateOnly else precise
}

data class Trends(val count: Int, val medianMs: Long?, val durationCount: Int,
    val daily: Map<LocalDate, Int>, val forms: List<Int>, val symptoms: List<Pair<Int, Int>>)

fun calculateTrends(entries: List<BowelEntry>, start: LocalDate, end: LocalDate): Trends {
    require(!end.isBefore(start))
    val selected = entries.filter { it.isCompleted() && LocalDate.parse(it.record.eventLocalDate) in start..end }
    val durations = selected.mapNotNull { it.record.durationMs }.sorted()
    val median = if (durations.isEmpty()) null else if (durations.size % 2 == 1) durations[durations.size / 2]
        else { val a = durations[durations.size / 2 - 1]; val b = durations[durations.size / 2]; a + (b - a) / 2 }
    val counts = selected.groupingBy { it.record.eventLocalDate }.eachCount()
    val daily = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }
        .associateWith { day -> counts[day.toString()] ?: 0 }
    return Trends(selected.size, median, durations.size, daily,
        (1..7).map { n -> selected.count { it.details?.stoolForm == "bristol_$n" } },
        (0..4).map { i -> val flags = selected.mapNotNull { it.details?.flags()?.get(i) }; flags.count { it } to flags.size })
}
