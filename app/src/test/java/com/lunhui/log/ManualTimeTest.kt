package com.lunhui.log

import com.lunhui.log.feature.bowel.manualEndTime
import org.junit.Assert.*
import org.junit.Test

class ManualTimeTest {
    @Test fun endIsDerivedFromStartAndDuration() {
        assertEquals(136_000L, manualEndTime(1_000, "2", "15", 200_000))
    }
    @Test fun durationCanCrossMidnight() {
        val start = java.time.Instant.parse("2026-10-18T23:58:00Z").toEpochMilli()
        assertEquals(start + 300_000, manualEndTime(start, "5", "0", start + 600_000))
    }
    @Test fun missingStartInvalidNumbersAndFutureEndAreRejected() {
        listOf<() -> Unit>(
            { manualEndTime(null, "1", "0", 100_000) },
            { manualEndTime(0, "", "0", 100_000) },
            { manualEndTime(0, "-1", "0", 100_000) },
            { manualEndTime(0, "1", "60", 100_000) },
            { manualEndTime(0, "1.5", "0", 100_000) },
            { manualEndTime(100_000, "1", "0", 100_000) },
            { manualEndTime(0, Long.MAX_VALUE.toString(), "0", Long.MAX_VALUE) }
        ).forEach { assertThrows(IllegalArgumentException::class.java, it) }
    }
    @Test fun zeroDurationRemainsValid() {
        assertEquals(1_000L, manualEndTime(1_000, "0", "0", 1_000))
    }
}
