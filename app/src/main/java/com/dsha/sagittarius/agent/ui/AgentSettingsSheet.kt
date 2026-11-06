package com.dsha.sagittarius.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.agent.llm.ProviderRegistry
import com.dsha.sagittarius.databinding.SheetAgentSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AgentSettingsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAgentSettingsBinding? = null
    private val binding get() = _binding!!
    private var providers: List<ProviderOption> = emptyList()
    private var models: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAgentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        val app = requireActivity().application as SagittariusApp
        val prefs = app.sagittariusPrefs
        providers = ProviderRegistry.all.map { ProviderOption(it.id, it.displayName) } +
            ProviderOption("custom", getString(R.string.agent_custom_provider))
        binding.spinnerProvider.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, providers.map { it.label }
        )
        val providerIndex = providers.indexOfFirst { it.id == prefs.provider.value }.coerceAtLeast(0)
        binding.spinnerProvider.setSelection(providerIndex)
        binding.etBaseUrl.setText(prefs.customBaseUrl.value)
        binding.switchBocha.isChecked = prefs.webSearchEnabled.value
        binding.switchLocalFood.isChecked = true
        binding.switchAiEstimate.isChecked = true
        binding.etBaseUrl.visibility = if (prefs.provider.value == "custom") View.VISIBLE else View.GONE
        loadModelOptions(prefs.provider.value)
        binding.spinnerSearchProvider.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.agent_search_bocha))
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val hasProviderKey = withContext(Dispatchers.IO) { app.apiKeyStore.load(prefs.provider.value) }
            val hasBocha = withContext(Dispatchers.IO) { app.apiKeyStore.load("bocha") }
            if (!hasProviderKey.isNullOrBlank()) binding.etProviderKey.hint = getString(R.string.agent_provider_key_hint)
            if (!hasBocha.isNullOrBlank()) binding.etSearchKey.hint = getString(R.string.agent_search_key_hint)
        }
        binding.spinnerProvider.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = providers.getOrNull(position) ?: return
                binding.etBaseUrl.visibility = if (provider.id == "custom") View.VISIBLE else View.GONE
                loadModelOptions(provider.id)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
        val accent = com.dsha.sagittarius.ui.ButtonAccentManager.current
        binding.btnSaveSettings.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            accent.colors
        ).apply { cornerRadius = 16 * resources.displayMetrics.density }
        binding.btnSaveSettings.setTextColor(accent.textColor)
        binding.btnSaveSettings.setOnClickListener { save() }
    }

    private fun loadModelOptions(providerId: String) {
        if (providerId == "custom" || providerId == "deepseek") {
            models = listOf("deepseek-chat", "deepseek-reasoner")
        } else {
            models = ProviderRegistry.get(providerId)?.fallbackModels.orEmpty()
                .ifEmpty { listOf("deepseek-chat", "deepseek-reasoner") }
        }
        val prefs = (requireActivity().application as SagittariusApp).sagittariusPrefs
        val current = if (providerId == prefs.provider.value) prefs.model.value else models.first()
        if (current !in models) models = models + current
        binding.spinnerModel.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, models
        )
        binding.spinnerModel.setSelection(models.indexOf(current).coerceAtLeast(0))
    }

    private fun save() {
        val app = requireActivity().application as SagittariusApp
        val prefs = app.sagittariusPrefs
        val provider = providers.getOrNull(binding.spinnerProvider.selectedItemPosition) ?: return
        val model = models.getOrNull(binding.spinnerModel.selectedItemPosition) ?: return
        val baseUrl = binding.etBaseUrl.text?.toString()?.trim().orEmpty()
        if (provider.id == "custom" && (baseUrl.toHttpUrlOrNull() == null || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")))) {
            binding.etBaseUrl.error = getString(R.string.agent_custom_url_invalid)
            return
        }
        val providerKey = binding.etProviderKey.text?.toString()?.trim().orEmpty()
        val searchKey = binding.etSearchKey.text?.toString()?.trim().orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val existingKey = withContext(Dispatchers.IO) { app.apiKeyStore.load(provider.id) }
            if (providerKey.isEmpty() && existingKey.isNullOrBlank()) {
                binding.etProviderKey.error = getString(R.string.agent_api_empty)
                return@launch
            }
            withContext(Dispatchers.IO) {
                prefs.setProvider(provider.id)
                if (provider.id == "custom") prefs.setCustomBaseUrl(baseUrl)
                prefs.setModel(provider.id, model)
                if (providerKey.isNotEmpty()) app.apiKeyStore.save(provider.id, providerKey)
                if (searchKey.isNotEmpty()) app.apiKeyStore.save("bocha", searchKey)
                prefs.setWebSearchEnabled(binding.switchBocha.isChecked)
            }
            Toast.makeText(requireContext(), R.string.agent_settings_saved, Toast.LENGTH_SHORT).show()
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putBoolean(RESULT_SAVED, true) }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class ProviderOption(val id: String, val label: String)

    companion object {
        const val REQUEST_KEY = "agent_settings_result"
        const val RESULT_SAVED = "saved"
    }
}
