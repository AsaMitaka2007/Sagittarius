package com.dsha.sagittarius.agent.ui

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.databinding.ActivitySagittariusBinding
import com.dsha.sagittarius.databinding.PopupPetEvictBinding
import com.dsha.sagittarius.agent.debug.SagittariusLogger
import com.dsha.sagittarius.agent.llm.ProviderRegistry
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.ui.ButtonMotion
import com.dsha.sagittarius.ui.ThemeTokens
import com.dsha.sagittarius.ui.ThemedActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.sin

class SagittariusActivity : ThemedActivity() {

    private lateinit var binding: ActivitySagittariusBinding
    private lateinit var adapter: ChatAdapter
    private val viewModel: SagittariusViewModel by viewModels {
        SagittariusViewModel.Factory(application as SagittariusApp)
    }
    private var lastCount = 0
    private var lastSignature = 0
    private var userNearBottom = true
    private var lastUserProgress: Int? = null
    private var petFloat: ValueAnimator? = null
    private var petFloatBaseY = 0f
    private var petVisibleState = true
    private var petBalanceSupported = false
    private var petBalanceText: String? = null
    private var petTokens = 0L
    private var petTextColor = 0
    private var petScale = 1f
    private var petLocked = false
    private var petLongPressed = false
    private var petDragMoved = false
    private var petDownRawX = 0f
    private var petDownRawY = 0f
    private var petStartTx = 0f
    private var petStartTy = 0f
    private var petLongRunnable: Runnable? = null
    private val petHandler = Handler(Looper.getMainLooper())
    private val petTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySagittariusBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyIconTints()
        adapter = ChatAdapter().apply {
            onItemLongClick = { item -> MessageActionsSheet.newInstance(item.content).show(supportFragmentManager, "message_actions") }
            onToolClick = { item -> ToolDetailsSheet.newInstance(item).show(supportFragmentManager, "tool_detail") }
            onChoiceSelected = { item, choice ->
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.sendChoice(item.id, choice)
            }
        }
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerChat.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showOptionsSheet() }
        binding.btnDebug.setOnClickListener { showDebugLog() }
        binding.btnSettings.setOnClickListener {
            AgentSettingsSheet().show(supportFragmentManager, "agent_settings")
        }
        binding.btnWebSearch.setOnClickListener { viewModel.toggleWebSearch() }
        binding.tvProvider.setOnClickListener { showProviderMenu() }
        binding.tvModel.setOnClickListener { showModelMenu() }
        binding.reasoningSlider.onProgressPreview = { value ->
            // 只更新 UI，不写设置、不触发请求。
            fadeReasoningState(reasoningLabelForProgress(value))
            updateScaleIndicator(value)
        }
        binding.reasoningSlider.onProgressChanged = { value ->
            // 松手后一次性提交，避免拖动时反复写 DataStore。
            val levels = viewModel.supportedEfforts.value
            val effort = effortForProgress(value, levels)
            lastUserProgress = value
            if (effort != viewModel.effort.value) viewModel.selectEffort(effort)
            updateReasoningLabel(effort, forced = false)
            updateScaleIndicator(value)
        }
        binding.btnSend.setOnClickListener {
            if (viewModel.generating.value) {
                viewModel.stop()
                updateSendButton()
            } else {
                sendCurrentInput()
            }
        }
        binding.btnSessions.setOnClickListener { showSessionSwitch() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.btnScrollBottom.setOnClickListener { scrollToBottom() }
        binding.btnPet.setOnClickListener {
            viewModel.setPetVisible(!viewModel.petVisible.value)
        }
        binding.btnPet.setOnLongClickListener {
            PetConfigSheet.newInstance(viewModel.petTextColor.value, viewModel.petScale.value)
                .show(supportFragmentManager, "pet_config")
            true
        }
        setupPetGestures()
        val pressViews = listOf(
            binding.btnBack, binding.btnPet, binding.btnSettings, binding.btnDebug, binding.btnMore,
            binding.btnSessions, binding.btnWebSearch, binding.btnSend, binding.tvProvider,
            binding.tvModel, binding.btnRetry, binding.btnScrollBottom,
            binding.chipExpense, binding.chipDiet, binding.chipTaxi
        )
        pressViews.forEach { ButtonMotion.attach(it) }
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSendButton()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.chipExpense.setOnClickListener { binding.etInput.setText(R.string.agent_example_expense); binding.etInput.setSelection(binding.etInput.text?.length ?: 0) }
        binding.chipDiet.setOnClickListener { binding.etInput.setText(R.string.agent_example_diet); binding.etInput.setSelection(binding.etInput.text?.length ?: 0) }
        binding.chipTaxi.setOnClickListener { binding.etInput.setText(R.string.agent_example_taxi); binding.etInput.setSelection(binding.etInput.text?.length ?: 0) }
        binding.recyclerChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val nearBottom = lm.findLastVisibleItemPosition() >= adapter.itemCount - 2 ||
                    !recyclerView.canScrollVertically(1)
                userNearBottom = nearBottom
                binding.btnScrollBottom.visibility = if (nearBottom) View.GONE else View.VISIBLE
            }
        })
        supportFragmentManager.setFragmentResultListener(
            ApiKeySheet.REQUEST_KEY,
            this
        ) { _, result ->
            if (result.getBoolean(ApiKeySheet.RESULT_SUCCESS)) viewModel.reloadForKeyChange()
        }
        supportFragmentManager.setFragmentResultListener(
            AgentSettingsSheet.REQUEST_KEY,
            this
        ) { _, result ->
            if (result.getBoolean(AgentSettingsSheet.RESULT_SAVED)) viewModel.reloadForKeyChange()
        }
        supportFragmentManager.setFragmentResultListener(
            AgentMoreSheet.REQUEST_KEY,
            this
        ) { _, result ->
            when {
                result.getBoolean(AgentMoreSheet.RESULT_CLEAR) -> viewModel.clearConversation(showWelcome = false)
                result.getBoolean(AgentMoreSheet.RESULT_PERSONA) -> {
                    viewModel.refreshPersonaGreeting()
                    applyIconTints()
                }
            }
        }
        supportFragmentManager.setFragmentResultListener(
            MessageActionsSheet.REQUEST_KEY,
            this
        ) { _, result ->
            val text = result.getString(MessageActionsSheet.RESULT_TEXT).orEmpty()
            when (result.getString(MessageActionsSheet.RESULT_ACTION)) {
                MessageActionsSheet.ACTION_COPY -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sagittarius", text))
                    android.widget.Toast.makeText(this, R.string.agent_copied, android.widget.Toast.LENGTH_SHORT).show()
                }
                MessageActionsSheet.ACTION_CLEAR -> {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.agent_clear_current_confirm)
                        .setNegativeButton(R.string.agent_cancel, null)
                        .setPositiveButton(R.string.agent_confirm) { _, _ -> viewModel.clearConversation(showWelcome = false) }
                        .show()
                }
            }
        }
        supportFragmentManager.setFragmentResultListener(
            PetConfigSheet.REQUEST_KEY_COLOR,
            this
        ) { _, result ->
            val color = result.getInt(PetConfigSheet.RESULT_COLOR_VALUE)
            viewModel.setPetTextColor(color)
        }
        supportFragmentManager.setFragmentResultListener(
            PetConfigSheet.REQUEST_KEY_SCALE_PREVIEW,
            this
        ) { _, result ->
            applyPetScale(result.getInt(PetConfigSheet.RESULT_SCALE, 100), save = false)
        }
        supportFragmentManager.setFragmentResultListener(
            PetConfigSheet.REQUEST_KEY_SCALE_SAVE,
            this
        ) { _, result ->
            val percent = result.getInt(PetConfigSheet.RESULT_SCALE, 100)
            applyPetScale(percent, save = false)
            viewModel.setPetScale(percent)
        }
        supportFragmentManager.setFragmentResultListener(
            SessionSwitchSheet.REQUEST_KEY,
            this
        ) { _, result ->
            when (result.getString(SessionSwitchSheet.RESULT_ACTION)) {
                SessionSwitchSheet.ACTION_NEW -> viewModel.newSession()
                SessionSwitchSheet.ACTION_SWITCH -> viewModel.switchSession(
                    result.getLong(SessionSwitchSheet.RESULT_ID)
                )
                SessionSwitchSheet.ACTION_DELETE -> viewModel.deleteSession(
                    result.getLong(SessionSwitchSheet.RESULT_ID)
                )
            }
        }
        observe()
        updateSendButton()
        if (intent.getBooleanExtra(EXTRA_VOICE, false)) {
            binding.etInput.postDelayed({ openImeVoice() }, 300L)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.messages.collect { messages ->
                    val grew = messages.size > lastCount
                    val newUser = grew && messages.lastOrNull()?.role == ChatItem.Role.USER
                    if (newUser) userNearBottom = true
                    val signature = messages.sumOf {
                        it.content.length + it.reasoning.length + it.toolTraces.size * 31
                    }
                    adapter.submitList(messages)
                    binding.emptyPanel.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerChat.post {
                        if (userNearBottom && (grew || signature != lastSignature)) {
                            scrollToBottom()
                        }
                    }
                    lastCount = messages.size
                    lastSignature = signature
                } }
                launch { viewModel.generating.collect { updateSendButton() } }
                launch { viewModel.provider.collect { id ->
                    val label = if (id == "custom") getString(R.string.agent_custom_provider)
                    else ProviderRegistry.get(id)?.displayName ?: id
                    binding.tvProvider.text = getString(R.string.agent_provider) + " · " + label
                } }
                launch { viewModel.modelId.collect { id ->
                    val label = viewModel.models.value.firstOrNull { it.id == id }?.displayName ?: id
                    binding.tvModel.text = getString(R.string.agent_model) + " · " + label
                } }
                launch { viewModel.petVisible.collect { visible ->
                    applyPetVisibility(visible, animate = petVisibleState != visible)
                } }
                launch { viewModel.totalTokens.collect { total ->
                    petTokens = total
                    renderPetStatus()
                } }
                launch { viewModel.balanceSupported.collect { supported ->
                    petBalanceSupported = supported
                    renderPetStatus()
                } }
                launch { viewModel.balanceText.collect { text ->
                    petBalanceText = text
                    renderPetStatus()
                } }
                launch { viewModel.petTextColor.collect { color ->
                    petTextColor = color
                    renderPetStatus()
                } }
                launch { viewModel.petScale.collect { percent ->
                    if (!petLongPressed) applyPetScale(percent, save = false)
                } }
                launch { viewModel.petX.collect { x ->
                    if (!petLongPressed) applyPetPosition(x, viewModel.petY.value)
                } }
                launch { viewModel.petY.collect { y ->
                    if (!petLongPressed) applyPetPosition(viewModel.petX.value, y)
                } }
                launch { viewModel.petLocked.collect { locked -> petLocked = locked } }
                launch { viewModel.models.collect {
                    val id = viewModel.modelId.value
                    val label = viewModel.models.value.firstOrNull { it.id == id }?.displayName ?: id
                    binding.tvModel.text = getString(R.string.agent_model) + " · " + label
                } }
                launch {
                    viewModel.effort.collect { effort ->
                        if (!viewModel.forcedThinking.value) updateReasoningUi(effort)
                    }
                }
                launch {
                    viewModel.supportedEfforts.collect { updateReasoningUi(viewModel.effort.value) }
                }
                launch {
                    viewModel.forcedThinking.collect { forced ->
                        if (forced) {
                            binding.reasoningSlider.isEnabled = false
                            binding.reasoningSlider.setProgress(100)
                            fadeReasoningState(getString(R.string.agent_forced_thinking))
                            updateScaleIndicator(100)
                        } else {
                            updateReasoningUi(viewModel.effort.value)
                        }
                    }
                }
                launch { viewModel.webSearchEnabled.collect { enabled ->
                    updateWebSearchButton(enabled)
                } }
                launch { viewModel.lastError.collect { error ->
                    binding.btnRetry.visibility = if (error != null && viewModel.canRetry()) View.VISIBLE else View.GONE
                } }
                launch { viewModel.approvalRequests.collect { request -> showToolApproval(request) } }
            }
        }
    }

    private fun updateWebSearchButton(enabled: Boolean) {
        val accent = com.dsha.sagittarius.ui.ButtonAccentManager.current
        binding.btnWebSearch.background = if (enabled) {
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                accent.colors
            ).apply { cornerRadius = 18f * resources.displayMetrics.density }
        } else {
            androidx.core.content.ContextCompat.getDrawable(this, com.dsha.sagittarius.R.drawable.bg_agent_pill)
        }
        binding.btnWebSearch.setTextColor(
            android.content.res.ColorStateList.valueOf(
                if (enabled) accent.textColor else com.dsha.sagittarius.ui.ThemeTokens.of(this).textPrimary
            )
        )
        binding.btnWebSearch.contentDescription = getString(
            if (enabled) R.string.agent_web_on else R.string.agent_web_off
        )
    }

    private fun sendCurrentInput() {
        val text = binding.etInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || viewModel.generating.value) return
        binding.etInput.setText("")
        binding.etInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        viewModel.send(text)
        updateSendButton()
    }

    private fun applyIconTints() {
        val tint = android.content.res.ColorStateList.valueOf(
            com.dsha.sagittarius.ui.ButtonAccentManager.current.startColor
        )
        listOf(binding.btnPet, binding.btnSessions, binding.btnDebug, binding.btnSettings, binding.btnMore).forEach {
            it.imageTintList = tint
        }
    }

    private fun showSessionSwitch() {
        val sessions = viewModel.sessions.value
        SessionSwitchSheet.newInstance(
            ids = sessions.map { it.id }.toLongArray(),
            titles = sessions.map { it.title }.toTypedArray(),
            currentId = viewModel.sessionId.value
        ).show(supportFragmentManager, "session_switch")
    }

    private fun openImeVoice() {
        if (viewModel.generating.value) return
        if (viewModel.models.value.isEmpty()) viewModel.loadModels()
        binding.etInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // 等软键盘与输入法连接就绪后再尝试语音键。
        binding.etInput.postDelayed({
            imm.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT)
            runCatching {
                val ic = binding.etInput.onCreateInputConnection(EditorInfo())
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST)
                ic?.sendKeyEvent(down)
                ic?.sendKeyEvent(up)
                binding.etInput.dispatchKeyEvent(down)
                binding.etInput.dispatchKeyEvent(up)
            }
        }, 280L)
    }

    private fun updateSendButton() {
        val generating = viewModel.generating.value
        binding.btnSend.text = if (generating) "■" else "↑"
        binding.btnSend.contentDescription = getString(
            if (generating) R.string.agent_stop else R.string.agent_send
        )
        binding.btnSend.isEnabled = generating || binding.etInput.text?.toString()?.isNotBlank() == true
        binding.btnSend.alpha = if (binding.btnSend.isEnabled) 1f else 0.45f
        val accent = com.dsha.sagittarius.ui.ButtonAccentManager.current
        binding.btnSend.background = if (binding.btnSend.isEnabled) {
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                accent.colors
            ).apply { cornerRadius = 18f * resources.displayMetrics.density }
        } else {
            androidx.core.content.ContextCompat.getDrawable(this, com.dsha.sagittarius.R.drawable.bg_agent_pill)
        }
        binding.btnSend.setTextColor(
            android.content.res.ColorStateList.valueOf(
                if (binding.btnSend.isEnabled) accent.textColor else com.dsha.sagittarius.ui.ThemeTokens.of(this).textPrimary
            )
        )
        binding.tvProvider.isEnabled = !generating
        binding.tvModel.isEnabled = !generating
        binding.tvProvider.alpha = if (generating) 0.5f else 1f
        binding.tvModel.alpha = if (generating) 0.5f else 1f
        binding.reasoningSlider.isEnabled = !generating && !viewModel.forcedThinking.value &&
            viewModel.supportedEfforts.value.size > 1
        binding.btnRetry.visibility = if (viewModel.lastError.value != null && viewModel.canRetry()) View.VISIBLE else View.GONE
    }

    private fun effortLabel(value: String): String = getString(
        when (value) {
            "low" -> R.string.agent_effort_low
            "high" -> R.string.agent_effort_high
            "max" -> R.string.agent_effort_max
            else -> R.string.agent_effort_off
        }
    )

    private fun updateReasoningUi(effort: String) {
        val levels = viewModel.supportedEfforts.value
        if (viewModel.forcedThinking.value) return
        val pending = lastUserProgress
        val progress = if (pending != null && effortForProgress(pending, levels) == effort) {
            pending
        } else {
            lastUserProgress = null
            effortToProgress(effort, levels)
        }
        binding.reasoningSlider.setProgress(progress)
        binding.reasoningSlider.isEnabled = !viewModel.generating.value && levels.size > 1
        updateReasoningLabel(effort, forced = false)
        updateScaleIndicator(progress)
    }

    private fun effortToProgress(effort: String, levels: List<String>): Int = when (effort) {
        "low" -> 38
        "high" -> 68
        "max" -> 100
        else -> if (levels.size <= 1 && levels.firstOrNull() == "off") 0 else 8
    }

    private fun effortForProgress(value: Int, levels: List<String>): String = when {
        value < 25 -> "off"
        value < 55 -> "low"
        value < 80 -> "high"
        "max" in levels -> "max"
        else -> "high"
    }

    private fun reasoningLabelForProgress(value: Int): String = when {
        value < 25 -> getString(R.string.agent_reasoning_fast)
        value < 55 -> getString(R.string.agent_reasoning_light)
        value < 80 -> getString(R.string.agent_reasoning_balanced)
        else -> getString(R.string.agent_reasoning_deep)
    }

    private fun updateScaleIndicator(value: Int) {
        val tokens = com.dsha.sagittarius.ui.ThemeTokens.of(this)
        val accent = com.dsha.sagittarius.ui.ButtonAccentManager.current
        val labels = listOf(
            (value < 25) to binding.tvScaleOff,
            (value in 25..54) to binding.tvScaleLow,
            (value in 55..79) to binding.tvScaleHigh,
            (value >= 80) to binding.tvScaleMax
        )
        labels.forEach { (selected, view) ->
            view.setTextColor(if (selected) accent.startColor else tokens.textTertiary)
        }
        binding.tvScaleMax.paint.shader = if (value >= 100) {
            android.graphics.LinearGradient(
                0f, 0f, 240f, 0f,
                intArrayOf(
                    android.graphics.Color.parseColor("#FF3B30"),
                    android.graphics.Color.parseColor("#FF8A00"),
                    android.graphics.Color.parseColor("#FFD60A"),
                    android.graphics.Color.parseColor("#34C759"),
                    android.graphics.Color.parseColor("#00E5FF"),
                    android.graphics.Color.parseColor("#4DA8FF"),
                    android.graphics.Color.parseColor("#A86BFF")
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        } else null
        binding.tvScaleMax.invalidate()
    }

    private fun updateReasoningLabel(effort: String, forced: Boolean) {
        val next = when {
            forced -> getString(R.string.agent_forced_thinking)
            effort == "low" -> getString(R.string.agent_reasoning_light)
            effort == "high" -> getString(R.string.agent_reasoning_balanced)
            effort == "max" -> getString(R.string.agent_reasoning_deep)
            else -> getString(R.string.agent_reasoning_fast)
        }
        fadeReasoningState(next)
    }

    private fun fadeReasoningState(next: String) {
        if (binding.tvReasoningState.text == next) return
        binding.tvReasoningState.animate().cancel()
        binding.tvReasoningState.animate().alpha(0f).setDuration(80L).withEndAction {
            binding.tvReasoningState.text = next
            binding.tvReasoningState.animate().alpha(1f).setDuration(160L).start()
        }.start()
    }

    private fun showProviderMenu() {
        val providers = ProviderRegistry.all
        val labels = providers.map { it.displayName } + getString(R.string.agent_custom_provider)
        val ids = providers.map { it.id } + "custom"
        val current = ids.indexOf(viewModel.provider.value).coerceAtLeast(0)
        AnimatedMenuPopup(binding.tvProvider, labels, current) { index ->
            val target = ids[index]
            if (target != viewModel.provider.value) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.agent_provider_switch_confirm)
                    .setNegativeButton(R.string.agent_cancel, null)
                    .setPositiveButton(R.string.agent_confirm) { _, _ ->
                        viewModel.selectProvider(target)
                        lifecycleScope.launch {
                            val app = application as com.dsha.sagittarius.SagittariusApp
                            val key = withContext(Dispatchers.IO) { app.apiKeyStore.load(target) }
                            if (key.isNullOrBlank()) {
                                AgentSettingsSheet().show(supportFragmentManager, "agent_settings")
                            }
                        }
                    }
                    .show()
            }
        }.show()
    }

    private fun showModelMenu() {
        val models = viewModel.models.value
        val labels = models.map { it.displayName }
        val current = models.indexOfFirst { it.id == viewModel.modelId.value }.coerceAtLeast(0)
        AnimatedMenuPopup(binding.tvModel, labels, current) { which ->
            val target = models.getOrNull(which) ?: return@AnimatedMenuPopup
            if (target.id != viewModel.modelId.value) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.agent_model_confirm)
                    .setNegativeButton(R.string.agent_cancel, null)
                    .setPositiveButton(R.string.agent_confirm) { _, _ -> viewModel.selectModel(target.id) }
                    .show()
            }
        }.show()
    }

    private fun showToolApproval(request: SagittariusViewModel.ToolApprovalRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_permission_required)
            .setMessage(getString(R.string.agent_permission_message, request.toolName, request.args.take(300)))
            .setCancelable(false)
            .setNegativeButton(R.string.agent_deny) { _, _ -> viewModel.respondApproval(request.id, false) }
            .setPositiveButton(R.string.agent_allow) { _, _ -> viewModel.respondApproval(request.id, true) }
            .show()
    }

    private fun showOptionsSheet() {
        AgentMoreSheet.newInstance(
            count = viewModel.messages.value.size,
            prompt = viewModel.tokenUsage.value.promptTokens,
            completion = viewModel.tokenUsage.value.completionTokens,
            model = viewModel.modelId.value
        ).show(supportFragmentManager, "agent_more")
    }

    private fun showDebugLog() {
        val logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
            text = SagittariusLogger.snapshot().joinToString("\n\n").ifBlank {
                getString(R.string.agent_debug_empty)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_debug)
            .setView(ScrollView(this).apply { addView(logView) })
            .setPositiveButton(R.string.agent_debug_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sagittarius logs", logView.text))
                android.widget.Toast.makeText(this, R.string.agent_debug_copied, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.agent_debug_clear) { _, _ -> SagittariusLogger.clear() }
            .setNegativeButton(R.string.agent_debug_close, null)
            .show()
    }

    private fun scrollToBottom() {
        if (adapter.itemCount == 0) return
        binding.recyclerChat.post {
            binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
            binding.btnScrollBottom.visibility = View.GONE
            userNearBottom = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_VOICE, false)) {
            binding.etInput.postDelayed({ openImeVoice() }, 250L)
        }
    }

    private fun applyPetVisibility(visible: Boolean, animate: Boolean) {
        petVisibleState = visible
        binding.btnPet.alpha = if (visible) 1f else 0.45f
        if (visible) {
            binding.petContainer.visibility = View.VISIBLE
            binding.petContainer.post {
                applyPetPosition(viewModel.petX.value, viewModel.petY.value)
                applyPetScale(viewModel.petScale.value, save = false)
            }
            if (animate && !com.dsha.sagittarius.ui.MotionUtils.reduceMotion(this)) {
                binding.petContainer.alpha = 0f
                binding.petContainer.scaleX = petScale * 0.82f
                binding.petContainer.scaleY = petScale * 0.82f
                binding.petContainer.animate().alpha(1f).scaleX(petScale).scaleY(petScale)
                    .setInterpolator(OvershootInterpolator(2f)).setDuration(260L).start()
            } else {
                binding.petContainer.alpha = 1f
                binding.petContainer.scaleX = petScale
                binding.petContainer.scaleY = petScale
            }
            startPetFloat()
        } else {
            stopPetFloat()
            if (animate && !com.dsha.sagittarius.ui.MotionUtils.reduceMotion(this)) {
                binding.petContainer.animate().alpha(0f).scaleX(petScale * 0.82f).scaleY(petScale * 0.82f)
                    .setDuration(180L).withEndAction {
                        binding.petContainer.visibility = View.GONE
                        binding.petContainer.alpha = 1f
                    }.start()
            } else {
                binding.petContainer.visibility = View.GONE
                binding.petContainer.alpha = 1f
            }
        }
        renderPetStatus()
    }

    private fun setupPetGestures() {
        binding.petContainer.post {
            applyPetScale(viewModel.petScale.value, save = false)
            val parent = binding.petContainer.parent as? View
            val defaultY = ((parent?.height ?: 0) - binding.petContainer.height * petScale - 96f * resources.displayMetrics.density)
                .coerceAtLeast(0f)
            val x = if (viewModel.petPositionSet.value) viewModel.petX.value else 0f
            val y = if (viewModel.petPositionSet.value) viewModel.petY.value else defaultY
            applyPetPosition(x, y)
            if (!viewModel.petPositionSet.value) viewModel.setPetPosition(x, y)
        }
        binding.petContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopPetFloat()
                    binding.petContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    petDownRawX = event.rawX
                    petDownRawY = event.rawY
                    petStartTx = binding.petContainer.translationX
                    petStartTy = binding.petContainer.translationY
                    petLongPressed = false
                    petDragMoved = false
                    petLongRunnable?.let(petHandler::removeCallbacks)
                    petLongRunnable = Runnable {
                        if (binding.petContainer.visibility == View.VISIBLE) {
                            petLongPressed = true
                            binding.petContainer.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            binding.petContainer.animate()
                                .scaleX(petScale * 1.06f).scaleY(petScale * 1.06f)
                                .setDuration(120L).start()
                            binding.petContainer.elevation = 12f * resources.displayMetrics.density
                        }
                    }.also { petHandler.postDelayed(it, 500L) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    binding.petContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    val dx = event.rawX - petDownRawX
                    val dy = event.rawY - petDownRawY
                    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                    if (!petLocked && distance > 10f * resources.displayMetrics.density) {
                        petDragMoved = true
                        petLongRunnable?.let(petHandler::removeCallbacks)
                        if (!petLongPressed) {
                            petLongPressed = true
                            binding.petContainer.animate()
                                .scaleX(petScale * 1.06f).scaleY(petScale * 1.06f)
                                .setDuration(100L).start()
                            binding.petContainer.elevation = 10f * resources.displayMetrics.density
                        }
                        val parent = binding.petContainer.parent as? View ?: return@setOnTouchListener true
                        val maxX = (parent.width - binding.petContainer.width * petScale).toFloat()
                        val maxY = (parent.height - binding.petContainer.height * petScale).toFloat()
                        binding.petContainer.translationX = (petStartTx + dx).coerceIn(0f, maxX.coerceAtLeast(0f))
                        binding.petContainer.translationY = (petStartTy + dy).coerceIn(0f, maxY.coerceAtLeast(0f))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    petLongRunnable?.let(petHandler::removeCallbacks)
                    val wasLong = petLongPressed
                    val wasDrag = petDragMoved
                    petLongPressed = false
                    binding.petContainer.elevation = 0f
                    binding.petContainer.animate().scaleX(petScale).scaleY(petScale).setDuration(160L).start()
                    if (wasDrag || wasLong) {
                        if (!petLocked && wasDrag) {
                            viewModel.setPetPosition(binding.petContainer.translationX, binding.petContainer.translationY)
                        }
                        showEvictPopup(event.rawX, event.rawY)
                    } else {
                        petReaction()
                    }
                    if (petVisibleState) startPetFloat()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    petLongRunnable?.let(petHandler::removeCallbacks)
                    petLongPressed = false
                    binding.petContainer.elevation = 0f
                    binding.petContainer.animate().scaleX(petScale).scaleY(petScale).setDuration(120L).start()
                    if (petVisibleState) startPetFloat()
                    true
                }
                else -> false
            }
        }
    }

    private fun petReaction() {
        viewModel.refreshBalance()
        binding.tvPetBalance.animate().alpha(0.35f).setDuration(120L).withEndAction {
            binding.tvPetBalance.animate().alpha(1f).setDuration(300L).start()
        }.start()
        binding.petContainer.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        binding.petImage.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120L)
            .withEndAction {
                binding.petImage.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2.5f)).setDuration(280L).start()
            }.start()
        val lines = resources.getStringArray(R.array.agent_pet_lines)
        if (lines.isNotEmpty()) {
            android.widget.Toast.makeText(this, lines.random(), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEvictPopup(rawX: Float, rawY: Float) {
        val popupBinding = PopupPetEvictBinding.inflate(layoutInflater)
        val popup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 10f * resources.displayMetrics.density
        popup.isOutsideTouchable = true
        popupBinding.btnEvict.setOnClickListener {
            viewModel.setPetVisible(false)
            popup.dismiss()
        }
        popupBinding.btnLock.setImageResource(
            if (petLocked) com.dsha.sagittarius.R.drawable.ic_lock_open_silhouette
            else com.dsha.sagittarius.R.drawable.ic_lock_silhouette
        )
        popupBinding.btnLock.setOnClickListener {
            val next = !petLocked
            petLocked = next
            viewModel.setPetLocked(next)
            popupBinding.btnLock.setImageResource(
                if (next) com.dsha.sagittarius.R.drawable.ic_lock_open_silhouette
                else com.dsha.sagittarius.R.drawable.ic_lock_silhouette
            )
            android.widget.Toast.makeText(
                this,
                if (next) R.string.agent_pet_locked_on else R.string.agent_pet_locked_off,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val x = (rawX.toInt() - 40).coerceIn(16, (screenW - 180).coerceAtLeast(16))
        val y = (rawY.toInt() + 12).coerceIn(16, (screenH - 100).coerceAtLeast(16))
        popup.showAtLocation(binding.root, Gravity.TOP or Gravity.START, x, y)
    }

    private fun applyPetPosition(x: Float, y: Float) {
        val parent = binding.petContainer.parent as? View ?: return
        if (binding.petContainer.width == 0 || parent.width == 0) return
        stopPetFloat()
        val maxX = (parent.width - binding.petContainer.width * petScale).toFloat()
        val maxY = (parent.height - binding.petContainer.height * petScale).toFloat()
        binding.petContainer.translationX = x.coerceIn(0f, maxX.coerceAtLeast(0f))
        binding.petContainer.translationY = y.coerceIn(0f, maxY.coerceAtLeast(0f))
        if (petVisibleState) startPetFloat()
    }

    private fun applyPetScale(percent: Int, save: Boolean) {
        petScale = (percent.coerceIn(50, 200)) / 100f
        binding.petContainer.pivotX = 0f
        binding.petContainer.pivotY = 0f
        binding.petContainer.scaleX = petScale
        binding.petContainer.scaleY = petScale
        if (save) viewModel.setPetScale(percent)
    }

    private fun startPetFloat() {
        stopPetFloat()
        if (!petVisibleState || com.dsha.sagittarius.ui.MotionUtils.reduceMotion(this)) return
        petFloatBaseY = binding.petContainer.translationY
        petFloat = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val phase = it.animatedValue as Float
                binding.petContainer.translationY = petFloatBaseY +
                    sin(phase * Math.PI * 2).toFloat() * 3f * resources.displayMetrics.density
            }
            start()
        }
    }

    private fun stopPetFloat() {
        petFloat?.cancel()
        petFloat = null
    }

    private fun renderPetStatus() {
        val accent = if (petTextColor == 0) ThemeTokens.of(this).accent else petTextColor
        binding.tvPetBalance.setTextColor(accent)
        binding.tvPetTokens.setTextColor(accent)
        binding.tvPetBalance.visibility = if (petBalanceSupported) View.VISIBLE else View.GONE
        binding.tvPetBalance.text = getString(R.string.agent_pet_balance, petBalanceText ?: "--")
        val tokens = when {
            petTokens >= 1_000_000L -> "%.1f M".format(petTokens / 1_000_000.0)
            petTokens >= 1_000L -> "%.1f K".format(petTokens / 1_000.0)
            else -> petTokens.toString()
        }
        binding.tvPetTokens.text = getString(R.string.agent_pet_tokens, tokens)
    }

    override fun onResume() {
        super.onResume()
        if (petVisibleState) startPetFloat()
    }

    override fun onPause() {
        stopPetFloat()
        super.onPause()
    }

    override fun onDestroy() {
        petLongRunnable?.let(petHandler::removeCallbacks)
        stopPetFloat()
        if (isFinishing) viewModel.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VOICE = "sagittarius_open_voice"
    }
}
