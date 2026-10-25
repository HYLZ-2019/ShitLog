package com.lunhui.log.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunhui.log.BuildConfig
import com.lunhui.log.LogViewModel
import com.lunhui.log.core.backup.*
import com.lunhui.log.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private data class PendingRestore(val text: String, val snapshot: BackupSnapshot, val preview: RestorePreview)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(model: LogViewModel, busy: Boolean) {
    val context = LocalContext.current
    val theme by model.theme.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingRestore?>(null) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.action {
            val snapshot = model.backups.snapshot()
            withContext(Dispatchers.IO) {
                val text = BackupCodec.encode(snapshot, System.currentTimeMillis(), BuildConfig.VERSION_NAME)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
            model.message.value = "完整备份已导出"
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.action {
            val snapshot = model.backups.snapshot()
            withContext(Dispatchers.IO) {
                val text = BackupCodec.csv(snapshot)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
            model.message.value = "CSV 已导出"
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.action {
            val (text,snapshot) = withContext(Dispatchers.IO) {
                val bytes = ByteArrayOutputStream()
                requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer); if (n < 0) break
                        require(bytes.size() + n <= 20_000_000) { "备份超过 20 MB 限制" }
                        bytes.write(buffer,0,n)
                    }
                }
                val text = bytes.toString("UTF-8").removePrefix("\uFEFF")
                text to BackupCodec.decode(text)
            }
            pending = PendingRestore(text,snapshot,model.backups.preview(snapshot))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionTitle("外观")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (code,label) ->
                FilterChip(selected = theme == code, onClick = { model.theme(code) }, enabled = !busy, label = { Text(label) })
            }
        }
        HorizontalDivider()
        SectionTitle("数据由你掌握", "无需账户，记录保存在当前设备的应用私有目录")
        Button(onClick = { exportJson.launch("轮回记录-备份-${LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("导出完整备份（JSON）") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("从备份恢复") }
        OutlinedButton(onClick = { exportCsv.launch("轮回记录-排便-${LocalDate.now()}.csv") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("导出 CSV") }
        Text("JSON 用于完整恢复，CSV 用于查看和分析。两个格式都是明文文件，请自行选择保存位置。卸载应用可能删除本地数据，建议定期导出备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("恢复时只合并新增数据。重复内容跳过，冲突保留本地，进行中的记录需跳过后手工补记。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        SectionTitle("关于轮回记录")
        Text("版本 ${BuildConfig.VERSION_NAME} · 备份格式 ${BackupCodec.VERSION}")
        Text("无广告 · 无账户 · 离线使用", color = MaterialTheme.colorScheme.primary)
        Text("以循环为记，把日常留在自己手中。", style = MaterialTheme.typography.bodySmall)
    }
    pending?.let { p ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("确认合并备份？") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("新增 ${p.preview.added} 条\n重复 ${p.preview.duplicate} 条\n冲突 ${p.preview.conflicts} 条（保留本地）\n进行中 ${p.preview.activeSkipped} 条（跳过）")
                if (p.preview.activeSkipped > 0) Text("确认即同意跳过进行中记录；可稍后在历史中手工补记。")
                Text("包含的已删除记录会保留删除状态。本地已有内容不会被覆盖。")
            } },
            confirmButton = { TextButton(onClick = {
                pending = null
                model.action {
                    val result = model.backups.restore(p.snapshot,p.text,System.currentTimeMillis())
                    model.message.value = "恢复完成：新增 ${result.added}，重复 ${result.duplicate}，冲突 ${result.conflicts}，进行中跳过 ${result.activeSkipped}"
                }
            }) { Text(if (p.preview.activeSkipped > 0) "跳过进行中并合并" else "确认合并") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}
