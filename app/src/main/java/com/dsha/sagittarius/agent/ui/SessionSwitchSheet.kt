package com.dsha.sagittarius.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.databinding.SheetSessionSwitchBinding
import com.dsha.sagittarius.ui.ThemeTokens
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SessionSwitchSheet : BottomSheetDialogFragment() {
    private var _binding: SheetSessionSwitchBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSessionSwitchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        val ids: LongArray = requireArguments().getLongArray(ARG_IDS) ?: longArrayOf()
        val titles: Array<String> = requireArguments().getStringArray(ARG_TITLES) ?: emptyArray()
        val currentId = requireArguments().getLong(ARG_CURRENT)
        val tokens = ThemeTokens.of(requireContext())
        binding.btnNewSession.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(RESULT_ACTION, ACTION_NEW) }
            )
            dismissAllowingStateLoss()
        }
        ids.forEachIndexed { index, id ->
            val row = TextView(requireContext()).apply {
                text = titles.getOrNull(index) ?: "对话"
                textSize = 13f
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(14, 0, 14, 0)
                setTextColor(if (id == currentId) tokens.accent else tokens.textPrimary)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 14f * resources.displayMetrics.density
                    setColor(if (id == currentId) androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 24) else 0)
                }
                setOnLongClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setMessage(R.string.agent_delete_session_confirm)
                        .setNegativeButton(R.string.agent_cancel, null)
                        .setPositiveButton(R.string.agent_delete) { _, _ ->
                            parentFragmentManager.setFragmentResult(
                                REQUEST_KEY,
                                Bundle().apply {
                                    putString(RESULT_ACTION, ACTION_DELETE)
                                    putLong(RESULT_ID, id)
                                }
                            )
                            dismissAllowingStateLoss()
                        }
                        .show()
                    true
                }
                setOnClickListener {
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(RESULT_ACTION, ACTION_SWITCH)
                            putLong(RESULT_ID, id)
                        }
                    )
                    dismissAllowingStateLoss()
                }
            }
            binding.sessionContainer.addView(row, android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            ).apply { topMargin = (2 * resources.displayMetrics.density).toInt() })
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_IDS = "ids"
        private const val ARG_TITLES = "titles"
        private const val ARG_CURRENT = "current"
        const val REQUEST_KEY = "session_switch_result"
        const val RESULT_ACTION = "action"
        const val RESULT_ID = "session_id"
        const val ACTION_NEW = "new"
        const val ACTION_SWITCH = "switch"
        const val ACTION_DELETE = "delete"

        fun newInstance(ids: LongArray, titles: Array<String>, currentId: Long): SessionSwitchSheet =
            SessionSwitchSheet().apply {
                arguments = Bundle().apply {
                    putLongArray(ARG_IDS, ids)
                    putStringArray(ARG_TITLES, titles)
                    putLong(ARG_CURRENT, currentId)
                }
            }
    }
}
