package com.lunhui.log.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import com.lunhui.log.LogViewModel
import com.lunhui.log.core.model.*
import com.lunhui.log.feature.bowel.EditorScreen
import com.lunhui.log.feature.history.HistoryScreen
import com.lunhui.log.feature.trends.TrendsScreen
import com.lunhui.log.feature.settings.SettingsScreen
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LogApp(model: LogViewModel) {
    val entries by model.entries.collectAsStateWithLifecycle()
    val timer by model.active.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var newDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reading by remember { mutableStateOf(model.repository.clock.read()) }
    LaunchedEffect(lifecycle, timer?.recordId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { reading = model.repository.clock.read(); delay(if (timer == null) 30_000 else 1000) }
        }
    }
    val selected = entries.firstOrNull { it.record.id == editorId }
    val openEditor: (String) -> Unit = { editorId = it }
    if (editorId != null) {
        if (editorId == "new" || selected != null) {
            val localPending = selected?.record?.detailsStatus == "pending" && sources.any {
                it.recordId == editorId && it.source in listOf("local_timer", "manual") }
            androidx.compose.runtime.key(editorId, localPending) {
                EditorScreen(model, selected, LocalDate.parse(newDate), localPending, busy, { editorId = null }, snackbar)
            }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    BackHandler(settings) { settings = false }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (settings) "设置" else "轮回记录") }, navigationIcon = {
                if (settings) IconButton(onClick = { settings = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            }, actions = {
                if (!settings) IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "设置") }
            })
        },
        bottomBar = {
            if (!settings) NavigationBar {
                val icons = listOf(Icons.Outlined.RadioButtonChecked, Icons.Outlined.CalendarMonth, Icons.Outlined.BarChart)
                listOf("记录", "历史", "趋势").forEachIndexed { i, title ->
                    NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i], title) }, label = { Text(title) })
                }
            }
        }, snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (settings) SettingsScreen(model, busy)
            else when (page) {
                0 -> HomeScreen(entries, timer, reading, busy,
                    onStart = { model.action { model.repository.start() } },
                    onFinish = { id -> model.action { editorId = model.repository.finish(id) } },
                    onCancel = { id -> model.action { model.repository.cancel(id) } },
                    onOpen = openEditor)
                1 -> HistoryScreen(entries, onOpen = { e -> if (e.record.status == "in_progress") page = 0 else openEditor(e.record.id) },
                    onAdd = { date -> newDate = date.toString(); editorId = "new" })
                2 -> TrendsScreen(entries)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
