package com.dsha.sagittarius.agent.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.agent.llm.ProviderRegistry
import com.dsha.sagittarius.data.AgentMemoryCategory
import com.dsha.sagittarius.data.AgentMemoryEntity
import com.dsha.sagittarius.databinding.FragmentAgentConfigBinding
import com.dsha.sagittarius.ui.ThemeTokens
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 配置页：服务商 / 模型 / 分类记忆 / 高级设置 / 预留外观区。 */
class AgentConfigFragment : Fragment() {

    private var _binding: FragmentAgentConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel get() = (requireActivity() as SagittariusActivity).viewModel
    private val tokens get() = ThemeTokens.of(requireContext())
    private var memoryFilter: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnProviderSettings.setOnClickListener {
            AgentSettingsSheet().show(parentFragmentManager, "agent_settings")
        }
        binding.btnAdvanced.setOnClickListener {
            AgentMoreSheet.newInstance(
                count = viewModel.messages.value.size,
                prompt = viewModel.tokenUsage.value.promptTokens,
                completion = viewModel.tokenUsage.value.completionTokens,
                model = viewModel.modelId.value
            ).show(parentFragmentManager, "agent_more")
        }
        binding.btnAddMemory.setOnClickListener { showMemoryDialog(null) }
        parentFragmentManager.setFragmentResultListener(
            AgentSettingsSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(AgentSettingsSheet.RESULT_SAVED)) viewModel.reloadForKeyChange()
        }
        parentFragmentManager.setFragmentResultListener(
            AgentMoreSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when {
                result.getBoolean(AgentMoreSheet.RESULT_CLEAR) ->
                    viewModel.clearConversation(showWelcome = false)
                result.getBoolean(AgentMoreSheet.RESULT_PERSONA) ->
                    viewModel.refreshPersonaGreeting()
            }
        }
        renderMemoryChips()
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.provider.collect { id ->
                        val label = if (id == "custom") getString(R.string.agent_custom_provider)
                        else ProviderRegistry.get(id)?.displayName ?: id
                        binding.tvProviderConfig.text =
                            getString(R.string.agent_provider) + " · " + label
                        renderProviderChips()
                    }
                }
                launch {
                    viewModel.modelId.collect { id ->
                        val label = viewModel.models.value.firstOrNull { it.id == id }?.displayName ?: id
                        binding.tvModelConfig.text = getString(R.string.agent_model) + " · " + label
                        renderModelChips()
                    }
                }
                launch {
                    viewModel.models.collect { renderModelChips() }
                }
                launch {
                    viewModel.memories.collect {
                        renderMemoryChips()
                        renderMemories()
                    }
                }
            }
        }
    }

    private fun renderProviderChips() {
        binding.providerChips.removeAllViews()
        val current = viewModel.provider.value
        val items = ProviderRegistry.all.map { it.id to it.displayName } +
            ("custom" to getString(R.string.agent_custom_provider))
        items.forEach { (id, label) ->
            binding.providerChips.addView(chip(label, id == current) {
                selectProvider(id)
            })
        }
    }

    private fun renderModelChips() {
        binding.modelChips.removeAllViews()
        val current = viewModel.modelId.value
        viewModel.models.value.forEach { model ->
            binding.modelChips.addView(chip(model.displayName, model.id == current) {
                if (model.id != current) viewModel.selectModel(model.id)
            })
        }
    }

    private fun renderMemoryChips() {
        binding.memoryChips.removeAllViews()
        val items = listOf<String?>(null) + AgentMemoryCategory.all
        items.forEach { category ->
            val label = category ?: getString(R.string.agent_config_category_all)
            binding.memoryChips.addView(chip(label, memoryFilter == category) {
                memoryFilter = category
                renderMemoryChips()
                renderMemories()
            })
        }
    }

    private fun renderMemories() {
        val container = binding.memoryContainer
        container.removeAllViews()
        val memories = viewModel.memories.value
            .filter { memoryFilter == null || it.category == memoryFilter }
        if (memories.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.agent_config_memory_empty)
                setTextColor(tokens.textTertiary)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 18)
            })
            return
        }
        memories.forEach { memory ->
            container.addView(
                memoryRow(memory),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6f) }
            )
        }
    }

    private fun memoryRow(memory: AgentMemoryEntity): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_agent_card)
            setPadding(dp(12f), dp(10f), dp(8f), dp(10f))
            alpha = if (memory.enabled) 1f else 0.55f
            setOnClickListener { showMemoryDialog(memory) }
        }
        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        info.addView(TextView(requireContext()).apply {
            text = memory.content
            setTextColor(tokens.textPrimary)
            textSize = 13f
            maxLines = 3
        })
        info.addView(TextView(requireContext()).apply {
            text = memory.category + if (memory.enabled) "" else " · 已停用"
            setTextColor(tokens.textTertiary)
            textSize = 10f
            setPadding(0, dp(3f), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = TextView(requireContext()).apply {
            text = getString(
                if (memory.enabled) R.string.agent_config_memory_toggle_on
                else R.string.agent_config_memory_toggle_off
            )
            setTextColor(tokens.accent)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(6f), 0, dp(6f), 0)
            setOnClickListener {
                viewModel.setMemoryEnabled(memory, !memory.enabled)
            }
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(56f), dp(40f)))

        val delete = TextView(requireContext()).apply {
            text = getString(R.string.agent_config_memory_delete)
            setTextColor(Color.parseColor("#D9534F"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(6f), 0, dp(6f), 0)
            setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.agent_config_memory_delete_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteMemory(memory.id) }
                    .show()
            }
        }
        row.addView(delete, LinearLayout.LayoutParams(dp(56f), dp(40f)))
        return row
    }

    private fun showMemoryDialog(existing: AgentMemoryEntity?) {
        var category = existing?.category ?: AgentMemoryCategory.DIET
        val content = EditText(requireContext()).apply {
            setText(existing?.content.orEmpty())
            hint = getString(R.string.agent_config_memory_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input)
        }
        val chips = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        val chipViews = mutableListOf<TextView>()
        fun renderChips() {
            chipViews.forEachIndexed { index, view ->
                val value = AgentMemoryCategory.all[index]
                val selected = value == category
                view.setTextColor(if (selected) tokens.accent else tokens.textSecondary)
                view.background = GradientDrawable().apply {
                    cornerRadius = 14f * resources.displayMetrics.density
                    setColor(
                        if (selected) ColorUtils.setAlphaComponent(tokens.accent, 30) else 0
                    )
                    setStroke(
                        1,
                        if (selected) tokens.accent
                        else ColorUtils.setAlphaComponent(tokens.textTertiary, 50)
                    )
                }
            }
        }
        AgentMemoryCategory.all.forEach { value ->
            val chip = TextView(requireContext()).apply {
                text = value
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(4f), 0, dp(4f), 0)
                setOnClickListener {
                    category = value
                    renderChips()
                }
            }
            chipViews += chip
            chips.addView(chip, LinearLayout.LayoutParams(0, dp(36f), 1f).apply {
                marginEnd = dp(4f)
            })
        }
        renderChips()
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(6f), dp(20f), dp(4f))
            addView(chips)
            addView(content, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (existing == null) R.string.agent_config_memory_add_title
                else R.string.agent_config_memory_edit_title
            )
            .setView(ScrollView(requireContext()).apply { addView(root) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = content.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    content.error = "请输入记忆内容"
                    return@setOnClickListener
                }
                if (existing == null) {
                    viewModel.addMemory(category, text)
                } else {
                    viewModel.updateMemory(existing, category, text)
                }
                Toast.makeText(
                    requireContext(), R.string.agent_config_memory_saved, Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun selectProvider(id: String) {
        if (id == viewModel.provider.value) return
        viewModel.selectProvider(id)
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as SagittariusApp
            val key = withContext(Dispatchers.IO) { app.apiKeyStore.load(id) }
            if (key.isNullOrBlank()) {
                AgentSettingsSheet().show(parentFragmentManager, "agent_settings")
            }
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(requireContext()).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(12f), 0, dp(12f), 0)
            setTextColor(if (selected) tokens.accent else tokens.textSecondary)
            background = GradientDrawable().apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(if (selected) ColorUtils.setAlphaComponent(tokens.accent, 28) else 0)
                setStroke(
                    1,
                    if (selected) tokens.accent
                    else ColorUtils.setAlphaComponent(tokens.textTertiary, 45)
                )
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)
            ).apply { marginEnd = dp(6f) }
        }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
