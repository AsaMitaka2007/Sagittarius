package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.ui.LiquidGlassView
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.max

/**
 * 以按钮为原点的胶囊展开层，沿用预算悬浮窗的展开机制：
 * LiquidGlassView + 圆角/紧凑态切换 + SpringAnimation + 内容 alpha/scaleY 入场。
 */
class CapsuleOverlay(
    private val host: FrameLayout,
    private val tokens: ThemeTokens
) {

    private var scrim: View? = null
    private var capsule: LiquidGlassView? = null
    private var animator: ValueAnimator? = null
    private var dismissListener: (() -> Unit)? = null
    private var dismissAnimator: ValueAnimator? = null

    val isShowing: Boolean get() = capsule != null

    fun show(
        anchor: View,
        target: RectF,
        content: View,
        onDismiss: (() -> Unit)? = null
    ) {
        if (isShowing) return
        dismissListener = onDismiss
        host.visibility = View.VISIBLE
        val hostLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        host.getLocationOnScreen(hostLocation)
        anchor.getLocationOnScreen(anchorLocation)
        val start = RectF(
            (anchorLocation[0] - hostLocation[0]).toFloat(),
            (anchorLocation[1] - hostLocation[1]).toFloat(),
            (anchorLocation[0] - hostLocation[0] + anchor.width).toFloat(),
            (anchorLocation[1] - hostLocation[1] + anchor.height).toFloat()
        )
        val end = RectF(target)
        val pad = 10f * host.resources.displayMetrics.density
        end.left = end.left.coerceIn(pad, max(pad, host.width - end.width() - pad))
        end.top = end.top.coerceIn(pad, max(pad, host.height - end.height() - pad))
        end.right = end.left + end.width()
        end.bottom = end.top + end.height()

        val scrimView = View(host.context).apply {
            setBackgroundColor(Color.parseColor("#8F000000"))
            alpha = 0f
            isClickable = true
            setOnClickListener { dismiss() }
        }
        host.addView(scrimView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        scrim = scrimView

        val density = host.resources.displayMetrics.density
        var expandedApplied = false
        val glass = LiquidGlassView(host.context).apply {
            clipChildren = false
            clipToPadding = false
            setCompact(true)
            setExpanded(false)
            setPadding(
                (20f * density).toInt(), (18f * density).toInt(),
                (20f * density).toInt(), (18f * density).toInt()
            )
            scaleX = 0.96f
            scaleY = 0.96f
        }
        glass.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val params = FrameLayout.LayoutParams(
            start.width().toInt(), start.height().toInt()
        ).apply {
            leftMargin = start.left.toInt()
            topMargin = start.top.toInt()
        }
        host.addView(glass, params)
        capsule = glass

        scrimView.animate().alpha(1f).setDuration(180L).start()
        content.alpha = 0f
        content.scaleY = 0.9f
        content.pivotY = 0f
        content.animate().alpha(1f).scaleY(1f)
            .setStartDelay(120L).setDuration(220L).start()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                val p = (it.animatedValue as Float).coerceIn(0f, 1f)
                val w = start.width() + (end.width() - start.width()) * p
                val h = start.height() + (end.height() - start.height()) * p
                val x = start.left + (end.left - start.left) * p
                val y = start.top + (end.top - start.top) * p
                val lp = glass.layoutParams as FrameLayout.LayoutParams
                lp.width = w.toInt().coerceAtLeast(1)
                lp.height = h.toInt().coerceAtLeast(1)
                lp.leftMargin = x.toInt()
                lp.topMargin = y.toInt()
                glass.layoutParams = lp
                if (p >= 0.22f && !expandedApplied) {
                    expandedApplied = true
                    glass.setExpanded(true)
                    glass.setCompact(false)
                    if (!com.dsha.sagittarius.ui.MotionUtils.reduceMotion(host.context)) {
                        SpringAnimation(glass, DynamicAnimation.SCALE_X, 1f).apply {
                            spring.dampingRatio = 0.42f
                            spring.stiffness = 300f
                            start()
                        }
                        SpringAnimation(glass, DynamicAnimation.SCALE_Y, 1f).apply {
                            spring.dampingRatio = 0.42f
                            spring.stiffness = 300f
                            start()
                        }
                    } else {
                        glass.scaleX = 1f
                        glass.scaleY = 1f
                    }
                }
            }
            start()
        }
    }

    fun dismiss() {
        val glass = capsule ?: return
        val scrimView = scrim
        animator?.cancel()
        dismissAnimator?.cancel()
        glass.setExpanded(false)
        glass.setCompact(true)
        dismissAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 170L
            addUpdateListener {
                val alpha = it.animatedValue as Float
                glass.alpha = alpha
                glass.scaleX = 0.94f + 0.06f * alpha
                glass.scaleY = 0.94f + 0.06f * alpha
                scrimView?.alpha = alpha
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    host.removeView(glass)
                    scrimView?.let { host.removeView(it) }
                    capsule = null
                    scrim = null
                    host.visibility = View.GONE
                    dismissListener?.invoke()
                    dismissListener = null
                }
            })
            start()
        }
    }
}
