package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.databinding.ActivitySagittariusBinding
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.ThemeTokens
import com.dsha.sagittarius.ui.ThemedActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * AI 管家宿主：对话 / 配置 / 终端三选项卡。
 *
 * 生成任务、工具审批与会话切换都收敛在这里的共享 ViewModel，
 * 切 Tab 不会中断请求。桌宠已移除，预算悬浮胶囊不受影响。
 */
class SagittariusActivity : ThemedActivity() {

    private lateinit var binding: ActivitySagittariusBinding
    internal val viewModel: SagittariusViewModel by viewModels {
        SagittariusViewModel.Factory(application as SagittariusApp)
    }

    private val tokens by lazy { ThemeTokens.of(this) }
    private var tabAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySagittariusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
        setupTabs()
        setupEntryReveal(savedInstanceState)
        val startTab = intent.getIntExtra(EXTRA_START_TAB, 0)
        if (startTab != 0) {
            binding.viewPager.post { selectTab(startTab) }
        }
        observeApprovals()
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this) {
            if (binding.viewPager.currentItem != 0) selectTab(0) else finish()
        }
        handleVoiceIntent(intent)
    }

    private fun setupPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> AgentChatFragment()
                1 -> AgentConfigFragment()
                else -> AgentTerminalFragment()
            }
        }
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabs(position, animate = true)
                }
            }
        )
    }

    private fun setupTabs() {
        binding.btnBack.setOnClickListener { finish() }
        binding.tabPill.background = GradientDrawable().apply {
            cornerRadius = 17f * resources.displayMetrics.density
            setColor(ColorUtils.setAlphaComponent(ButtonAccentManager.current.startColor, 28))
        }
        binding.tabChat.setOnClickListener { selectTab(0) }
        binding.tabConfig.setOnClickListener { selectTab(1) }
        binding.tabTerminal.setOnClickListener { selectTab(2) }
        binding.viewPager.post { updateTabs(0, animate = false) }
    }

    fun selectTab(index: Int) {
        val safe = index.coerceIn(0, 2)
        if (binding.viewPager.currentItem != safe) {
            binding.viewPager.setCurrentItem(safe, true)
        } else {
            updateTabs(safe, animate = true)
        }
    }

    private fun updateTabs(index: Int, animate: Boolean) {
        val tabs = listOf(binding.tabChat, binding.tabConfig, binding.tabTerminal)
        tabs.forEachIndexed { i, tab ->
            val selected = i == index
            tab.setTextColor(if (selected) tokens.textPrimary else tokens.textTertiary)
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        val target = tabs[index.coerceIn(0, 2)]
        if (target.width == 0) return
        val margin = (4f * resources.displayMetrics.density).toInt()
        val targetWidth = (target.width - margin * 2).coerceAtLeast(1)
        val targetX = target.x + margin
        val pill = binding.tabPill
        tabAnimator?.cancel()
        if (!animate) {
            val lp = pill.layoutParams
            lp.width = targetWidth
            pill.layoutParams = lp
            pill.x = targetX
            return
        }
        val startX = pill.x
        val startWidth = pill.width.coerceAtLeast(1)
        tabAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            addUpdateListener {
                val p = it.animatedValue as Float
                val lp = pill.layoutParams
                lp.width = (startWidth + (targetWidth - startWidth) * p).toInt()
                pill.layoutParams = lp
                pill.x = startX + (targetX - startX) * p
            }
            start()
        }
    }

    private fun setupEntryReveal(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val ox = intent.getFloatExtra(EXTRA_REVEAL_X, -1f)
        val oy = intent.getFloatExtra(EXTRA_REVEAL_Y, -1f)
        if (ox < 0f || oy < 0f) return
        val radius = intent.getFloatExtra(
            EXTRA_REVEAL_RADIUS, 32f * resources.displayMetrics.density
        )
        binding.entryReveal.post {
            val rootLocation = IntArray(2)
            binding.root.getLocationOnScreen(rootLocation)
            binding.entryReveal.start(ox - rootLocation[0], oy - rootLocation[1], radius)
        }
    }

    private fun observeApprovals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.approvalRequests.collect { request -> showToolApproval(request) }
            }
        }
    }

    private fun showToolApproval(request: SagittariusViewModel.ToolApprovalRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_permission_required)
            .setMessage(
                getString(
                    R.string.agent_permission_message,
                    request.toolName,
                    request.args.take(300)
                )
            )
            .setCancelable(false)
            .setNegativeButton(R.string.agent_deny) { _, _ ->
                viewModel.respondApproval(request.id, false)
            }
            .setPositiveButton(R.string.agent_allow) { _, _ ->
                viewModel.respondApproval(request.id, true)
            }
            .show()
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_VOICE, false) != true) return
        binding.viewPager.post {
            selectTab(0)
            supportFragmentManager.setFragmentResult(KEY_VOICE, Bundle())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) viewModel.stop()
        tabAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VOICE = "sagittarius_open_voice"
        const val EXTRA_START_TAB = "sagittarius_start_tab"
        const val KEY_VOICE = "sagittarius_voice_request"
        const val EXTRA_REVEAL_X = "sagittarius_reveal_x"
        const val EXTRA_REVEAL_Y = "sagittarius_reveal_y"
        const val EXTRA_REVEAL_RADIUS = "sagittarius_reveal_radius"
    }
}
