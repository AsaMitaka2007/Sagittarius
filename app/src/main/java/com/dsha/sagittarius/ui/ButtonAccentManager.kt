package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.buttonAccentDataStore by preferencesDataStore(name = "button_accent_style")

data class ButtonAccentStyle(
    val id: String,
    val label: String,
    val startColor: Int,
    val endColor: Int
) {
    val midColor: Int get() = ColorUtils.blendARGB(startColor, endColor, 0.5f)
    val colors: IntArray get() = intArrayOf(startColor, midColor, endColor)

    val isLight: Boolean
        get() = (ColorUtils.calculateLuminance(startColor) + ColorUtils.calculateLuminance(endColor)) / 2.0 > 0.52

    val isTransparent: Boolean
        get() = Color.alpha(startColor) == 0 && Color.alpha(endColor) == 0

    val isInk: Boolean
        get() = id == "ink"

    /** 胶囊/卡片描边：墨水黑用米白，其余用当前按钮色的半透明版本。 */
    val borderColor: Int
        get() = if (isInk) Color.parseColor("#CCF3EBDD") else ColorUtils.setAlphaComponent(startColor, 210)

    val textColor: Int
        get() = when {
            isInk -> Color.parseColor("#F3EBDD")
            isLight -> Color.parseColor("#101828")
            else -> Color.WHITE
        }
}

object ButtonAccentManager {
    private val idKey = stringPreferencesKey("id")
    private val startKey = intPreferencesKey("start")
    private val endKey = intPreferencesKey("end")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val presets: List<ButtonAccentStyle> = listOf(
        ButtonAccentStyle("red", "霓虹红", Color.parseColor("#FF1744"), Color.parseColor("#FF6A45")),
        ButtonAccentStyle("aurora", "极光绿", Color.parseColor("#2ED9B0"), Color.parseColor("#0E2E6B")),
        ButtonAccentStyle("cyber", "赛博紫", Color.parseColor("#4B1D95"), Color.parseColor("#FF3FBF")),
        ButtonAccentStyle("sunset", "晚霞橙", Color.parseColor("#FF7A18"), Color.parseColor("#FFB347")),
        ButtonAccentStyle("blue", "冰川蓝", Color.parseColor("#4DA8FF"), Color.parseColor("#7B61FF")),
        ButtonAccentStyle("caramel", "焦糖", Color.parseColor("#C58A66"), Color.parseColor("#B87A8E")),
        ButtonAccentStyle("transparent", "透明", Color.TRANSPARENT, Color.TRANSPARENT)
    )

    private val _style = MutableStateFlow(presets[1])
    val style: StateFlow<ButtonAccentStyle> = _style.asStateFlow()
    private val inkStyle = ButtonAccentStyle(
        "ink", "墨水黑", Color.parseColor("#0B1220"), Color.parseColor("#0B1220")
    )
    /** 奶油主题的默认焦糖强调色；用户主动选过其他颜色时仍尊重用户选择。 */
    private val insStyle = ButtonAccentStyle(
        "caramel", "焦糖", Color.parseColor("#C58A66"), Color.parseColor("#B87A8E")
    )
    val current: ButtonAccentStyle
        get() = when {
            ThemeManager.current == ThemeMode.BLACK -> inkStyle
            ThemeManager.current == ThemeMode.INS && _style.value.id == "aurora" -> insStyle
            else -> _style.value
        }

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val stored = runBlocking(Dispatchers.IO) {
                runCatching { appContext.buttonAccentDataStore.data.first() }.getOrNull()
            }
            val id = stored?.get(idKey).orEmpty()
            val custom = stored?.get(startKey)?.let { start ->
                ButtonAccentStyle("custom", "自定义", start, stored[endKey] ?: start)
            }
            _style.value = presets.firstOrNull { it.id == id } ?: custom ?: presets[1]
            initialized = true
        }
    }

    fun setStyle(context: Context, style: ButtonAccentStyle) {
        _style.value = style
        val appContext = context.applicationContext
        scope.launch {
            appContext.buttonAccentDataStore.edit {
                it[idKey] = style.id
                it[startKey] = style.startColor
                it[endKey] = style.endColor
            }
        }
    }

    fun custom(start: Int, end: Int = start): ButtonAccentStyle =
        ButtonAccentStyle("custom", "自定义", start, end)
}
