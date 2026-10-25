package com.lunhui.log.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
class AppPreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.settingsStore.data.map { it[themeKey] ?: "system" }
    suspend fun setTheme(value: String) {
        require(value in listOf("system", "light", "dark"))
        context.settingsStore.edit { it[themeKey] = value }
    }
}
