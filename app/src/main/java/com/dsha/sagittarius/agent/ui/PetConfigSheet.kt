package com.dsha.sagittarius.agent.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.databinding.SheetPetConfigBinding
import com.dsha.sagittarius.ui.ThemeTokens
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PetConfigSheet : BottomSheetDialogFragment() {
    private var _binding: SheetPetConfigBinding? = null
    private val binding get() = _binding!!
    private var selectedColor = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPetConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SheetMotion.animateIn(view)
        selectedColor = requireArguments().getInt(ARG_COLOR)
        val scale = requireArguments().getInt(ARG_SCALE).coerceIn(50, 200)
        binding.petScaleSeek.progress = scale - 50
        binding.tvPetScale.text = "$scale%"
        renderColors()
        binding.colorTheme.setOnClickListener { pick(0) }
        binding.colorPink.setOnClickListener { pick(Color.parseColor("#FF6FB5")) }
        binding.colorCyan.setOnClickListener { pick(Color.parseColor("#5EE7FF")) }
        binding.colorYellow.setOnClickListener { pick(Color.parseColor("#FFD166")) }
        binding.colorWhite.setOnClickListener { pick(Color.WHITE) }
        binding.petScaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = progress + 50
                binding.tvPetScale.text = "$percent%"
                if (fromUser) emit(REQUEST_KEY_SCALE_PREVIEW, Bundle().apply { putInt(RESULT_SCALE, percent) })
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) {
                emit(REQUEST_KEY_SCALE_SAVE, Bundle().apply { putInt(RESULT_SCALE, (bar?.progress ?: 50) + 50) })
            }
        })
    }

    private fun pick(color: Int) {
        selectedColor = color
        renderColors()
        emit(REQUEST_KEY_COLOR, Bundle().apply { putInt(RESULT_COLOR_VALUE, color) })
    }

    private fun renderColors() {
        val tokens = ThemeTokens.of(requireContext())
        val accent = tokens.accent
        listOf(
            binding.colorTheme to 0,
            binding.colorPink to Color.parseColor("#FF6FB5"),
            binding.colorCyan to Color.parseColor("#5EE7FF"),
            binding.colorYellow to Color.parseColor("#FFD166"),
            binding.colorWhite to Color.WHITE
        ).forEach { (view, color) ->
            val selected = color == selectedColor
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (color == 0) accent else color)
                setStroke(
                    if (selected) (3 * resources.displayMetrics.density).toInt() else 1,
                    if (selected) Color.WHITE else ColorUtils.setAlphaComponent(accent, 110)
                )
            }
        }
    }

    private fun emit(key: String, data: Bundle) {
        parentFragmentManager.setFragmentResult(key, data)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_COLOR = "color"
        private const val ARG_SCALE = "scale"
        const val REQUEST_KEY_COLOR = "pet_config_color"
        const val REQUEST_KEY_SCALE_PREVIEW = "pet_config_scale_preview"
        const val REQUEST_KEY_SCALE_SAVE = "pet_config_scale_save"
        const val RESULT_COLOR_VALUE = "color_value"
        const val RESULT_SCALE = "scale"

        fun newInstance(color: Int, scale: Int): PetConfigSheet = PetConfigSheet().apply {
            arguments = Bundle().apply {
                putInt(ARG_COLOR, color)
                putInt(ARG_SCALE, scale)
            }
        }
    }
}
