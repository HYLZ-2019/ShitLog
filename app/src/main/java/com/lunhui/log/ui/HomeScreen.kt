package com.lunhui.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.*
import com.lunhui.log.ui.theme.Pine
import com.lunhui.log.ui.theme.Cream
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(entries: List<BowelEntry>, timer: ActiveTimer?, now: ClockReading, busy: Boolean,
    onStart: () -> Unit, onFinish: (String) -> Unit, onCancel: (String) -> Unit, onOpen: (String) -> Unit) {
    val active = entries.firstOrNull { it.record.id == timer?.recordId && it.isVisible() }
    val completed = entries.filter { it.isCompleted() }
    val lastEntry = latestCompleted(entries)
    val latest = lastEntry?.takeIf { it.record.endedAt != null }
    var cancel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日 · EEEE", java.util.Locale.CHINA)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.fillMaxWidth().background(Pine, RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("拉屎", color = Cream, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (active != null) Text("正在记录", color = Cream.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                }
                CycleMark(Modifier.size(52.dp), Cream)
            }
            Spacer(Modifier.height(8.dp))
            if (active != null) {
                Text("本次时长", color = Cream.copy(alpha = .8f))
                Text(formatDuration(measureDuration(active.record.startedAt!!, timer, now).millis),
                    fontSize = 38.sp, fontWeight = FontWeight.Medium, color = Cream)
                Text("开始于 ${recordTime(active.record.startedAt, active.record)}", color = Cream.copy(alpha = .8f))
            } else {
                Text("距离上次排便", color = Cream.copy(alpha = .8f))
                Text(if (latest != null) formatSince(now.wall - latest.record.endedAt!!) else if (lastEntry != null) "上次记录于 ${lastEntry.record.eventLocalDate}" else "从这一次开始",
                    color = Cream, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { if (active == null) onStart() else onFinish(active.record.id) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Pine)) {
                Text(if (active == null) "开拉" else "结束", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            if (active != null) TextButton(onClick = { cancel = true }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = Cream)) { Text("取消本次") }
        }
        completed.filter { it.record.detailsStatus == "pending" }.firstOrNull()?.let { pending ->
            OutlinedCard(onClick = { onOpen(pending.record.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("有记录待补充", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (latest?.record?.let { it.endedAt!! > now.wall || it.timeReviewRequired } == true)
            TextButton(onClick = { onOpen(latest.record.id) }) { Text("上次记录时间需检查 · 前往编辑") }
        SectionTitle("最近记录")
        if (completed.isEmpty()) Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        completed.take(3).forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.record.eventLocalDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryCard(entry) { onOpen(entry.record.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (cancel && active != null) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("取消本次计时？") },
        text = { Text("本次将不计入排便历史和统计。") },
        confirmButton = { TextButton(onClick = { cancel = false; onCancel(active.record.id) }) { Text("取消本次") } },
        dismissButton = { TextButton(onClick = { cancel = false }) { Text("继续计时") } })
}
