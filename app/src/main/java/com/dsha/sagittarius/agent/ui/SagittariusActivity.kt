package com.dsha.sagittarius.agent.ui

import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.databinding.ActivitySagittariusBinding
import com.dsha.sagittarius.ui.ThemedActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * AI 管家宿主：对话 / 配置 / 终端三页，左右滑动切换。
 *
 * 不设顶部标题、返回按钮或 tab 按钮；退出依赖系统返回手势。
 * 生成任务、工具审批与会话切换收敛在共享 ViewModel。
 */
class SagittariusActivity : ThemedActivity() {

    private lateinit var binding: ActivitySagittariusBinding
    internal val viewModel: SagittariusViewModel by viewModels {
        SagittariusViewModel.Factory(application as SagittariusApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySagittariusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
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

    override fun themeResFor(mode: com.dsha.sagittarius.theme.ThemeMode): Int =
        R.style.Theme_Sagittarius_Agent

    override fun followsThemeChanges(): Boolean = false

    override fun allowsGlassBlur(): Boolean = false

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
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.pageIndicator.setCurrent(position, animate = true)
                }
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                    binding.pageIndicator.setScrollProgress(position + positionOffset)
                }
            }
        )
        binding.viewPager.post { binding.pageIndicator.setCurrent(0, animate = false) }
    }

    fun selectTab(index: Int) {
        val safe = index.coerceIn(0, 2)
        if (binding.viewPager.currentItem != safe) {
            binding.viewPager.setCurrentItem(safe, true)
        }
    }

    private fun setupEntryReveal(savedInstanceState: Bundle?) {
        val ox = intent.getFloatExtra(EXTRA_REVEAL_X, -1f)
        val oy = intent.getFloatExtra(EXTRA_REVEAL_Y, -1f)
        if (savedInstanceState != null || ox < 0f || oy < 0f) {
            binding.contentRoot.clipToOutline = false
            binding.contentRoot.outlineProvider = null
            return
        }
        val radius = intent.getFloatExtra(
            EXTRA_REVEAL_RADIUS, 32f * resources.displayMetrics.density
        )
        var cx = ox
        var cy = oy
        var revealRadius = radius
        // 圆形裁剪 AI 页面：圆外保持透明，露出首页原选项卡。
        // 先同步设好裁剪，避免首帧闪出整页。
        binding.contentRoot.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = revealRadius.toInt().coerceAtLeast(1)
                outline.setOval(
                    (cx - r).toInt(),
                    (cy - r).toInt(),
                    (cx + r).toInt(),
                    (cy + r).toInt()
                )
            }
        }
        binding.contentRoot.clipToOutline = true
        binding.contentRoot.invalidateOutline()
        binding.entryReveal.post {
            val rootLocation = IntArray(2)
            binding.root.getLocationOnScreen(rootLocation)
            cx = ox - rootLocation[0]
            cy = oy - rootLocation[1]
            binding.contentRoot.invalidateOutline()
            binding.entryReveal.setOnReveal { r ->
                revealRadius = r
                binding.contentRoot.invalidateOutline()
            }
            binding.entryReveal.start(
                cx,
                cy,
                radius,
                onFilled = {
                    binding.contentRoot.clipToOutline = false
                    binding.contentRoot.outlineProvider = null
                    binding.contentRoot.invalidate()
                }
            )
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
