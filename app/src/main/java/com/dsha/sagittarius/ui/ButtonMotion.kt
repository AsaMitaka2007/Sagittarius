package com.dsha.sagittarius.ui

import android.animation.ObjectAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** 全局按钮按压反馈：弹性缩放 + 涟漪 + 触觉。 */
object ButtonMotion {

    fun attach(view: View, haptic: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 23 && view.foreground == null) {
            val attrs = view.context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
            runCatching { view.foreground = attrs.getDrawable(0) }
            attrs.recycle()
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.9f).scaleY(0.9f)
                        .setDuration(80L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    if (haptic) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(280L)
                        .setInterpolator(OvershootInterpolator(2.4f))
                        .start()
                }
            }
            false
        }
    }

    /** FAB：按下缩小，松开 Spring 回弹，并带一次衰减旋转。 */
    fun attachFab(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70L).start()
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    SpringAnimation(v, DynamicAnimation.SCALE_X, 1f).apply {
                        spring = SpringForce(1f).setDampingRatio(0.42f)
                            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        start()
                    }
                    SpringAnimation(v, DynamicAnimation.SCALE_Y, 1f).apply {
                        spring = SpringForce(1f).setDampingRatio(0.42f)
                            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        start()
                    }
                    ObjectAnimator.ofFloat(v, "rotation", 0f, 4.5f, -3.5f, 2f, 0f).apply {
                        duration = 360L
                        interpolator = OvershootInterpolator(1.4f)
                        start()
                    }
                }
            }
            false
        }
    }
}
