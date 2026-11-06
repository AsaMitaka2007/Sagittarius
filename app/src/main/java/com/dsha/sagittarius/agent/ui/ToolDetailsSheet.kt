package com.dsha.sagittarius.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dsha.sagittarius.databinding.SheetToolDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ToolDetailsSheet : BottomSheetDialogFragment() {
    private var _binding: SheetToolDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetToolDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        val args = requireArguments().getString(ARG_ARGS).orEmpty().ifBlank { "{}" }
        val result = requireArguments().getString(ARG_RESULT).orEmpty().ifBlank { "{}" }
        binding.tvToolTitle.text = requireArguments().getString(ARG_NAME).orEmpty()
        binding.tvToolArgs.text = pretty(args)
        binding.tvToolResult.text = pretty(result)
    }

    private fun pretty(raw: String): String = runCatching {
        val json = org.json.JSONTokener(raw).nextValue()
        when (json) {
            is org.json.JSONObject -> json.toString(2)
            is org.json.JSONArray -> json.toString(2)
            else -> raw
        }
    }.getOrDefault(raw)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_ARGS = "args"
        private const val ARG_RESULT = "result"

        fun newInstance(trace: ToolTrace): ToolDetailsSheet = ToolDetailsSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_NAME, trace.name)
                putString(ARG_ARGS, trace.args)
                putString(ARG_RESULT, trace.result.orEmpty())
            }
        }
    }
}
