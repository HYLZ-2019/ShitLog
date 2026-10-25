package com.lunhui.log.feature.bowel

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lunhui.log.LogViewModel
import com.lunhui.log.core.model.*
import com.lunhui.log.core.time.formatDuration
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun EditorScreen(model: LogViewModel, entry: BowelEntry?, date: LocalDate, localPending: Boolean, busy: Boolean,
    onClose: () -> Unit, snackbar: SnackbarHostState) {
    val r = entry?.record
    val key = r?.id ?: "new"
    val initialFlags = entry?.details?.flags()?.map { if (it == null && localPending) false else it } ?: List(5) { false }
    var flags by rememberSaveable(key) { mutableStateOf(initialFlags) }
    var form by rememberSaveable(key) { mutableStateOf(entry?.details?.stoolForm) }
    var amount by rememberSaveable(key) { mutableStateOf(entry?.details?.amount) }
    var note by rememberSaveable(key) { mutableStateOf(r?.note ?: "") }
    var start by rememberSaveable(key) { mutableStateOf(r?.startedAt) }
    var end by rememberSaveable(key) { mutableStateOf(r?.endedAt) }
    var timeChanged by rememberSaveable(key) { mutableStateOf(false) }
    var editTime by rememberSaveable(key) { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val zone = r?.zoneId?.let { ZoneId.of(it) } ?: r?.startOffset?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val duration = if (timeChanged || r == null) {
        if (start != null && end != null && end!! >= start!!) end!! - start!! else null
    } else r.durationMs
    val dirty = flags != initialFlags || form != entry?.details?.stoolForm || amount != entry?.details?.amount ||
        note != (r?.note ?: "") || timeChanged
    fun close() { if (!busy) { if (dirty) leave = true else onClose() } }
    val save: () -> Unit = {
        model.action {
            model.repository.save(r?.id, start, end, zone.id, timeChanged, note,
                BowelDetails(key, flags[0], flags[1], flags[2], flags[3], flags[4], form, amount))
            onClose()
        }
    }
    BackHandler { close() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (r == null) "补记排便" else "排便详情") },
        navigationIcon = { IconButton(onClick = { close() }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
        actions = { if (r != null) IconButton(onClick = { delete = true }, enabled = !busy) { Icon(Icons.Outlined.Delete, "删除记录") } }) },
        snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { close() }, enabled = !busy) {
                        Text(if (r?.detailsStatus == "pending") "稍后补充" else "返回")
                    }
                    Button(onClick = save, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("保存") }
                }
            }
        }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("时长", style = MaterialTheme.typography.titleSmall)
                Text(formatDuration(duration), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { editTime = true }, enabled = !busy) { Text(if (r == null && !timeChanged) "填写" else "修改") }
            }
            if (r?.timeReviewRequired == true && !timeChanged) Text("时间待核对", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                symptomLabels.forEachIndexed { i, label ->
                    FilterChip(selected = flags[i] == true, enabled = !busy,
                        onClick = { flags = flags.mapIndexed { n, v -> if (i == n) v != true else v } },
                        shape = RoundedCornerShape(50),
                        leadingIcon = { Box(Modifier.size(14.dp)) { if (flags[i] == true) Icon(Icons.Outlined.Check, null) } },
                        label = { Text((if (i == 3) "带血（非经血）" else label) + if (flags[i] == null) " · 未记" else "") },
                        modifier = Modifier.semantics {
                            contentDescription = label
                            stateDescription = when (flags[i]) { true -> "是"; false -> "否"; null -> "未记录" }
                        })
                }
            }
            Text("性状", style = MaterialTheme.typography.titleSmall)
            StoolChoices(form, !busy) { form = it }
            Text("排便量", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                amountLabels.forEach { (code, label) ->
                    FilterChip(selected = amount == code, onClick = { amount = if (amount == code) null else code },
                        enabled = !busy, shape = RoundedCornerShape(50), label = { Text(label) })
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("文字备注") },
                minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
    if (editTime) TimeEditDialog(start, duration, zone, date,
        now = { model.repository.clock.read().wall }, onDismiss = { editTime = false }) { newStart, newEnd ->
        start = newStart; end = newEnd; timeChanged = true; editTime = false
    }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("保存修改？") },
        confirmButton = { TextButton(onClick = { leave = false; save() }) { Text("保存") } },
        dismissButton = { Row { TextButton(onClick = { leave = false }) { Text("继续编辑") }; TextButton(onClick = { leave = false; onClose() }) { Text("放弃修改") } } })
    if (delete && r != null) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除这条记录？") },
        confirmButton = { TextButton(onClick = { delete = false; model.action { model.repository.delete(r.id); onClose() } }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("保留") } })
}

