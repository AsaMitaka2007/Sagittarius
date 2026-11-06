package com.dsha.sagittarius.theme

import android.content.Context
import android.util.Log
import androidx.annotation.StyleRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dsha.sagittarius.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private val Context.themeDataStore by preferencesDataStore(name = "sagittarius_theme")

enum class ThemeMode(
    val storageValue: String,
    @StyleRes val themeRes: Int
) {
    LIGHT("light", R.style.Theme_Sagittarius_Light),
    BLACK("black", R.style.Theme_Sagittarius_Black),
    GLASS("glass", R.style.Theme_Sagittarius_Glass);

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: BLACK
    }
}

/**
 * DataStore 持久化主题。Activity 在 onCreate 前取当前值；
 * 切换时 StateFlow 立即发出，Base Activity 与悬浮窗服务同步响应。
 */
object ThemeManager {

    private const val THEME_STORE = "sagittarius_theme.preferences_pb"
    private const val LEGACY_THEME_STORE = "budget_left_theme.preferences_pb"

    private val key = stringPreferencesKey("theme_mode")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _mode = MutableStateFlow(ThemeMode.BLACK)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    @Volatile
    private var initialized = false

    val current: ThemeMode get() = _mode.value
    val isLight: Boolean get() = current == ThemeMode.LIGHT
    val isGlass: Boolean get() = current == ThemeMode.GLASS

    /**
     * 改名兼容：同包内旧 DataStore 文件存在而新文件不存在时，复制一次。
     * 跨包名时旧文件在旧包沙箱，读不到，这里会自然跳过。
     */
    private fun migrateLegacyThemeFile(context: Context) {
        runCatching {
            val dir = File(context.filesDir, "datastore")
            val legacy = File(dir, LEGACY_THEME_STORE)
            val current = File(dir, THEME_STORE)
            if (!legacy.exists() || current.exists()) return@runCatching
            dir.mkdirs()
            legacy.copyTo(current, overwrite = false)
        }.onFailure {
            Log.e("Sagittarius", "legacy theme migration failed", it)
        }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            migrateLegacyThemeFile(appContext)
            _mode.value = runBlocking(Dispatchers.IO) {
                runCatching {
                    val preferences = appContext.themeDataStore.data.first()
                    ThemeMode.fromStorage(preferences[key])
                }.getOrDefault(ThemeMode.BLACK)
            }
            initialized = true
        }
    }

    fun setMode(context: Context, mode: ThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        val appContext = context.applicationContext
        scope.launch {
            appContext.themeDataStore.edit { it[key] = mode.storageValue }
        }
    }
}
