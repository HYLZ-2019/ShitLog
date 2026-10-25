package com.lunhui.log

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lunhui.log.core.database.LogDatabase
import com.lunhui.log.core.time.*
import com.lunhui.log.core.model.*
import com.lunhui.log.core.backup.*
import com.lunhui.log.feature.bowel.RecordRepository
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: LogDatabase
    private lateinit var repo: RecordRepository
    private var now=ClockReading(1_792_300_000_000,10_000,"boot1",ZoneId.of("Asia/Shanghai"))
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LogDatabase::class.java).build()
        repo=RecordRepository(db,RecordClock { now })
    }
    @After fun close() { db.close() }
    @Test fun concurrentStartAndRepeatedFinishAreIdempotent() = runBlocking {
        val ids=coroutineScope { (1..8).map { async(Dispatchers.IO) { repo.start() } }.awaitAll() }
        assertEquals(1,ids.distinct().size)
        val id=ids.first(); now=now.copy(wall=now.wall+60_000,elapsed=now.elapsed+60_000)
        repo.finish(id)
        val first=db.logDao().record(id)
        now=now.copy(wall=now.wall+60_000,elapsed=now.elapsed+60_000)
        repo.finish(id)
        assertEquals(first,db.logDao().record(id)); assertNull(db.logDao().active()); assertEquals(60_000L,first!!.durationMs)
        assertEquals("pending",first.detailsStatus); assertNull(db.logDao().allEntries().single().details!!.blood)
    }
    @Test fun savingDetailsPreservesMeasuredTimeAndSupportsNull() = runBlocking {
        val id=repo.start(); now=now.copy(wall=now.wall+1000,elapsed=now.elapsed+1000); repo.finish(id)
        val old=db.logDao().record(id)!!
        repo.save(id,old.startedAt,old.endedAt,old.zoneId,false,"备注",BowelDetails(id,abdominalPain=false))
        val entry=db.logDao().allEntries().single()
        assertEquals(old.durationMs,entry.record.durationMs); assertEquals("completed",entry.record.detailsStatus)
        assertEquals(false,entry.details!!.abdominalPain); assertNull(entry.details.blood)
    }
    @Test fun cancelReleasesSlotAndIsNotCompleted() = runBlocking {
        val id=repo.start(); repo.cancel(id)
        assertNull(db.logDao().active()); assertEquals("cancelled",db.logDao().record(id)!!.status)
        assertNotEquals(id,repo.start())
    }
    @Test fun fullRestoreTwiceDoesNotDuplicateRecordsOrSources() = runBlocking {
        val id=repo.start(); now=now.copy(wall=now.wall+1000,elapsed=now.elapsed+1000); repo.finish(id)
        val backup=BackupRepository(db); val snapshot=backup.snapshot()
        val text=BackupCodec.encode(snapshot,now.wall,"test")
        val other=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LogDatabase::class.java).build()
        try {
            val target=BackupRepository(other)
            assertEquals(1,target.restore(BackupCodec.decode(text),text,now.wall).added)
            assertEquals(1,target.restore(BackupCodec.decode(text),text,now.wall).duplicate)
            assertEquals(snapshot.entries,target.snapshot().entries); assertEquals(snapshot.sources,target.snapshot().sources)
            assertEquals(1,target.snapshot().batches.size)
        } finally { other.close() }
    }
    @Test fun invalidManualTimeRollsBackWithoutCreatingRecord() = runBlocking {
        assertTrue(runCatching { repo.save(null,now.wall,now.wall-1,"Asia/Shanghai",true,"",BowelDetails("new")) }.isFailure)
        assertEquals(0,db.logDao().allEntries().size)
    }
    @Test fun rebootAndWallClockChangeKeepRecordRecoverable() = runBlocking {
        val id=repo.start()
        now=now.copy(wall=now.wall-1000,elapsed=1,boot="boot2")
        repo.finish(id)
        val r=db.logDao().record(id)!!
        assertEquals(0L,r.durationMs); assertTrue(r.timeReviewRequired); assertEquals("wall_clock",r.durationSource)
    }
}
