package com.lunhui.log.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.formatDuration
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CycleMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(40.dp)) {
        val w = size.width; val h = size.height
        drawArc(color, 205f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(w * .16f, h * .16f),
            size = androidx.compose.ui.geometry.Size(w * .68f, h * .68f), style = Stroke(w * .065f, cap = StrokeCap.Round))
        drawArc(color, 25f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(w * .16f, h * .16f),
            size = androidx.compose.ui.geometry.Size(w * .68f, h * .68f), style = Stroke(w * .065f, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(w * .69f, h * .29f); lineTo(w * .83f, h * .36f); lineTo(w * .87f, h * .20f) }, color,
            style = Stroke(w * .06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(Path().apply { moveTo(w * .31f, h * .71f); lineTo(w * .17f, h * .64f); lineTo(w * .13f, h * .80f) }, color,
            style = Stroke(w * .06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

fun recordTime(ms: Long?, record: LogRecord, pattern: String = "HH:mm"): String {
    if (ms == null) return "时间未记录"
    val zone = record.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: record.startOffset?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneOffset.UTC
    return Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern(pattern))
}

@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun EntryCard(entry: BowelEntry, onClick: () -> Unit) {
    val r = entry.record
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (r.status == "in_progress") "正在记录" else recordTime(r.startedAt, r), style = MaterialTheme.typography.titleMedium)
                Text(if (r.status == "in_progress") "进行中" else formatDuration(r.durationMs), color = MaterialTheme.colorScheme.primary)
            }
            if (r.status == "completed") {
                Text(if (r.detailsStatus == "pending") "时间已保存 · 待补充详情" else
                    listOfNotNull(entry.details?.stoolForm?.let { formLabel(it) }, entry.details?.amount?.let { amountLabels[it] })
                        .joinToString(" · ").ifEmpty { "已记录" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.timeReviewRequired) Text("记录时间需检查", color = MaterialTheme.colorScheme.error)
                if (r.note.isNotBlank()) Text(r.note, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
