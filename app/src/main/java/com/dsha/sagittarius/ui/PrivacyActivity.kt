package com.dsha.sagittarius.ui

import android.os.Bundle
import com.dsha.sagittarius.databinding.ActivityPrivacyBinding

class PrivacyActivity : ThemedActivity() {

    private lateinit var binding: ActivityPrivacyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
    }

    private var entrancePlayed = false

    override fun onResume() {
        super.onResume()
        if (entrancePlayed) return
        entrancePlayed = true
        binding.root.post { MotionUtils.animateCardsIn(binding.root) }
    }
}
