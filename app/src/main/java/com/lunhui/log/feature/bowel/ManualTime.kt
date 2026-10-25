package com.lunhui.log.feature.bowel

/** A manual entry needs a start and duration; its end is derived internally. */
fun manualEndTime(start: Long?, minutesText: String, secondsText: String, now: Long): Long {
    require(start != null) { "请选择开始时间" }
    val minutes = minutesText.toLongOrNull()
    val seconds = secondsText.toLongOrNull()
    require(minutes != null && minutes >= 0) { "请输入有效分钟数" }
    require(seconds != null && seconds in 0..59) { "秒数需在 0–59 之间" }
    val end = try {
        Math.addExact(start, Math.addExact(Math.multiplyExact(minutes, 60_000), seconds * 1000))
    } catch (_: ArithmeticException) { throw IllegalArgumentException("时长过大") }
    require(start <= now && end <= now) { "不能记录未来时间" }
    return end
}
