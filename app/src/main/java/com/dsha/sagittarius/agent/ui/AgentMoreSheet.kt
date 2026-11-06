package com.dsha.sagittarius.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.agent.data.SagittariusPrefs
import com.dsha.sagittarius.databinding.SheetAgentMoreBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentMoreSheet : BottomSheetDialogFragment() {
    private var liveliness = "high"
    private var _binding: SheetAgentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAgentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        val app = requireActivity().application as SagittariusApp
        val prefs = app.sagittariusPrefs
        binding.etPersona.setText(prefs.personaDescription.value)
        binding.etGreeting.setText(prefs.personaGreeting.value)
        liveliness = prefs.liveliness.value
        renderLiveliness()
        when (prefs.toolPermission.value) {
            SagittariusPrefs.ToolPermissionMode.VIEW_ONLY -> binding.permView.isChecked = true
            SagittariusPrefs.ToolPermissionMode.ASK -> binding.permAsk.isChecked = true
            SagittariusPrefs.ToolPermissionMode.FULL -> binding.permFull.isChecked = true
        }
        binding.tvContextInfo.text = getString(
            R.string.agent_context_info,
            requireArguments().getInt(ARG_COUNT)
        )
        binding.tvTokenInfo.text = getString(
            R.string.agent_token_info,
            requireArguments().getInt(ARG_PROMPT),
            requireArguments().getInt(ARG_COMPLETION)
        )
        binding.tvModelInfo.text = requireArguments().getString(ARG_MODEL).orEmpty()
        binding.tabPersona.setOnClickListener { showTab(0) }
        binding.tabPermission.setOnClickListener { showTab(1) }
        binding.tabContext.setOnClickListener { showTab(2) }
        binding.liveOff.setOnClickListener { selectLiveliness("off") }
        binding.liveLow.setOnClickListener { selectLiveliness("low") }
        binding.liveHigh.setOnClickListener { selectLiveliness("high") }
        binding.liveUltra.setOnClickListener { selectLiveliness("ultra") }
        binding.btnSavePersona.setOnClickListener {
            val description = binding.etPersona.text?.toString().orEmpty()
            val greeting = binding.etGreeting.text?.toString().orEmpty()
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    prefs.setPersona(description, greeting)
                    prefs.setLiveliness(liveliness)
                }
                Toast.makeText(requireContext(), R.string.agent_persona_saved, Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(RESULT_PERSONA, true) })
            }
        }
        binding.btnSavePermission.setOnClickListener {
            val mode = when {
                binding.permView.isChecked -> SagittariusPrefs.ToolPermissionMode.VIEW_ONLY
                binding.permFull.isChecked -> SagittariusPrefs.ToolPermissionMode.FULL
                else -> SagittariusPrefs.ToolPermissionMode.ASK
            }
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { prefs.setToolPermission(mode) }
                Toast.makeText(requireContext(), R.string.agent_permission_saved, Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(RESULT_PERMISSION, true) })
            }
        }
        binding.btnClearHistory.setOnClickListener {
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(RESULT_CLEAR, true) })
            dismissAllowingStateLoss()
        }
        showTab(0)
    }

    private fun selectLiveliness(value: String) {
        liveliness = value
        renderLiveliness()
    }

    private fun renderLiveliness() {
        val tokens = com.dsha.sagittarius.ui.ThemeTokens.of(requireContext())
        listOf(
            "off" to binding.liveOff,
            "low" to binding.liveLow,
            "high" to binding.liveHigh,
            "ultra" to binding.liveUltra
        ).forEach { (value, view) ->
            val selected = value == liveliness
            view.setTextColor(if (selected) tokens.accent else tokens.textSecondary)
            view.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(
                    if (selected) androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 30)
                    else 0
                )
                setStroke(
                    1,
                    if (selected) tokens.accent else androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.textTertiary, 50)
                )
            }
        }
    }

    private fun showTab(index: Int) {
        binding.panelPersona.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelPermission.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.panelContext.visibility = if (index == 2) View.VISIBLE else View.GONE
        val tabs = listOf(binding.tabPersona, binding.tabPermission, binding.tabContext)
        tabs.forEachIndexed { i, tab ->
            tab.setTextColor(
                if (i == index) com.dsha.sagittarius.ui.ThemeTokens.of(requireContext()).accent
                else com.dsha.sagittarius.ui.ThemeTokens.of(requireContext()).textTertiary
            )
            tab.setTypeface(null, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "agent_more_result"
        const val RESULT_PERSONA = "persona"
        const val RESULT_PERMISSION = "permission"
        const val RESULT_CLEAR = "clear"
        private const val ARG_COUNT = "count"
        private const val ARG_PROMPT = "prompt"
        private const val ARG_COMPLETION = "completion"
        private const val ARG_MODEL = "model"

        fun newInstance(count: Int, prompt: Int, completion: Int, model: String): AgentMoreSheet =
            AgentMoreSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COUNT, count)
                    putInt(ARG_PROMPT, prompt)
                    putInt(ARG_COMPLETION, completion)
                    putString(ARG_MODEL, model)
                }
            }
    }
}
