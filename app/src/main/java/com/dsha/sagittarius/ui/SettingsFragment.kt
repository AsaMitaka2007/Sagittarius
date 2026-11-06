package com.dsha.sagittarius.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.BuildConfig
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.databinding.FragmentSettingsBinding
import com.dsha.sagittarius.databinding.ItemSettingRowBinding
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.theme.ThemeMode
import com.dsha.sagittarius.widget.BudgetWidgetProvider
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: BudgetRepository

    private var floatingSwitch: com.google.android.material.materialswitch.MaterialSwitch? = null
    private var overlayDesc: TextView? = null
    private var overlayIcon: ImageView? = null
    private var notificationDesc: TextView? = null
    private var notificationIcon: ImageView? = null
    private var updatingSwitch = false
    private var entrancePlayed = false
    private lateinit var tokens: ThemeTokens
    private val themeChips = mutableMapOf<ThemeMode, GlassChipView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).repository
        tokens = ThemeTokens.of(requireContext())
        binding.tvVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)
        setupThemeSection()
        buildAppearanceRows()
        buildRows()
        observeFloatingState()
        observeTheme()
        refreshPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        maybeAnimateEntrance()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshPermissions()
            maybeAnimateEntrance()
        }
    }

    override fun onDestroyView() {
        floatingSwitch?.let(MotionUtils::cancelPress)
        floatingSwitch = null
        overlayDesc = null
        overlayIcon = null
        notificationDesc = null
        notificationIcon = null
        _binding = null
        super.onDestroyView()
    }

    fun refreshPermissions() {
        if (_binding == null) return
        val overlayGranted = Settings.canDrawOverlays(requireContext())
        overlayDesc?.text = getString(
            if (overlayGranted) R.string.settings_overlay_granted
            else R.string.settings_overlay_missing
        )
        val okColor = if (overlayGranted) tokens.success else tokens.textSecondary
        overlayDesc?.setTextColor(okColor)
        overlayIcon?.imageTintList = ColorStateList.valueOf(okColor)

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        notificationDesc?.text = getString(
            if (notificationGranted) R.string.settings_notification_granted
            else R.string.settings_notification_missing
        )
        val notifyColor = if (notificationGranted) tokens.success else tokens.textSecondary
        notificationDesc?.setTextColor(notifyColor)
        notificationIcon?.imageTintList = ColorStateList.valueOf(notifyColor)
    }

    private fun maybeAnimateEntrance() {
        if (entrancePlayed || isHidden || !isResumed) return
        entrancePlayed = true
        binding.root.post { _binding?.let { MotionUtils.animateCardsIn(it.root) } }
    }

    private fun setupThemeSection() {
        val density = resources.displayMetrics.density
        val labels = mapOf(
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.BLACK to R.string.theme_black,
            ThemeMode.GLASS to R.string.theme_glass
        )
        ThemeMode.entries.forEach { mode ->
            val chip = GlassChipView(requireContext()).apply {
                text = getString(labels.getValue(mode))
                setOnClickListener {
                    Haptics.tap(this)
                    ThemeManager.setMode(requireContext(), mode)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (38 * density).toInt()
            ).apply { marginEnd = (8 * density).toInt() }
            themeChips[mode] = chip
            binding.themeContainer.addView(chip, params)
        }
        updateThemeChips(ThemeManager.current)
    }

    private fun observeTheme() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ThemeManager.mode.collect { mode -> updateThemeChips(mode) }
            }
        }
    }

    private fun updateThemeChips(mode: ThemeMode) {
        themeChips.forEach { (item, chip) -> chip.isSelected = item == mode }
    }

    private fun buildAppearanceRows() {
        val current = ButtonAccentManager.current
        val row = addRow(
            container = binding.appearanceContainer,
            iconRes = R.drawable.ic_console_palette,
            title = getString(R.string.settings_button_color),
            desc = getString(R.string.settings_button_color_desc) + " · " + current.label,
            showSwitch = false,
            showChevron = true
        )
        row.settingIcon.imageTintList = ColorStateList.valueOf(current.startColor)
        row.root.setOnClickListener {
            Haptics.tap(row.root)
            ButtonAccentDialog.show(requireContext()) { style ->
                ButtonAccentManager.setStyle(requireContext(), style)
                requireActivity().recreate()
            }
        }
    }

    private fun buildRows() {
        val floatingRow = addRow(
            container = binding.visibilityContainer,
            iconRes = R.drawable.ic_wallet,
            title = getString(R.string.floating_switch_title),
            desc = getString(R.string.floating_switch_desc),
            showSwitch = true,
            showChevron = false
        )
        floatingSwitch = floatingRow.settingSwitch
        floatingRow.settingSwitch.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> MotionUtils.press(view)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> MotionUtils.release(view)
            }
            false
        }
        floatingRow.root.setOnClickListener { toggleFloatingSwitch() }
        floatingRow.settingSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingSwitch) {
                if (checked) requestFloating() else stopFloating()
            }
        }

        val overlayRow = addRow(
            container = binding.visibilityContainer,
            iconRes = R.drawable.ic_layers,
            title = getString(R.string.settings_overlay_permission),
            desc = getString(R.string.settings_overlay_missing),
            showSwitch = false,
            showChevron = true
        )
        overlayDesc = overlayRow.settingDesc
        overlayIcon = overlayRow.settingIcon
        overlayRow.root.setOnClickListener {
            (activity as? MainActivity)?.showOverlayPermissionGuide()
        }

        val notificationRow = addRow(
            container = binding.visibilityContainer,
            iconRes = R.drawable.ic_notification,
            title = getString(R.string.notification_permission_title),
            desc = getString(R.string.settings_notification_missing),
            showSwitch = false,
            showChevron = true
        )
        notificationDesc = notificationRow.settingDesc
        notificationIcon = notificationRow.settingIcon
        notificationRow.root.setOnClickListener {
            val main = activity as? MainActivity ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                main.requestNotificationPermission()
            } else {
                main.openNotificationSettings()
            }
        }

        val privacyRow = addRow(
            container = binding.aboutContainer,
            iconRes = R.drawable.ic_privacy,
            title = getString(R.string.settings_privacy),
            desc = getString(R.string.settings_privacy_desc),
            showSwitch = false,
            showChevron = true
        )
        privacyRow.root.setFlowEnabled(true)
        privacyRow.root.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyActivity::class.java))
        }
    }

    private fun observeFloatingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeState().collect { state ->
                    updatingSwitch = true
                    floatingSwitch?.isChecked = state?.floatingEnabled == true
                    updatingSwitch = false
                }
            }
        }
    }

    private fun toggleFloatingSwitch() {
        val switch = floatingSwitch ?: return
        updatingSwitch = true
        switch.isChecked = !switch.isChecked
        updatingSwitch = false
        if (switch.isChecked) requestFloating() else stopFloating()
    }

    private fun requestFloating() {
        val main = activity as? MainActivity ?: return
        if (!Settings.canDrawOverlays(requireContext())) {
            updatingSwitch = true
            floatingSwitch?.isChecked = false
            updatingSwitch = false
        }
        main.requestFloatingWindow()
    }

    private fun stopFloating() {
        (activity as? MainActivity)?.stopFloatingService()
    }

    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(requireContext())
        if (manager.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(requireContext(), BudgetWidgetProvider::class.java)
            val requested = runCatching {
                manager.requestPinAppWidget(provider, null, null)
            }.getOrDefault(false)
            if (requested) {
                Toast.makeText(requireContext(), R.string.widget_pin_requested, Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(requireContext(), R.string.widget_pin_unavailable, Toast.LENGTH_SHORT).show()
        (activity as? MainActivity)?.showWidgetGuide()
    }

    private fun addRow(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        desc: String,
        showSwitch: Boolean,
        showChevron: Boolean
    ): ItemSettingRowBinding {
        val row = ItemSettingRowBinding.inflate(layoutInflater, container, false)
        row.settingIcon.setImageResource(iconRes)
        row.settingTitle.text = title
        row.settingDesc.text = desc
        row.settingSwitch.visibility = if (showSwitch) View.VISIBLE else View.GONE
        row.settingChevron.visibility = if (showChevron) View.VISIBLE else View.GONE
        if (showSwitch) {
            row.root.isClickable = true
        }
        container.addView(row.root)
        return row
    }
}
