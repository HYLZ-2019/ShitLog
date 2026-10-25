package com.lunhui.log

import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.*
import com.lunhui.log.core.backup.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.*
import java.util.UUID

class CoreTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun entry(day: String = "2026-10-18", duration: Long? = 60_000, status: String = "completed",
        flags: List<Boolean?> = listOf(null,null,null,null,null), id: String = UUID.randomUUID().toString()): BowelEntry {
        val r = LogRecord(id = id, status = status, detailsStatus = "completed", eventLocalDate = day,
            timePrecision = "date", durationMs = duration, durationSource = duration?.let { "imported" },
            createdAt = 1, updatedAt = 1)
        return BowelEntry(r,BowelDetails(id,flags[0],flags[1],flags[2],flags[3],flags[4]))
    }
    private fun snapshot(entries: List<BowelEntry>) = BackupSnapshot(listOf(RecordType()),entries,emptyList(),emptyList())

    @Test fun sameBootUsesMonotonicTimeEvenWhenWallClockMovesBack() {
        val timer = ActiveTimer(recordId="x",elapsedStart=1000,bootId="7")
        val result = measureDuration(100_000,timer,ClockReading(50_000,61_000,"7",zone))
        assertEquals(60_000L,result.millis); assertEquals("monotonic",result.source); assertTrue(result.review)
    }
    @Test fun rebootNeverSubtractsOldElapsedReading() {
        val result = measureDuration(1000,ActiveTimer(recordId="x",elapsedStart=100,bootId="1"),ClockReading(121_000,5000,"2",zone))
        assertEquals(120_000L,result.millis); assertEquals("wall_clock",result.source); assertTrue(result.review)
    }
    @Test fun unknownBootAndNegativeWallClockClampsAndFlagsReview() {
        val result = measureDuration(20_000,ActiveTimer(recordId="x",elapsedStart=1,bootId=null),ClockReading(10_000,50_000,null,zone))
        assertEquals(0L,result.millis); assertTrue(result.review)
    }
    @Test fun normalMonotonicTimingDoesNotNeedReview() {
        val result = measureDuration(1000,ActiveTimer(recordId="x",elapsedStart=100,bootId="1"),ClockReading(61_000,60_100,"1",zone))
        assertEquals(60_000L,result.millis); assertFalse(result.review)
    }
    @Test fun timeFormattingCoversBoundaries() {
        assertEquals("刚刚",formatSince(59_999)); assertEquals("1 分钟",formatSince(60_000))
        assertEquals("1 小时 1 分钟",formatSince(3_660_000)); assertEquals("1 天 3 小时",formatSince(97_200_000))
        assertEquals("记录时间需检查",formatSince(-1)); assertEquals("25:00:00",formatDuration(90_000_000))
    }
    @Test fun unknownFlagsDoNotEnterDenominator() {
        val list = listOf(entry(flags=listOf(true,null,null,null,null)),entry(flags=listOf(false,null,null,null,null)),entry())
        val t=calculateTrends(list,LocalDate.parse("2026-10-18"),LocalDate.parse("2026-10-18"))
        assertEquals(1 to 2,t.symptoms[0]); assertEquals(0 to 0,t.symptoms[1]); assertEquals(3,t.count)
    }
    @Test fun cancelledDeletedAndActiveDoNotEnterStats() {
        val completed = entry()
        val list = listOf(completed,entry(status="cancelled"),entry(status="in_progress"),entry().let { it.copy(record=it.record.copy(deletedAt=2)) })
        val t=calculateTrends(list,LocalDate.parse("2026-10-18"),LocalDate.parse("2026-10-18"))
        assertEquals(1,t.count)
    }
    @Test fun medianIgnoresMissingDurationAndUsesAverageForEvenCount() {
        val list=listOf(entry(duration=100),entry(duration=null),entry(duration=300))
        val t=calculateTrends(list,LocalDate.parse("2026-10-18"),LocalDate.parse("2026-10-18"))
        assertEquals(200L,t.medianMs); assertEquals(2,t.durationCount)
    }
    @Test fun dateAssignmentNotAffectedByCurrentTimeZone() {
        val a=entry(day="2024-02-29")
        val b=entry(day="2024-03-01")
        val t=calculateTrends(listOf(a,b),LocalDate.parse("2024-02-28"),LocalDate.parse("2024-03-01"))
        assertEquals(listOf(0,1,1),t.daily.values.toList())
    }
    @Test fun newerDateOnlyHistoryDoesNotShowOlderExactInterval() {
        val precise = entry(day="2026-10-16").let { it.copy(record=it.record.copy(endedAt=Instant.parse("2026-10-16T04:00:00Z").toEpochMilli(),zoneId="Asia/Shanghai")) }
        val newer = entry(day="2026-10-17")
        assertEquals(newer,latestCompleted(listOf(precise,newer)))
    }
    @Test fun exactLatestRecordUsesInstantAcrossTimeZones() {
        val early = entry(day="2026-10-18").let { it.copy(record=it.record.copy(endedAt=Instant.parse("2026-10-17T15:00:00Z").toEpochMilli(),zoneId="Asia/Tokyo")) }
        val later = entry(day="2026-10-17").let { it.copy(record=it.record.copy(endedAt=Instant.parse("2026-10-17T16:00:00Z").toEpochMilli(),zoneId="America/Los_Angeles")) }
        assertEquals(later,latestCompleted(listOf(early,later)))
    }
    @Test fun emptyStatsHaveNoFakeDurationOrDivisionByZero() {
        val t=calculateTrends(emptyList(),LocalDate.now(),LocalDate.now())
        assertNull(t.medianMs); assertEquals(0,t.count); assertEquals(List(7){0},t.forms)
    }
    @Test fun backupRoundTripPreservesUnknownAndRawSource() {
        val e=entry().let { it.copy(record=it.record.copy(note="中文,\"引号\"\n第二行",deletedAt=2)) }
        val batch=ImportBatch(UUID.randomUUID().toString(),"legacy","abc",1,1,0,0)
        val source=RecordSource(UUID.randomUUID().toString(),e.record.id,"legacy","旧ID",batch.id,"{\"颜色\":\"黄褐色\"}",1)
        val input=BackupSnapshot(listOf(RecordType()),listOf(e),listOf(source),listOf(batch))
        val decoded=BackupCodec.decode(BackupCodec.encode(input,1000,"test"))
        assertEquals(input,decoded); assertNull(decoded.entries.single().details!!.blood)
    }
    @Test fun decodeRejectsNewerVersion() {
        val root=JSONObject(BackupCodec.encode(snapshot(emptyList()),1,"test")).put("format_version",2)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }
    @Test fun decodeRejectsDanglingDetails() {
        val root=JSONObject(BackupCodec.encode(snapshot(listOf(entry())),1,"test"))
        root.getJSONArray("bowel_details").getJSONObject(0).put("record_id",UUID.randomUUID().toString())
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }
    @Test fun decodeRejectsInvalidBooleanInsteadOfCoercingIt() {
        val root=JSONObject(BackupCodec.encode(snapshot(listOf(entry())),1,"test"))
        root.getJSONArray("bowel_details").getJSONObject(0).put("abdominal_pain","false")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }
    @Test fun decodeRejectsMissingDurationSourceAndDuplicateId() {
        val e=entry()
        val root=JSONObject(BackupCodec.encode(snapshot(listOf(e)),1,"test"))
        root.getJSONArray("records").getJSONObject(0).put("duration_source",JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(snapshot(listOf(e,e)),1,"test")) }
    }
    @Test fun previewClassifiesNewDuplicateConflictAndActive() {
        val duplicate=entry(); val conflict=entry(); val fresh=entry(); val active=entry(status="in_progress",duration=null)
        val incoming=snapshot(listOf(duplicate,conflict.copy(record=conflict.record.copy(note="新备注")),fresh,active))
        val p=previewRestore(snapshot(listOf(duplicate,conflict)),incoming)
        assertEquals(1,p.added); assertEquals(1,p.duplicate); assertEquals(1,p.conflicts); assertEquals(1,p.activeSkipped)
    }
    @Test fun incomingTombstoneDoesNotSilentlyDeleteLocalRecord() {
        val e=entry(); val p=previewRestore(snapshot(listOf(e)),snapshot(listOf(e.copy(record=e.record.copy(deletedAt=2)))))
        assertEquals(1,p.conflicts); assertEquals(0,p.added)
    }
    @Test fun csvEscapesTextAndFormulaPrefixAndKeepsUnknownBlank() {
        val e=entry().let { it.copy(record=it.record.copy(note="=SUM(1,2)\n\"中文\"")) }
        val csv=BackupCodec.csv(snapshot(listOf(e)))
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"'=SUM(1,2)\n\"\"中文\"\"\"")); assertTrue(csv.contains("\"\",\"\",\"\",\"\",\"\""))
    }
}
