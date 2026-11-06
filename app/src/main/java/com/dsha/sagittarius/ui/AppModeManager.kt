package com.dsha.sagittarius.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class AppMode { ACCOUNT, DIET }

private val Context.appModeDataStore by preferencesDataStore(name = "app_mode")

object AppModeManager {
    private val key = stringPreferencesKey("mode")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _mode = MutableStateFlow(AppMode.ACCOUNT)
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val stored = runBlocking(Dispatchers.IO) {
                runCatching { app.appModeDataStore.data.first()[key] }.getOrNull()
            }
            _mode.value = AppMode.entries.firstOrNull { it.name == stored } ?: AppMode.ACCOUNT
            initialized = true
        }
    }

    fun setMode(context: Context, mode: AppMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        val app = context.applicationContext
        scope.launch { app.appModeDataStore.edit { it[key] = mode.name } }
    }
}