@Composable private fun StoolChoices(value: String?, enabled: Boolean, onChange: (String?) -> Unit) {
    val captions = listOf("颗粒", "结块", "裂纹", "光滑", "软块", "糊状", "水样")
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 360.dp && scale <= 1.15f -> 7
            maxWidth >= 280.dp && scale <= 1.4f -> 4
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).toList().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { n ->
                        val selected = value == "bristol_$n"
                        Card(onClick = { onChange(if (selected) null else "bristol_$n") }, enabled = enabled,
                            colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).semantics {
                                this.selected = selected
                                contentDescription = "$n 型 · ${stoolLabels[n - 1]}"
                            }) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                StoolIllustration(n, Modifier.size(24.dp))
                                Text("$n 型", style = MaterialTheme.typography.labelMedium)
                                Text(captions[n - 1], style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable private fun TimeEditDialog(initialStart: Long?, duration: Long?, zone: ZoneId, date: LocalDate,
    now: () -> Long, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var start by rememberSaveable { mutableStateOf(initialStart) }
    var minutes by rememberSaveable { mutableStateOf(duration?.let { (it / 60_000).toString() } ?: "") }
    var seconds by rememberSaveable { mutableStateOf(duration?.let { (it / 1000 % 60).toString() } ?: "0") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("修改时间") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeButton("开始时间", start, zone, date) { start = it; error = null }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = minutes, onValueChange = { minutes = it; error = null }, label = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = seconds, onValueChange = { seconds = it; error = null }, label = { Text("秒") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { manualEndTime(start, minutes, seconds, now()) }
            .onSuccess { onConfirm(start!!, it) }
            .onFailure { error = it.message ?: "时间无效" }
    }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable fun DateTimeButton(label: String, value: Long?, zone: ZoneId, fallbackDate: LocalDate,
    enabled: Boolean = true, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val base = value?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: fallbackDate.atTime(LocalTime.now().withSecond(0).withNano(0))
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val local = LocalDate.of(y, m + 1, d).atTime(h, min)
                val offsets = zone.rules.getValidOffsets(local)
                if (offsets.isEmpty()) {
                    android.widget.Toast.makeText(context, "该时间处于夏令时跳变区间，请重新选择", android.widget.Toast.LENGTH_LONG).show()
                } else onPick(ZonedDateTime.ofLocal(local, zone, offsets.first()).toInstant().toEpochMilli())
            }, base.hour, base.minute, true).show()
        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) } ?: "点击选择")
        }
    }
}

@Composable fun StoolIllustration(type: Int, modifier: Modifier = Modifier.size(46.dp)) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (type) {
            1 -> listOf(.28f to .28f, .65f to .24f, .45f to .59f, .72f to .76f).forEach { (x,y) -> drawCircle(color, w * .10f, Offset(w*x,h*y)) }
            2 -> (0..4).forEach { n -> drawCircle(color, w * .16f, Offset(w*(.26f+.11f*n), h*(.25f+.12f*n))) }
            3, 4 -> {
                drawLine(color, Offset(w*.25f,h*.24f), Offset(w*.74f,h*.76f), w*.29f, StrokeCap.Round)
                if (type == 3) (0..2).forEach { n -> drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha=.8f), Offset(w*(.3f+n*.14f),h*(.31f+n*.14f)), Offset(w*(.46f+n*.14f),h*(.28f+n*.14f)), w*.035f) }
            }
            5 -> listOf(.27f to .34f, .68f to .28f, .55f to .7f).forEach { (x,y) -> drawOval(color, Offset(w*(x-.12f),h*(y-.1f)), Size(w*.25f,h*.22f)) }
            6 -> (0..7).forEach { n -> val a = n * .78; drawCircle(color.copy(alpha=.8f),w*.16f,Offset(w*(.5f+.24f*kotlin.math.cos(a).toFloat()), h*(.5f+.22f*kotlin.math.sin(a).toFloat()))) }
            7 -> (0..2).forEach { n -> drawArc(color, 10f, 150f, false, Offset(w*.12f,h*(.12f+n*.22f)), Size(w*.75f,h*.22f),style=Stroke(w*.05f,cap=StrokeCap.Round)) }
        }
    }
}
