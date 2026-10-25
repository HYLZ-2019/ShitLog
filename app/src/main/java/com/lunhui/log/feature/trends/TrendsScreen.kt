package com.lunhui.log.feature.trends

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.formatDuration
import com.lunhui.log.ui.SectionTitle
import java.time.*
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrendsScreen(entries: List<BowelEntry>) {
    var preset by rememberSaveable { mutableIntStateOf(30) }
    var startText by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var endText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val start = if (preset == 0) LocalDate.parse(startText) else LocalDate.now().minusDays(preset.toLong() - 1)
    val end = if (preset == 0) LocalDate.parse(endText) else LocalDate.now()
    val valid = end >= start && ChronoUnit.DAYS.between(start, end) <= 3660
    val trends = remember(entries, start, end) { if (valid) calculateTrends(entries, start, end) else null }
    val context = LocalContext.current
    fun choose(value: String, onPicked: (String) -> Unit) {
        val date = LocalDate.parse(value)
        DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y,m+1,d).toString()) }, date.year, date.monthValue-1,date.dayOfMonth).show()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionTitle("回看日常", "只统计已完成记录，缺失数据不作推断")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "近 7 天", 30 to "近 30 天", 0 to "自定义").forEach { (n,label) ->
                FilterChip(selected = preset == n, onClick = { preset = n }, label = { Text(label) })
            }
        }
        if (preset == 0) {
            OutlinedButton(onClick = { choose(startText) { startText = it } }, modifier = Modifier.fillMaxWidth()) { Text("开始：$startText") }
            OutlinedButton(onClick = { choose(endText) { endText = it } }, modifier = Modifier.fillMaxWidth()) { Text("结束：$endText") }
        }
        if (!valid || trends == null) Text("请选择先后顺序正确、跨度不超过 10 年的日期范围", color = MaterialTheme.colorScheme.error)
        else {
            Text("$start — $end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("排便次数", "${trends.count} 次", "已完成记录", Modifier.weight(1f))
                SummaryCard("时长中位数", formatDuration(trends.medianMs), "有效 ${trends.durationCount} 条", Modifier.weight(1f))
            }
            if (trends.count == 0) Text("这个时间范围内还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("每日次数", "按记录发生时的当地日期统计")
            val peak = (trends.daily.values.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                trends.daily.forEach { (day,count) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.height(90.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(18.dp).height((if (count == 0) 2f else count.toFloat()/peak*82).dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (count == 0) .2f else 1f), RoundedCornerShape(5.dp)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${day.monthValue}/${day.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            SectionTitle("性状分布", "已记录 ${trends.forms.sum()} 条")
            trends.forms.forEachIndexed { i, count ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i+1} 型", Modifier.width(42.dp))
                    LinearProgressIndicator(progress = { if (trends.forms.sum() == 0) 0f else count.toFloat()/trends.forms.sum() },
                        modifier = Modifier.weight(1f).height(6.dp))
                    Text("$count 次", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            SectionTitle("身体感受", "只以明确填写了是／否的记录为分母")
            trends.symptoms.forEachIndexed { i, (yes, total) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(symptomLabels[i], Modifier.weight(1f))
                    Text("$yes 次 / 已记录 $total 次", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun SummaryCard(title: String, value: String, detail: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
