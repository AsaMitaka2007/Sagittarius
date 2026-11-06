package com.dsha.sagittarius.ui

import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun edge(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun reject(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
