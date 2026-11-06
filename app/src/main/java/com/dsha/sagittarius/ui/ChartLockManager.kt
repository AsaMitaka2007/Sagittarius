package com.dsha.sagittarius.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

private val Context.chartLockDataStore by preferencesDataStore(name = "stats_chart_lock")

/** 日销折线图锁定状态；独立持久化，Fragment 重建后仍然生效。 */
object ChartLockManager {

    private val key = booleanPreferencesKey("locked")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val stored = runBlocking(Dispatchers.IO) {
                runCatching { appContext.chartLockDataStore.data.first()[key] }.getOrNull()
            } ?: false
            _locked.value = stored
            initialized = true
        }
    }

    fun setLocked(context: Context, value: Boolean) {
        _locked.value = value
        val appContext = context.applicationContext
        scope.launch { appContext.chartLockDataStore.edit { it[key] = value } }
    }
}
