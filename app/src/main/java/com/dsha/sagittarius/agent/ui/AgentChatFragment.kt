package com.dsha.sagittarius.agent.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.databinding.FragmentAgentChatBinding
import com.dsha.sagittarius.ui.ThemeTokens
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AgentChatFragment : Fragment() {

    private var _binding: FragmentAgentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel get() = (requireActivity() as SagittariusActivity).viewModel
    private lateinit var adapter: ChatAdapter

    private var lastCount = 0
    private var lastSignature = 0
    private var userNearBottom = true
    private var capsuleOverlay: CapsuleOverlay? = null
    private var activeCapsule: CapsuleKind? = null
    private var sessionAdapter: SessionAdapter? = null
    private var sessionActionRow: LinearLayout? = null
    private var sessionSelectAll: TextView? = null
    private var sessionDelete: TextView? = null
    private var sessionSelectToggle: TextView? = null
    private val pendingAttachments = mutableListOf<ChatAttachment>()
    private val pickAttachments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> loadAttachments(uris) }

    private enum class CapsuleKind { SESSION, REASONING }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ChatAdapter().apply {
            onItemLongClick = { item ->
                MessageActionsSheet.newInstance(item.content)
                    .show(parentFragmentManager, "message_actions")
            }
            onToolClick = { item ->
                ToolDetailsSheet.newInstance(item).show(parentFragmentManager, "tool_detail")
            }
            onChoiceSelected = { item, choice ->
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.sendChoice(item.id, choice)
            }
        }
        binding.recyclerChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerChat.adapter = adapter

        binding.btnSend.setOnClickListener {
            if (viewModel.generating.value) {
                viewModel.stop()
                updateSendButton()
            } else {
                sendCurrentInput()
            }
        }
        binding.btnSessions.setOnClickListener { showSessionCapsule() }
        binding.btnWebSearch.setOnClickListener { viewModel.toggleWebSearch() }
        binding.btnAttach.setOnClickListener {
            val remaining = MAX_ATTACHMENTS - pendingAttachments.size
            if (remaining <= 0) {
                Toast.makeText(requireContext(), R.string.agent_attachment_limit, Toast.LENGTH_SHORT).show()
            } else {
                pickAttachments.launch(arrayOf("*/*"))
            }
        }
        binding.btnReasoning.setOnClickListener { showReasoningCapsule() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.btnScrollBottom.setOnClickListener { scrollToBottom() }

        binding.etInput.doAfterTextChanged { updateSendButton() }
        binding.chipExpense.setOnClickListener { fillInput(R.string.agent_example_expense) }
        binding.chipDiet.setOnClickListener { fillInput(R.string.agent_example_diet) }
        binding.chipTaxi.setOnClickListener { fillInput(R.string.agent_example_taxi) }

        binding.recyclerChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val nearBottom = lm.findLastVisibleItemPosition() >= adapter.itemCount - 2 ||
                    !recyclerView.canScrollVertically(1)
                userNearBottom = nearBottom
                binding.btnScrollBottom.visibility = if (nearBottom) View.GONE else View.VISIBLE
            }
        })

        parentFragmentManager.setFragmentResultListener(
            MessageActionsSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val text = result.getString(MessageActionsSheet.RESULT_TEXT).orEmpty()
            when (result.getString(MessageActionsSheet.RESULT_ACTION)) {
                MessageActionsSheet.ACTION_COPY -> {
                    val clipboard = requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sagittarius", text))
                    Toast.makeText(requireContext(), R.string.agent_copied, Toast.LENGTH_SHORT).show()
                }
                MessageActionsSheet.ACTION_CLEAR -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.agent_clear_current_confirm)
                        .setNegativeButton(R.string.agent_cancel, null)
                        .setPositiveButton(R.string.agent_confirm) { _, _ ->
                            viewModel.clearConversation(showWelcome = false)
                        }
                        .show()
                }
            }
        }
        parentFragmentManager.setFragmentResultListener(
            SagittariusActivity.KEY_VOICE,
            viewLifecycleOwner
        ) { _, _ -> openVoice() }

        observe()
        updateSendButton()
        updateReasoningButton(viewModel.effort.value, viewModel.forcedThinking.value)
        AgentTypography.apply(binding.root)
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        val grew = messages.size > lastCount
                        val newUser = grew && messages.lastOrNull()?.role == ChatItem.Role.USER
                        if (newUser) userNearBottom = true
                        val signature = messages.sumOf {
                            it.content.length + it.reasoning.length + it.toolTraces.size * 31
                        }
                        adapter.submitList(messages)
                        binding.emptyPanel.visibility =
                            if (messages.none { it.role == ChatItem.Role.USER }) View.VISIBLE else View.GONE
                        binding.recyclerChat.post {
                            if (userNearBottom && (grew || signature != lastSignature)) {
                                scrollToBottom()
                            }
                        }
                        lastCount = messages.size
                        lastSignature = signature
                    }
                }
                launch { viewModel.generating.collect { updateSendButton() } }
                launch {
                    viewModel.effort.collect { effort ->
                        if (!viewModel.forcedThinking.value) {
                            updateReasoningButton(effort, false)
                        }
                    }
                }
                launch {
                    viewModel.forcedThinking.collect { forced ->
                        updateReasoningButton(viewModel.effort.value, forced)
                    }
                }
                launch { viewModel.webSearchEnabled.collect { updateWebSearchButton(it) } }
                launch {
                    viewModel.sessions.collect { sessions ->
                        sessionAdapter?.submit(
                            sessions,
                            viewModel.sessionId.value,
                            sessionAdapter?.isSelectionMode() == true,
                            sessionAdapter?.selectedIds().orEmpty()
                        )
                    }
                }
                launch {
                    viewModel.lastError.collect { error ->
                        binding.btnRetry.visibility =
                            if (error != null && viewModel.canRetry()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateWebSearchButton(enabled: Boolean) {
        binding.btnWebSearch.background = ContextCompat.getDrawable(
            requireContext(),
            if (enabled) R.drawable.bg_agent_web_on else R.drawable.bg_agent_ink_border
        )
        binding.btnWebSearch.imageTintList = android.content.res.ColorStateList.valueOf(
            if (enabled) AgentInkColors.CREAM else AgentInkColors.TEXT_TERTIARY
        )
        binding.btnWebSearch.alpha = if (enabled) 1f else 0.82f
        binding.btnWebSearch.contentDescription = getString(
            if (enabled) R.string.agent_web_on else R.string.agent_web_off
        )
        // 开启时轻微弹一下，强化状态变化
        binding.btnWebSearch.animate().cancel()
        if (enabled) {
            binding.btnWebSearch.scaleX = 0.9f
            binding.btnWebSearch.scaleY = 0.9f
            binding.btnWebSearch.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
        } else {
            binding.btnWebSearch.scaleX = 1f
            binding.btnWebSearch.scaleY = 1f
        }
    }

    private fun loadAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val slots = MAX_ATTACHMENTS - pendingAttachments.size
        if (slots <= 0) {
            Toast.makeText(requireContext(), R.string.agent_attachment_limit, Toast.LENGTH_SHORT).show()
            return
        }
        if (uris.size > slots) {
            Toast.makeText(requireContext(), R.string.agent_attachment_limit, Toast.LENGTH_SHORT).show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), R.string.agent_attachment_reading, Toast.LENGTH_SHORT).show()
            val loaded = uris.take(slots).mapNotNull { uri ->
                runCatching { AttachmentReader.read(requireContext(), uri) }.getOrNull()
            }
            if (_binding == null) return@launch
            pendingAttachments += loaded
            renderAttachmentChips()
            updateSendButton()
        }
    }

    private fun renderAttachmentChips() {
        val row = binding.attachmentRow
        row.removeAllViews()
        binding.attachmentScroll.visibility =
            if (pendingAttachments.isEmpty()) View.GONE else View.VISIBLE
        pendingAttachments.toList().forEach { attachment ->
            val chip = TextView(requireContext()).apply {
                text = "📎 ${attachment.name}  ×"
                setTextColor(AgentInkColors.CREAM)
                textSize = 10f
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_agent_ink_border)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                setOnClickListener {
                    pendingAttachments.remove(attachment)
                    renderAttachmentChips()
                    updateSendButton()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8f) })
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateSendButton() {
        val generating = viewModel.generating.value
        binding.btnSend.text = if (generating) "■" else "↑"
        binding.btnSend.contentDescription = getString(
            if (generating) R.string.agent_stop else R.string.agent_send
        )
        binding.btnSend.isEnabled = generating ||
            binding.etInput.text?.toString()?.isNotBlank() == true ||
            pendingAttachments.isNotEmpty()
        binding.btnSend.alpha = if (binding.btnSend.isEnabled) 1f else 0.45f
        binding.btnSend.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_agent_ink)
        binding.btnSend.setTextColor(
            if (binding.btnSend.isEnabled) {
                AgentInkColors.CREAM
            } else {
                android.graphics.Color.WHITE
            }
        )
        binding.btnReasoning.isEnabled = !generating && !viewModel.forcedThinking.value
        binding.btnReasoning.alpha = if (binding.btnReasoning.isEnabled) 1f else 0.5f
        binding.btnRetry.visibility =
            if (viewModel.lastError.value != null && viewModel.canRetry()) View.VISIBLE else View.GONE
    }

    private fun updateReasoningButton(effort: String, forced: Boolean) {
        val label = when {
            forced -> getString(R.string.agent_forced_thinking)
            effort == "low" -> getString(R.string.agent_effort_low)
            effort == "high" -> getString(R.string.agent_effort_high)
            effort == "max" -> getString(R.string.agent_effort_max)
            else -> getString(R.string.agent_effort_off)
        }
        binding.btnReasoning.text = getString(R.string.agent_reasoning_short) + "·" + label
        binding.btnReasoning.isEnabled = !viewModel.generating.value && !forced
        binding.btnReasoning.alpha = if (binding.btnReasoning.isEnabled) 1f else 0.5f
    }

    private fun showSessionCapsule() {
        if (activeCapsule == CapsuleKind.SESSION && capsuleOverlay?.isShowing == true) {
            capsuleOverlay?.dismiss()
            return
        }
        capsuleOverlay?.dismissNow()
        val tokens = ThemeTokens.of(requireContext())
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(requireContext()).apply {
            text = getString(R.string.agent_session_title)
            setTextColor(AgentInkColors.TEXT_PRIMARY)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(40f), 1f))
        val btnNew = TextView(requireContext()).apply {
            text = getString(R.string.agent_session_new)
            setTextColor(AgentInkColors.CREAM)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        header.addView(btnNew, LinearLayout.LayoutParams(dp(56f), dp(40f)))
        val selectToggle = TextView(requireContext()).apply {
            text = getString(R.string.agent_session_select)
            setTextColor(AgentInkColors.TEXT_SECONDARY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        header.addView(selectToggle, LinearLayout.LayoutParams(dp(56f), dp(40f)))
        content.addView(header)
        sessionSelectToggle = selectToggle

        val recycler = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            clipToPadding = false
            setPadding(0, dp(4f), 0, dp(4f))
        }
        content.addView(recycler, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val adapter = SessionAdapter(
            tokens = tokens,
            onClick = { session ->
                if (session.id != viewModel.sessionId.value) {
                    viewModel.switchSession(session.id)
                }
                capsuleOverlay?.dismiss()
            },
            onLongClick = { session ->
                sessionAdapter?.setSelectionMode(true)
                sessionAdapter?.toggle(session.id)
                renderSessionSelection()
            }
        )
        sessionAdapter = adapter
        recycler.adapter = adapter

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(6f), 0, 0)
        }
        val selectAll = TextView(requireContext()).apply {
            text = getString(R.string.agent_session_select_all)
            setTextColor(AgentInkColors.CREAM)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        actionRow.addView(selectAll, LinearLayout.LayoutParams(dp(64f), dp(40f)))
        actionRow.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        val delete = TextView(requireContext()).apply {
            text = getString(R.string.agent_session_delete_n, 0)
            setTextColor(android.graphics.Color.parseColor("#D9534F"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        actionRow.addView(delete, LinearLayout.LayoutParams(dp(96f), dp(40f)))
        content.addView(actionRow)
        val closeFooter = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, 0)
        }
        val closeButton = TextView(requireContext()).apply {
            text = getString(R.string.agent_terminal_close)
            setTextColor(AgentInkColors.CREAM)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        closeFooter.addView(closeButton, LinearLayout.LayoutParams(dp(64f), dp(40f)))
        closeFooter.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        content.addView(closeFooter)
        sessionActionRow = actionRow
        sessionSelectAll = selectAll
        sessionDelete = delete

        fun exitSelection() {
            sessionAdapter?.setSelectionMode(false)
            actionRow.visibility = View.GONE
            selectToggle.text = getString(R.string.agent_session_select)
            renderSessionSelection()
        }
        fun enterSelection() {
            sessionAdapter?.setSelectionMode(true)
            actionRow.visibility = View.VISIBLE
            selectToggle.text = getString(R.string.agent_session_done)
            renderSessionSelection()
        }
        btnNew.setOnClickListener {
            viewModel.newSession()
            capsuleOverlay?.dismiss()
        }
        selectToggle.setOnClickListener {
            if (sessionAdapter?.isSelectionMode() == true) exitSelection() else enterSelection()
        }
        selectAll.setOnClickListener {
            sessionAdapter?.selectAll()
            renderSessionSelection()
        }
        delete.setOnClickListener {
            val ids = sessionAdapter?.selectedIds().orEmpty()
            if (ids.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.agent_session_delete_confirm, ids.size))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteSessions(ids.toList())
                    capsuleOverlay?.dismiss()
                }
                .show()
        }

        adapter.submit(
            viewModel.sessions.value,
            viewModel.sessionId.value,
            false,
            emptySet()
        )

        val width = minOf((binding.chatRoot.width - dp(48f)).coerceAtLeast(dp(240f)), dp(440f))
        val height = minOf((binding.chatRoot.height * 0.56f).toInt(), dp(460f))
            .coerceAtLeast(dp(260f))
        val left = (binding.chatRoot.width - width) / 2f
        val top = (binding.chatRoot.height - height - dp(16f)).toFloat()
        val overlay = CapsuleOverlay(binding.capsuleHost, tokens)
        capsuleOverlay = overlay
        activeCapsule = CapsuleKind.SESSION
        closeButton.setOnClickListener { overlay.dismiss() }
        overlay.show(
            anchor = binding.btnSessions,
            target = RectF(left, top, left + width, top + height),
            content = content
        ) {
            if (capsuleOverlay === overlay) {
                capsuleOverlay = null
                activeCapsule = null
            }
            sessionAdapter = null
            sessionActionRow = null
            sessionSelectAll = null
            sessionDelete = null
            sessionSelectToggle = null
        }
        renderSessionSelection()
    }

    private fun renderSessionSelection() {
        val adapter = sessionAdapter ?: return
        val selected = adapter.selectedIds().size
        sessionDelete?.text = getString(R.string.agent_session_delete_n, selected)
        sessionDelete?.alpha = if (selected > 0) 1f else 0.4f
        sessionDelete?.isEnabled = selected > 0
        sessionSelectToggle?.text = if (adapter.isSelectionMode()) {
            getString(R.string.agent_session_done)
        } else {
            getString(R.string.agent_session_select)
        }
        sessionActionRow?.visibility =
            if (adapter.isSelectionMode()) View.VISIBLE else View.GONE
    }

    /** 从推理按钮向上展开长条胶囊，内含两个竖向 4 档滑条。 */
    private fun showReasoningCapsule() {
        if (activeCapsule == CapsuleKind.REASONING && capsuleOverlay?.isShowing == true) {
            capsuleOverlay?.dismiss()
            return
        }
        capsuleOverlay?.dismissNow()
        val tokens = ThemeTokens.of(requireContext())
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val columns = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        content.addView(columns, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val effortLevels = viewModel.supportedEfforts.value
        val effortSlider = VerticalLevelSliderView(requireContext()).apply {
            isEnabled = !viewModel.forcedThinking.value && effortLevels.size > 1
            setProgress(effortToProgress(viewModel.effort.value, effortLevels))
        }
        val effortValue = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            setTextColor(AgentInkColors.CREAM)
            textSize = 12f
            text = reasoningLabelForProgress(
                effortToProgress(viewModel.effort.value, effortLevels)
            )
        }
        effortSlider.onProgressPreview = { value -> effortValue.text = reasoningLabelForProgress(value) }
        effortSlider.onProgressChanged = { value ->
            val effort = effortForProgress(value, effortLevels)
            if (effort != viewModel.effort.value) viewModel.selectEffort(effort)
            effortValue.text = reasoningLabelForProgress(value)
        }

        val liveSlider = VerticalLevelSliderView(requireContext()).apply {
            setProgress(livelinessToProgress(viewModel.liveliness.value))
        }
        val liveValue = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            setTextColor(AgentInkColors.CREAM)
            textSize = 12f
            text = livelinessLabelForProgress(livelinessToProgress(viewModel.liveliness.value))
        }
        liveSlider.onProgressPreview = { value ->
            liveValue.text = livelinessLabelForProgress(value)
        }
        liveSlider.onProgressChanged = { value ->
            viewModel.selectLiveliness(livelinessForProgress(value))
            liveValue.text = livelinessLabelForProgress(value)
        }

        fun addColumn(title: String, slider: VerticalLevelSliderView, value: TextView) {
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            column.addView(TextView(requireContext()).apply {
                text = title
                setTextColor(AgentInkColors.TEXT_SECONDARY)
                textSize = 11f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28f)))
            column.addView(slider, LinearLayout.LayoutParams(dp(56f), 0, 1f))
            column.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28f)))
            columns.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        addColumn(getString(R.string.agent_reasoning_effort), effortSlider, effortValue)
        addColumn(getString(R.string.agent_liveliness), liveSlider, liveValue)

        val closeFooter = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, 0)
        }
        val closeButton = TextView(requireContext()).apply {
            text = getString(R.string.agent_terminal_close)
            setTextColor(AgentInkColors.CREAM)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        closeFooter.addView(closeButton, LinearLayout.LayoutParams(dp(64f), dp(40f)))
        closeFooter.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        content.addView(closeFooter)

        val overlay = CapsuleOverlay(binding.capsuleHost, tokens)
        capsuleOverlay = overlay
        activeCapsule = CapsuleKind.REASONING
        overlay.onBeforeDismiss = {
            effortSlider.commit()
            liveSlider.commit()
        }
        closeButton.setOnClickListener { overlay.dismiss() }

        val hostW = binding.chatRoot.width
        val hostH = binding.chatRoot.height
        val width = minOf(dp(190f), hostW - dp(32f)).coerceAtLeast(dp(168f))
        val maxHeight = (hostH - dp(24f)).coerceAtLeast(dp(220f))
        // 比上一版加高约 1.5cm（94dp），并额外获得删掉标题后释放的空间
        val height = minOf((hostH * 0.72f).toInt(), dp(474f), maxHeight)
        val anchorLocation = IntArray(2)
        val hostLocation = IntArray(2)
        binding.btnReasoning.getLocationOnScreen(anchorLocation)
        binding.capsuleHost.getLocationOnScreen(hostLocation)
        val anchorTop = (anchorLocation[1] - hostLocation[1]).toFloat()
        var top = anchorTop - height - dp(10f)
        if (top < dp(12f)) top = (hostH - height - dp(12f)).toFloat()
        val right = hostW - dp(12f)
        val left = right - width
        overlay.show(
            anchor = binding.btnReasoning,
            target = RectF(left.toFloat(), top, right.toFloat(), top + height),
            content = content
        ) {
            if (capsuleOverlay === overlay) {
                capsuleOverlay = null
                activeCapsule = null
            }
        }
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

    private fun livelinessToProgress(value: String): Int = when (value) {
        "low" -> 33
        "high" -> 66
        "ultra" -> 100
        else -> 0
    }

    private fun livelinessForProgress(value: Int): String = when {
        value < 17 -> "off"
        value < 50 -> "low"
        value < 84 -> "high"
        else -> "ultra"
    }

    private fun livelinessLabelForProgress(value: Int): String = when (livelinessForProgress(value)) {
        "off" -> "关闭"
        "low" -> "轻微"
        "high" -> "标准"
        else -> "夸张"
    }

    private fun sendCurrentInput() {
        val text = binding.etInput.text?.toString()?.trim().orEmpty()
        if ((text.isEmpty() && pendingAttachments.isEmpty()) || viewModel.generating.value) return
        val attachments = pendingAttachments.toList()
        binding.etInput.setText("")
        binding.etInput.clearFocus()
        pendingAttachments.clear()
        renderAttachmentChips()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        viewModel.send(text, attachments)
        updateSendButton()
    }

    fun openVoice() {
        if (_binding == null) return
        if (viewModel.generating.value) return
        if (viewModel.models.value.isEmpty()) viewModel.loadModels()
        binding.etInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etInput.postDelayed({
            if (_binding == null) return@postDelayed
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

    private fun fillInput(res: Int) {
        binding.etInput.setText(res)
        binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
    }

    private fun scrollToBottom() {
        if (adapter.itemCount == 0) return
        binding.recyclerChat.post {
            binding.recyclerChat.smoothScrollToPosition(adapter.itemCount - 1)
            binding.btnScrollBottom.visibility = View.GONE
            userNearBottom = true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_ATTACHMENTS = 3
    }
}
