package com.dsha.sagittarius.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dsha.sagittarius.databinding.SheetMessageActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MessageActionsSheet : BottomSheetDialogFragment() {
    private var _binding: SheetMessageActionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetMessageActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        binding.actionCopy.setOnClickListener { send(ACTION_COPY) }
        binding.actionClear.setOnClickListener { send(ACTION_CLEAR) }
    }

    private fun send(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action)
                putString(RESULT_TEXT, requireArguments().getString(ARG_TEXT).orEmpty())
            }
        )
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TEXT = "text"
        const val REQUEST_KEY = "message_actions"
        const val RESULT_ACTION = "action"
        const val RESULT_TEXT = "text"
        const val ACTION_COPY = "copy"
        const val ACTION_CLEAR = "clear"

        fun newInstance(text: String): MessageActionsSheet = MessageActionsSheet().apply {
            arguments = Bundle().apply { putString(ARG_TEXT, text) }
        }
    }
}
