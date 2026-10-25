package com.lunhui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunhui.log.core.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunhuiApplication
    val repository = app.repository
    val backups = app.backups
    val entries = repository.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val active = repository.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val sources = repository.dao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val theme = app.preferences.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = e.message ?: "操作失败，请重试"
            } finally { busy.value = false }
        }
    }
    fun theme(value: String) = action { app.preferences.setTheme(value) }
}
