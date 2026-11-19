package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.max

/**
 * 以某个按钮为原点的胶囊展开层。
 *
 * 负责遮罩、起止矩形、圆角形变与内容淡入；具体内容与目标尺寸由调用方决定。
 */
class CapsuleOverlay(
    private val host: FrameLayout,
    private val tokens: ThemeTokens
) {

    private var scrim: View? = null
    private var capsule: MorphCapsuleView? = null
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
        val pad = (8f * host.resources.displayMetrics.density)
        end.left = end.left.coerceIn(pad, max(pad, host.width - end.width() - pad))
        end.top = end.top.coerceIn(pad, max(pad, host.height - end.height() - pad))
        end.right = end.left + end.width()
        end.bottom = end.top + end.height()

        val scrimView = View(host.context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            isClickable = true
            setOnClickListener { dismiss() }
        }
        host.addView(scrimView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        scrim = scrimView

        val capsuleView = MorphCapsuleView(host.context).apply {
            applyTheme(tokens)
            cornerRadius = start.height() / 2f
            clipChildren = true
            isClickable = true
            setOnClickListener { }
        }
        val density = host.resources.displayMetrics.density
        content.setPadding(
            (18f * density).toInt(), (16f * density).toInt(),
            (18f * density).toInt(), (16f * density).toInt()
        )
        capsuleView.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val params = FrameLayout.LayoutParams(
            start.width().toInt(), start.height().toInt()
        ).apply {
            leftMargin = start.left.toInt()
            topMargin = start.top.toInt()
        }
        host.addView(capsuleView, params)
        capsule = capsuleView

        scrimView.animate().alpha(1f).setDuration(180L).start()
        content.alpha = 0f
        content.scaleX = 0.96f
        content.scaleY = 0.96f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(110L).setDuration(220L).start()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = OvershootInterpolator(0.75f)
            addUpdateListener {
                val p = (it.animatedValue as Float).coerceIn(0f, 1f)
                val w = start.width() + (end.width() - start.width()) * p
                val h = start.height() + (end.height() - start.height()) * p
                val x = start.left + (end.left - start.left) * p
                val y = start.top + (end.top - start.top) * p
                val lp = capsuleView.layoutParams as FrameLayout.LayoutParams
                lp.width = w.toInt().coerceAtLeast(1)
                lp.height = h.toInt().coerceAtLeast(1)
                lp.leftMargin = x.toInt()
                lp.topMargin = y.toInt()
                capsuleView.layoutParams = lp
                capsuleView.cornerRadius = start.height() / 2f +
                    (24f * density - start.height() / 2f) * p
            }
            start()
        }
    }

    fun dismiss() {
        val capsuleView = capsule ?: return
        val scrimView = scrim
        animator?.cancel()
        dismissAnimator?.cancel()
        dismissAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180L
            addUpdateListener {
                val alpha = it.animatedValue as Float
                capsuleView.alpha = alpha
                capsuleView.scaleX = 0.96f + 0.04f * alpha
                capsuleView.scaleY = 0.96f + 0.04f * alpha
                scrimView?.alpha = alpha
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    host.removeView(capsuleView)
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
