package com.dsha.sagittarius.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.weather.WeatherCities
import com.dsha.sagittarius.data.weather.WeatherCity
import com.dsha.sagittarius.data.weather.WeatherRepository
import com.dsha.sagittarius.data.weather.WeatherState
import com.dsha.sagittarius.data.weather.WeatherType
import com.dsha.sagittarius.databinding.FragmentWeatherBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: WeatherRepository
    private var updatingSwitch = false
    private var requestedLocation = false

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            repository.refresh(force = true)
        } else {
            Toast.makeText(requireContext(), R.string.weather_location_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).weatherRepository
        binding.dateConsole.bindWeather(
            WeatherCities.all,
            repository.state.value.settings.selectedCityId
        ) { city -> chooseCity(city) }
        binding.cityCard.setOnClickListener {
            WeatherCityPickerSheet.show(
                requireContext(),
                repository.state.value.settings.selectedCityId
            ) { city -> chooseCity(city) }
        }
        binding.btnRefreshWeather.setOnClickListener {
            repository.refresh(force = true)
        }
        var pullStartY = 0f
        var pullTriggered = false
        binding.root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pullStartY = event.y
                    pullTriggered = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!pullTriggered && binding.root.scrollY == 0 &&
                        event.y - pullStartY > 88f * resources.displayMetrics.density
                    ) {
                        pullTriggered = true
                        repository.refresh(force = true)
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> pullTriggered = false
            }
            false
        }
        binding.switchWeatherEffects.setOnCheckedChangeListener { _, checked ->
            if (!updatingSwitch) repository.setEffectsEnabled(checked)
        }
        binding.btnWeatherDebug.setOnClickListener {
            WeatherDebugSheet.show(
                requireContext(),
                repository.state.value.settings.debugOverride
            ) { type -> repository.setDebugOverride(type) }
        }
        observeWeather()
        maybeRequestLocation()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun observeWeather() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: WeatherState) {
        val selected = WeatherCities.find(state.settings.selectedCityId)
        binding.tvCity.text = if (state.settings.selectedCityId == WeatherCities.FOLLOW_ID) {
            state.cityName
        } else {
            selected?.name ?: state.cityName
        }
        binding.dateConsole.setWeatherCity(
            state.settings.selectedCityId,
            if (state.settings.selectedCityId == WeatherCities.FOLLOW_ID) state.cityName else null
        )

        val (emoji, status) = when (state.type) {
            WeatherType.SUNNY -> "☀️" to getString(R.string.weather_status_sunny)
            WeatherType.RAINY -> "🌧️" to getString(R.string.weather_status_rainy)
            WeatherType.SNOWY -> "❄️" to getString(R.string.weather_status_snowy)
            WeatherType.CLOUDY -> "☁️" to getString(R.string.weather_status_cloudy)
        }
        binding.tvWeatherIcon.text = emoji
        binding.tvWeatherStatus.text = status
        binding.tvWeatherTemp.text = state.temperatureC?.let { "$it°C" } ?: "--"
        binding.tvWeatherUpdated.text = if (state.loading) {
            getString(R.string.weather_loading)
        } else if (state.updatedAt > 0L) {
            val time = Instant.ofEpochMilli(state.updatedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
            getString(R.string.weather_updated, time)
        } else {
            ""
        }
        binding.tvWeatherError.isVisible = state.error != null
        binding.tvWeatherError.text = state.error.orEmpty()
        binding.weatherPreview.setWeather(state.type, state.settings.effectsEnabled)

        updatingSwitch = true
        binding.switchWeatherEffects.isChecked = state.settings.effectsEnabled
        updatingSwitch = false
        binding.btnWeatherDebug.text = if (state.isDebug) {
            "${getString(R.string.weather_debug)} · ${getString(R.string.weather_debug_active)}"
        } else {
            getString(R.string.weather_debug)
        }
    }

    private fun chooseCity(city: WeatherCity) {
        repository.selectCity(city.id)
        if (city.id == WeatherCities.FOLLOW_ID) {
            maybeRequestLocation(force = true)
        }
    }

    private fun maybeRequestLocation(force: Boolean = false) {
        if (repository.state.value.settings.selectedCityId != WeatherCities.FOLLOW_ID) return
        if (hasLocationPermission()) {
            if (force) repository.refresh(force = true)
            return
        }
        if (requestedLocation && !force) return
        requestedLocation = true
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}
