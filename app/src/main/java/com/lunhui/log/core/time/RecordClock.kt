package com.lunhui.log.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.lunhui.log.core.model.ActiveTimer
import java.time.*
import kotlin.math.abs

data class ClockReading(val wall: Long, val elapsed: Long, val boot: String?, val zone: ZoneId)
fun interface RecordClock { fun read(): ClockReading }
class AndroidRecordClock(private val context: Context) : RecordClock {
    override fun read() = ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull(), ZoneId.systemDefault())
}
data class MeasuredDuration(val millis: Long, val source: String, val review: Boolean)
fun measureDuration(startWall: Long, timer: ActiveTimer?, now: ClockReading): MeasuredDuration {
    val wallDelta = now.wall - startWall
    if (timer != null && timer.bootId != null && timer.bootId == now.boot && now.elapsed >= timer.elapsedStart) {
        val elapsed = now.elapsed - timer.elapsedStart
        return MeasuredDuration(elapsed, "monotonic", abs(wallDelta - elapsed) > 5_000)
    }
    return MeasuredDuration(wallDelta.coerceAtLeast(0), "wall_clock", true)
}
fun formatDuration(ms: Long?): String {
    if (ms == null) return "未记录"
    val secs = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60)
}
fun formatSince(ms: Long): String = when {
    ms < 0 -> "记录时间需检查"
    ms < 60_000 -> "刚刚"
    ms < 3_600_000 -> "${ms / 60_000} 分钟"
    ms < 86_400_000 -> "${ms / 3_600_000} 小时 ${ms / 60_000 % 60} 分钟"
    else -> "${ms / 86_400_000} 天 ${ms / 3_600_000 % 24} 小时"
}
