package com.lunhui.log.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lunhui.log.core.model.*
import com.lunhui.log.ui.EntryCard
import com.lunhui.log.ui.SectionTitle
import java.time.*

@Composable fun HistoryScreen(entries: List<BowelEntry>, onOpen: (BowelEntry) -> Unit, onAdd: (LocalDate) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val visible = entries.filter { it.isVisible() }
    val counts = visible.filter { it.isCompleted() }.groupingBy { it.record.eventLocalDate }.eachCount()
    val activeDays = visible.filter { it.record.status == "in_progress" }.map { it.record.eventLocalDate }.toSet()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val totalCells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        } }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (0 until totalCells / 7).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { col ->
                        val day = row * 7 + col - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(54.dp)) else {
                            val date = month.atDay(day); val count = counts[date.toString()] ?: 0
                            val isActive = date.toString() in activeDays
                            val chosen = date == selected
                            Column(Modifier.weight(1f).heightIn(min = 54.dp)
                                .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                .clickable { selectedText = date.toString() }
                                .semantics { contentDescription = "$date，$count 次排便${if (isActive) "，有进行中记录" else ""}"; this.selected = chosen },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(day.toString(), fontWeight = if (date == LocalDate.now() || chosen) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(when { count > 0 -> "$count 次"; isActive -> "○"; else -> " " },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("数字为已完成次数 · ○ 进行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { selectedText = LocalDate.now().toString(); monthText = YearMonth.now().toString() }) { Text("今天") }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onAdd(selected) }, enabled = !selected.isAfter(LocalDate.now())) {
                Icon(Icons.Outlined.Add, null); Text("补记")
            }
        }
        val daily = visible.filter { it.record.eventLocalDate == selectedText }.sortedBy { it.record.startedAt }
        if (daily.isEmpty()) Text("这一天还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        daily.forEach { entry -> EntryCard(entry) { onOpen(entry) } }
        Spacer(Modifier.height(16.dp))
    }
}
