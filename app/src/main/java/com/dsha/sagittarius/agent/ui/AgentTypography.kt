package com.dsha.sagittarius.agent.ui

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Agent 页统一字体。Android 不内置 San Francisco，这里使用系统 sans-serif
 * 并收紧字距，作为最接近的可用替代；若后续提供 SF Pro 字体文件可在此替换。
 */
object AgentTypography {
    fun apply(root: View) {
        if (root is TextView) {
            val style = root.typeface?.style ?: Typeface.NORMAL
            root.typeface = Typeface.create("sans-serif", style)
            root.letterSpacing = -0.01f
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) apply(root.getChildAt(i))
        }
    }
}
