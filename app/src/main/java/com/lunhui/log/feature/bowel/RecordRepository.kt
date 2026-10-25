package com.lunhui.log.feature.bowel

import androidx.room.withTransaction
import com.lunhui.log.core.database.LogDatabase
import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.*
import java.time.*
import java.util.UUID

class RecordRepository(val db: LogDatabase, val clock: RecordClock) {
    val dao = db.logDao()
    val entries = dao.observeBowel()
    val active = dao.observeActive()
    suspend fun start(): String = db.withTransaction {
        dao.active()?.let { return@withTransaction it.recordId }
        dao.putType(RecordType())
        val now = clock.read()
        val local = Instant.ofEpochMilli(now.wall).atZone(now.zone)
        val id = UUID.randomUUID().toString()
        dao.insert(LogRecord(id = id, startedAt = now.wall, eventLocalDate = local.toLocalDate().toString(),
            zoneId = now.zone.id, startOffset = local.offset.totalSeconds, createdAt = now.wall, updatedAt = now.wall))
        dao.putDetails(BowelDetails(id))
        dao.insertTimer(ActiveTimer(recordId = id, elapsedStart = now.elapsed, bootId = now.boot))
        dao.insertSource(RecordSource(UUID.randomUUID().toString(), id, "local_timer"))
        id
    }
    suspend fun finish(id: String): String = db.withTransaction {
        val record = requireNotNull(dao.record(id)) { "记录不存在" }
        if (record.status == "completed") return@withTransaction id
        require(record.status == "in_progress" && record.deletedAt == null) { "记录已取消或删除" }
        val now = clock.read()
        val measured = measureDuration(requireNotNull(record.startedAt), dao.active()?.takeIf { it.recordId == id }, now)
        dao.update(record.copy(status = "completed", endedAt = now.wall,
            endOffset = Instant.ofEpochMilli(now.wall).atZone(now.zone).offset.totalSeconds,
            durationMs = measured.millis, durationSource = measured.source, timeReviewRequired = measured.review,
            updatedAt = now.wall))
        dao.removeTimer(id)
        id
    }
    suspend fun cancel(id: String) = db.withTransaction {
        val record = requireNotNull(dao.record(id))
        if (record.status == "in_progress") {
            dao.update(record.copy(status = "cancelled", updatedAt = clock.read().wall))
            dao.removeTimer(id)
        }
    }
    suspend fun delete(id: String) = db.withTransaction {
        val record = requireNotNull(dao.record(id))
        val now = clock.read().wall
        dao.update(record.copy(deletedAt = now, updatedAt = now))
        dao.removeTimer(id)
    }
    suspend fun save(id: String?, start: Long?, end: Long?, zone: String?, timesChanged: Boolean,
        note: String, details: BowelDetails): String = db.withTransaction {
        val now = clock.read()
        val old = id?.let { requireNotNull(dao.record(it)) { "记录不存在" } }
        require(old == null || (old.status == "completed" && old.deletedAt == null)) { "记录无法编辑" }
        if (old == null || timesChanged) {
            require(start != null && end != null) { "请填写开始时间和时长" }
            require(end >= start) { "结束不能早于开始" }
            require(start <= now.wall && end <= now.wall) { "不能记录未来时间" }
        }
        val zoneId = zone?.let { ZoneId.of(it) } ?: now.zone
        val recordId = old?.id ?: UUID.randomUUID().toString()
        val record = old ?: LogRecord(id = recordId, eventLocalDate = "", createdAt = now.wall, updatedAt = now.wall)
        val saved = record.copy(status = "completed", detailsStatus = "completed", note = note, updatedAt = now.wall)
        val final = if (old == null || timesChanged) saved.copy(startedAt = start, endedAt = end,
            eventLocalDate = Instant.ofEpochMilli(start!!).atZone(zoneId).toLocalDate().toString(),
            zoneId = zoneId.id, startOffset = Instant.ofEpochMilli(start).atZone(zoneId).offset.totalSeconds,
            endOffset = Instant.ofEpochMilli(end!!).atZone(zoneId).offset.totalSeconds,
            timePrecision = "instant", durationMs = end - start, durationSource = "manual", timeReviewRequired = false) else saved
        dao.putType(RecordType())
        if (old == null) {
            dao.insert(final)
            dao.insertSource(RecordSource(UUID.randomUUID().toString(), recordId, "manual"))
        } else dao.update(final)
        dao.putDetails(details.copy(recordId = recordId))
        recordId
    }
}
