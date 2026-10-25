package com.lunhui.log.core.database

import androidx.room.*
import com.lunhui.log.core.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Transaction @Query("SELECT * FROM records WHERE typeId = 'bowel' ORDER BY eventLocalDate DESC, startedAt DESC")
    fun observeBowel(): Flow<List<BowelEntry>>
    @Transaction @Query("SELECT * FROM records ORDER BY id") suspend fun allEntries(): List<BowelEntry>
    @Query("SELECT * FROM records WHERE id = :id") suspend fun record(id: String): LogRecord?
    @Query("SELECT * FROM active_timers WHERE typeId = 'bowel'") suspend fun active(): ActiveTimer?
    @Query("SELECT * FROM active_timers WHERE typeId = 'bowel'") fun observeActive(): Flow<ActiveTimer?>
    @Query("DELETE FROM active_timers WHERE recordId = :id") suspend fun removeTimer(id: String)
    @Query("SELECT * FROM record_types ORDER BY id") suspend fun types(): List<RecordType>
    @Query("SELECT * FROM record_sources ORDER BY id") suspend fun sources(): List<RecordSource>
    @Query("SELECT * FROM record_sources ORDER BY id") fun observeSources(): Flow<List<RecordSource>>
    @Query("SELECT * FROM import_batches ORDER BY id") suspend fun batches(): List<ImportBatch>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putType(type: RecordType)
    @Insert suspend fun insert(record: LogRecord)
    @Update suspend fun update(record: LogRecord)
    @Upsert suspend fun putDetails(details: BowelDetails)
    @Insert suspend fun insertTimer(timer: ActiveTimer)
    @Insert suspend fun insertSource(source: RecordSource)
    @Insert suspend fun insertBatch(batch: ImportBatch)
}

@Database(entities = [RecordType::class, LogRecord::class, BowelDetails::class, ActiveTimer::class,
    RecordSource::class, ImportBatch::class], version = 1, exportSchema = true)
abstract class LogDatabase : RoomDatabase() { abstract fun logDao(): LogDao }
