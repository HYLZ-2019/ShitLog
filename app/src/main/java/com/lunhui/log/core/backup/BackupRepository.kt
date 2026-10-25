package com.lunhui.log.core.backup

import androidx.room.withTransaction
import com.lunhui.log.core.database.LogDatabase
import com.lunhui.log.core.model.*
import java.security.MessageDigest
import java.util.UUID

data class RestorePreview(val added: Int, val duplicate: Int, val conflicts: Int, val activeSkipped: Int,
    val conflictIds: Set<String>)

fun previewRestore(local: BackupSnapshot, incoming: BackupSnapshot): RestorePreview {
    val existing = local.entries.associateBy { it.record.id }
    val oldSources = local.sources.associateBy { it.id }
    val localBatches = local.batches.associateBy { it.id }
    require(incoming.batches.none { localBatches[it.id]?.let { old -> old != it } == true }) { "备份批次冲突，未导入任何数据" }
    val conflictingSourceRecords = incoming.sources.filter { s -> oldSources[s.id]?.let { it != s } == true }.map { it.recordId }.toSet()
    val conflictIds = incoming.entries.filter { e ->
        val old = existing[e.record.id]
        (old != null && old != e) || e.record.id in conflictingSourceRecords
    }.map { it.record.id }.toSet()
    val completed = incoming.entries.filter { it.record.status != "in_progress" }
    return RestorePreview(completed.count { it.record.id !in existing && it.record.id !in conflictIds },
        completed.count { it.record.id in existing && it.record.id !in conflictIds },
        completed.count { it.record.id in conflictIds }, incoming.entries.count { it.record.status == "in_progress" }, conflictIds)
}

class BackupRepository(private val db: LogDatabase) {
    private val dao = db.logDao()
    private suspend fun readSnapshot() = BackupSnapshot(dao.types(), dao.allEntries(), dao.sources(), dao.batches())
    suspend fun snapshot(): BackupSnapshot = db.withTransaction { readSnapshot() }
    suspend fun preview(incoming: BackupSnapshot): RestorePreview = previewRestore(snapshot(), incoming)
    suspend fun restore(incoming: BackupSnapshot, text: String, now: Long): RestorePreview = db.withTransaction {
        val local = readSnapshot()
        val result = previewRestore(local, incoming)
        val localBatches = local.batches.associateBy { it.id }
        require(incoming.batches.none { localBatches[it.id]?.let { old -> old != it } == true }) { "备份批次冲突，未导入任何数据" }
        incoming.types.forEach { dao.putType(it) }
        incoming.batches.filter { it.id !in localBatches }.forEach { dao.insertBatch(it) }
        val existing = local.entries.map { it.record.id }.toSet()
        val accepted = incoming.entries.filter { it.record.status != "in_progress" && it.record.id !in result.conflictIds }
        accepted.filter { it.record.id !in existing }.forEach { e ->
            dao.insert(e.record); e.details?.let { dao.putDetails(it) }
        }
        val acceptedIds = accepted.map { it.record.id }.toSet()
        val sourceIds = local.sources.map { it.id }.toSet()
        incoming.sources.filter { it.recordId in acceptedIds && it.id !in sourceIds }.forEach { dao.insertSource(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (local.batches.none { it.digest == digest && it.source == "backup_restore" }) {
            dao.insertBatch(ImportBatch(UUID.randomUUID().toString(), "backup_restore", digest, now,
                result.added, result.duplicate + result.activeSkipped, result.conflicts))
        }
        result
    }
}
