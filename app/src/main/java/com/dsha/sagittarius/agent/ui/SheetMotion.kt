package com.dsha.sagittarius.agent.ui

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.ui.MotionUtils

object SheetMotion {
    fun animateIn(view: View) {
        if (MotionUtils.reduceMotion(view.context)) return
        view.alpha = 0f
        view.scaleY = 0.92f
        view.pivotY = 0f
        SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = 0.55f
            spring.stiffness = 380f
            start()
        }
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            start()
        }
    }
}
