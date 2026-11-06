package com.dsha.sagittarius.ui

import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.R

/**
 * 统一动效工具。
 *
 * - 按压：短促下压，松开用低阻尼 SpringAnimation 轻微过冲。
 * - 入场：只做一次性淡入/上移/微缩放。
 * - 持续动画是否允许：低内存设备或系统“移除动画”时关闭，按压回弹保留。
 */
object MotionUtils {

    private data class PressSprings(
        val scaleX: SpringAnimation,
        val scaleY: SpringAnimation,
        val translationY: SpringAnimation
    ) {
        fun cancel() {
            scaleX.cancel()
            scaleY.cancel()
            translationY.cancel()
        }
    }

    fun reduceMotion(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver
            val animator = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val transition = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            animator == 0f || transition == 0f
        } catch (_: Exception) {
            false
        }
    }

    fun isLowRam(context: Context): Boolean {
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isLowRamDevice
    }

    fun ambientAllowed(context: Context): Boolean =
        !reduceMotion(context) && !isLowRam(context)

    fun press(view: View) {
        if (!view.isEnabled || !view.isAttachedToWindow || reduceMotion(view.context)) return
        cancelPress(view)
        val dip = 2f * view.resources.displayMetrics.density
        val scaleX = SpringAnimation(view, DynamicAnimation.SCALE_X, 0.96f).apply {
            spring.dampingRatio = 0.88f
            spring.stiffness = 620f
            start()
        }
        val scaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 0.96f).apply {
            spring.dampingRatio = 0.88f
            spring.stiffness = 620f
            start()
        }
        val translation = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, dip).apply {
            spring.dampingRatio = 0.9f
            spring.stiffness = 700f
            start()
        }
        view.setTag(R.id.spring_press_tag, PressSprings(scaleX, scaleY, translation))
    }

    fun release(view: View) {
        if (!view.isEnabled) {
            cancelPress(view)
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            return
        }
        if (reduceMotion(view.context) || !view.isAttachedToWindow) {
            cancelPress(view)
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            return
        }
        cancelPress(view)
        val scaleX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = 0.58f
            spring.stiffness = 300f
            start()
        }
        val scaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = 0.58f
            spring.stiffness = 300f
            start()
        }
        val translation = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.dampingRatio = 0.72f
            spring.stiffness = 330f
            start()
        }
        view.setTag(R.id.spring_press_tag, PressSprings(scaleX, scaleY, translation))
    }

    fun cancelPress(view: View) {
        (view.getTag(R.id.spring_press_tag) as? PressSprings)?.cancel()
        view.setTag(R.id.spring_press_tag, null)
    }

    fun animateTabIn(view: View) {
        if (reduceMotion(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val offset = 8f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = offset
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 165L
            interpolator = DecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, offset, 0f).apply {
            duration = 260L
            interpolator = OvershootInterpolator(0.42f)
            start()
        }
    }

    fun animateCardsIn(root: View) {
        if (reduceMotion(root.context)) return
        val cards = mutableListOf<View>()
        collectEntranceViews(root, cards)
        val offset = 7f * root.resources.displayMetrics.density
        cards.take(8).forEachIndexed { index, view ->
            if (!view.isShown) return@forEachIndexed
            view.animate().cancel()
            view.alpha = 0f
            view.scaleX = 0.986f
            view.scaleY = 0.986f
            view.translationY = offset
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(index * 28L)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun collectEntranceViews(view: View, out: MutableList<View>) {
        when (view) {
            is GlassCardView -> {
                out += view
                return
            }
            is GradientActionButton -> {
                out += view
                return
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectEntranceViews(view.getChildAt(i), out)
            }
        }
    }
}
