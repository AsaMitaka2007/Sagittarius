package com.dsha.sagittarius.ui

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

object DateConsoleBinding {

    fun observe(
        fragment: Fragment,
        console: DateConsoleView,
        page: DateConsolePage,
        @StringRes titleRes: Int = 0,
        onState: (DateConsoleState) -> Unit
    ) {
        console.bind(page, titleRes)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    DateConsoleManager.stateOf(page).collect { state ->
                        console.setState(state)
                        onState(state)
                    }
                }
                launch {
                    DateConsoleStyleManager.style.collect { style -> console.setStyle(style) }
                }
            }
        }
    }
}
