package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.dsha.sagittarius.ui.MotionUtils

/** 支持流式容错标签与可暂停动画的 TextView。 */
class RichTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    private var rich: RichText? = null
    private var active = false

    fun setRichText(source: String) {
        rich?.animators?.forEach { it.stop() }
        active = false
        val parsed = RichTextParser.parse(
            source,
            invalidate = { invalidate() },
            animate = MotionUtils.ambientAllowed(context)
        )
        rich = parsed
        text = parsed.text
        if (isAttachedToWindow && windowVisibility == VISIBLE) startAnimations()
    }

    private fun startAnimations() {
        if (active) return
        active = true
        rich?.animators?.forEach { it.start() }
    }

    private fun stopAnimations() {
        if (!active) return
        active = false
        rich?.animators?.forEach { it.stop() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startAnimations() else stopAnimations()
    }
}
