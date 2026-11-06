package com.dsha.sagittarius.agent.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.dsha.sagittarius.BuildConfig
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.agent.llm.LlmException
import com.dsha.sagittarius.agent.llm.OkHttpLlmClient
import com.dsha.sagittarius.agent.llm.ProviderRegistry
import com.dsha.sagittarius.agent.debug.LogcatDebugLogger
import com.dsha.sagittarius.databinding.SheetApiKeyBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiKeySheet : BottomSheetDialogFragment() {

    private var _binding: SheetApiKeyBinding? = null
    private val binding get() = _binding!!
    private val keyStore get() = (requireActivity().application as SagittariusApp).apiKeyStore
    private var checking = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetApiKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        val providers = ProviderRegistry.all
        binding.spinnerProvider.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            providers.map { it.displayName }
        )
        val selectedProvider = keyStoreProviderPref()
        binding.spinnerProvider.setSelection(providers.indexOfFirst { it.id == selectedProvider }.coerceAtLeast(0))
        binding.btnHow.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.deepseek.com/api_keys")))
            }
        }
        binding.btnConnect.setOnClickListener { validate() }
        binding.etApiKey.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                validate()
                true
            } else false
        }
        binding.etApiKey.requestFocus()
        viewLifecycleOwner.lifecycleScope.launch {
            val hasBocha = withContext(Dispatchers.IO) { keyStore.load("bocha") }?.isNotBlank() == true
            if (hasBocha) binding.etBochaKey.hint = getString(R.string.agent_bocha_hint)
        }
    }

    private fun validate() {
        if (checking) return
        val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            showError(getString(R.string.agent_api_empty))
            return
        }
        val provider = ProviderRegistry.all.getOrNull(binding.spinnerProvider.selectedItemPosition)
            ?: ProviderRegistry.all.first()
        checking = true
        setLoading(true)
        binding.tvApiError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val client = OkHttpLlmClient(provider.baseUrl, apiKeyProvider = { key }, logger = LogcatDebugLogger, provider = provider)
                    client.validateKey()
                    keyStore.save(provider.id, key)
                    val bocha = binding.etBochaKey.text?.toString()?.trim().orEmpty()
                    if (bocha.isNotEmpty()) keyStore.save("bocha", bocha)
                    (requireActivity().application as SagittariusApp).sagittariusPrefs.setProvider(provider.id)
                    null
                }.getOrElse { it }
            }
            checking = false
            setLoading(false)
            if (error == null) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(RESULT_SUCCESS, true) }
                )
                dismissAllowingStateLoss()
            } else {
                val message = when (error) {
                    is LlmException -> when (error.error.kind) {
                        com.dsha.sagittarius.agent.llm.LlmError.Kind.AUTH -> getString(R.string.agent_error_auth)
                        com.dsha.sagittarius.agent.llm.LlmError.Kind.NETWORK -> getString(R.string.agent_error_network)
                        else -> getString(R.string.agent_error_bad)
                    }
                    else -> getString(R.string.agent_api_network)
                }
                showError(message)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConnect.isEnabled = !loading
        binding.btnConnect.alpha = if (loading) 0.55f else 1f
        binding.btnConnect.text = getString(if (loading) R.string.agent_loading else R.string.agent_api_connect)
        binding.etApiKey.isEnabled = !loading
    }

    private fun keyStoreProviderPref(): String =
        (requireActivity().application as SagittariusApp).sagittariusPrefs.provider.value

    private fun showError(message: String) {
        binding.tvApiError.text = message
        binding.tvApiError.visibility = View.VISIBLE
        binding.keyInputLayout.error = message
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "agent_api_key_result"
        const val RESULT_SUCCESS = "success"
    }
}
