package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.max

/**
 * 以按钮为原点的墨水胶囊展开层。
 *
 * 展开/收起使用纯黑圆角容器，边缘由 ParticleEdgeView 喷出与首页入口同款粒子；
 * 不再使用玻璃、流光或外发光。
 */
class CapsuleOverlay(
    private val host: FrameLayout,
    private val tokens: ThemeTokens
) {

    private var scrim: View? = null
    private var capsule: InkCapsuleView? = null
    private var particles: ParticleEdgeView? = null
    private var animator: ValueAnimator? = null
    private var dismissListener: (() -> Unit)? = null
    private var dismissAnimator: ValueAnimator? = null
    private var generation = 0

    val isShowing: Boolean get() = capsule != null

    fun show(
        anchor: View,
        target: RectF,
        content: View,
        onDismiss: (() -> Unit)? = null
    ) {
        if (isShowing) dismissNow()
        generation++
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
        val ink = InkCapsuleView(host.context).apply {
            clipChildren = true
            cornerRadius = start.height() / 2f
            setPadding(
                (22f * density).toInt(), (20f * density).toInt(),
                (22f * density).toInt(), (20f * density).toInt()
            )
            alpha = 0.98f
        }
        AgentTypography.apply(content)
        ink.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val params = FrameLayout.LayoutParams(
            start.width().toInt(), start.height().toInt()
        ).apply {
            leftMargin = start.left.toInt()
            topMargin = start.top.toInt()
        }
        host.addView(ink, params)
        capsule = ink

        val particleView = ParticleEdgeView(host.context)
        host.addView(particleView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        particles = particleView
        particleView.updateBounds(
            RectF(start.left, start.top, start.right, start.bottom),
            start.height() / 2f
        )
        particleView.burst()

        scrimView.animate().alpha(1f).setDuration(180L).start()
        content.alpha = 0f
        content.scaleY = 0.9f
        content.pivotY = 0f
        content.animate().alpha(1f).scaleY(1f)
            .setStartDelay(110L).setDuration(220L).start()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = DecelerateInterpolator(1.1f)
            addUpdateListener {
                val p = (it.animatedValue as Float).coerceIn(0f, 1f)
                val w = start.width() + (end.width() - start.width()) * p
                val h = start.height() + (end.height() - start.height()) * p
                val x = start.left + (end.left - start.left) * p
                val y = start.top + (end.top - start.top) * p
                val lp = ink.layoutParams as FrameLayout.LayoutParams
                lp.width = w.toInt().coerceAtLeast(1)
                lp.height = h.toInt().coerceAtLeast(1)
                lp.leftMargin = x.toInt()
                lp.topMargin = y.toInt()
                ink.layoutParams = lp
                val radius = start.height() / 2f + (24f * density - start.height() / 2f) * p
                ink.cornerRadius = radius
                particleView.updateBounds(RectF(x, y, x + w, y + h), radius)
            }
            start()
        }
    }

    fun dismiss() {
        val ink = capsule ?: return
        val scrimView = scrim
        animator?.cancel()
        particles?.burst()
        dismissAnimator?.cancel()
        dismissAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 170L
            addUpdateListener {
                val alpha = it.animatedValue as Float
                ink.alpha = alpha
                ink.scaleX = 0.94f + 0.06f * alpha
                ink.scaleY = 0.94f + 0.06f * alpha
                scrimView?.alpha = alpha
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    host.removeView(ink)
                    scrimView?.let { host.removeView(it) }
                    capsule = null
                    scrim = null
                    val pv = particles
                    val currentGeneration = generation
                    if (pv != null) {
                        // 让收起粒子完整播放；若期间重新展开，交由新实例接管
                        pv.postDelayed({
                            host.removeView(pv)
                            if (particles === pv) particles = null
                            if (capsule == null && generation == currentGeneration) {
                                host.visibility = View.GONE
                                dismissListener?.invoke()
                                dismissListener = null
                            }
                        }, 560L)
                    } else if (capsule == null && generation == currentGeneration) {
                        host.visibility = View.GONE
                        dismissListener?.invoke()
                        dismissListener = null
                    }
                }
            })
            start()
        }
    }

    /** 立即清空，用于在会话/推理胶囊之间切换，避免旧遮罩残留。 */
    fun dismissNow() {
        generation++
        animator?.cancel()
        dismissAnimator?.cancel()
        capsule?.let { host.removeView(it) }
        particles?.let { host.removeView(it) }
        scrim?.let { host.removeView(it) }
        capsule = null
        particles = null
        scrim = null
        dismissListener = null
        host.visibility = View.GONE
    }
}
