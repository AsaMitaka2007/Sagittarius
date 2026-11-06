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

private val Context.consoleImageStore by preferencesDataStore(name = "console_image")

object DateConsoleImageManager {
    private val key = stringPreferencesKey("path")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _path = MutableStateFlow<String?>(null)
    val path: StateFlow<String?> = _path.asStateFlow()
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            _path.value = runBlocking(Dispatchers.IO) {
                runCatching { app.consoleImageStore.data.first()[key] }.getOrNull()
            }
            initialized = true
        }
    }

    fun setPath(context: Context, path: String?) {
        _path.value = path
        val app = context.applicationContext
        scope.launch { app.consoleImageStore.edit { it[key] = path.orEmpty() } }
    }
}
