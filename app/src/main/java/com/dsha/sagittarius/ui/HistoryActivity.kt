package com.dsha.sagittarius.ui

import android.content.Intent
import android.os.Bundle

/**
 * 旧入口兼容层：历史记账已经迁入主界面的独立 Tab。
 * 外部/旧版本入口仍可通过本 Activity 跳到主界面的历史 Tab。
 */
class HistoryActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_HISTORY)
            }
        )
        finish()
    }
}
