package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
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

private val Context.dateConsoleDataStore by preferencesDataStore(name = "date_console_style")

data class DateConsoleStyle(
    val id: String,
    val label: String,
    val startColor: Int? = null,
    val endColor: Int? = null,
    val lightText: Boolean = false
) {
    val isTheme: Boolean get() = startColor == null || endColor == null
}

/**
 * 顶栏颜色/渐变预设。存入独立 DataStore；默认跟随主题 token。
 */
object DateConsoleStyleManager {

    private val key = stringPreferencesKey("style_id")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val presets: List<DateConsoleStyle> = listOf(
        DateConsoleStyle("theme", "主题玻璃"),
        DateConsoleStyle("gray", "浅灰", Color.parseColor("#E6EEF2F7"), Color.parseColor("#D9DCE3ED"), false),
        DateConsoleStyle("black", "深黑", Color.parseColor("#E60B1020"), Color.parseColor("#D90F172A"), true),
        DateConsoleStyle("blue", "淡蓝", Color.parseColor("#D9CFE8FF"), Color.parseColor("#B3A8C7F0"), false),
        DateConsoleStyle("pink", "淡粉", Color.parseColor("#E6FFD9E6"), Color.parseColor("#CCF3B8D4"), false),
        DateConsoleStyle("gold", "金色", Color.parseColor("#E6F5D9A8"), Color.parseColor("#D9D9B36C"), false),
        DateConsoleStyle("teal", "青绿", Color.parseColor("#E63AAE9C"), Color.parseColor("#CC1F7A6E"), true),
        DateConsoleStyle("violet", "紫罗兰", Color.parseColor("#E6665CFF"), Color.parseColor("#CCB65CFF"), true),
        DateConsoleStyle("aurora", "极光绿", Color.parseColor("#E62ED9B0"), Color.parseColor("#E60E2E6B"), true),
        DateConsoleStyle("sunset", "晚霞红", Color.parseColor("#E6D35CFF"), Color.parseColor("#E6FF6A45"), true),
        DateConsoleStyle("cyber", "赛博紫", Color.parseColor("#E64B1D95"), Color.parseColor("#E6FF3FBF"), true),
        DateConsoleStyle("dawn", "晨曦蓝", Color.parseColor("#E6B9E2FF"), Color.parseColor("#E6FFF0B8"), false),
        DateConsoleStyle("quicksand", "流沙金", Color.parseColor("#E6A97A28"), Color.parseColor("#E6FFF1D6"), false)
    )

    private val _style = MutableStateFlow(presets.first())
    val style: StateFlow<DateConsoleStyle> = _style.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val stored = runBlocking(Dispatchers.IO) {
                runCatching { appContext.dateConsoleDataStore.data.first()[key] }.getOrNull()
            }
            _style.value = presets.firstOrNull { it.id == stored } ?: presets.first()
            initialized = true
        }
    }

    fun setStyle(context: Context, style: DateConsoleStyle) {
        _style.value = style
        val appContext = context.applicationContext
        scope.launch {
            appContext.dateConsoleDataStore.edit { it[key] = style.id }
        }
    }

    fun styleFor(id: String): DateConsoleStyle = presets.firstOrNull { it.id == id } ?: presets.first()

    /** 从渐变主色推导高对比度图标色，保留主色调。 */
    fun iconTint(style: DateConsoleStyle): Int {
        if (style.isTheme) return Color.WHITE
        val base = style.startColor ?: Color.WHITE
        return if (isLight(style)) {
            ColorUtils.blendARGB(base, Color.BLACK, 0.58f)
        } else {
            ColorUtils.blendARGB(base, Color.WHITE, 0.46f)
        }
    }

    fun isLight(style: DateConsoleStyle): Boolean {
        if (style.isTheme) return false
        val average = (ColorUtils.calculateLuminance(style.startColor!!) +
            ColorUtils.calculateLuminance(style.endColor!!)) / 2.0
        return average > 0.55
    }
}
