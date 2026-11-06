package com.dsha.sagittarius.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Context.weatherDataStore by preferencesDataStore(name = "weather")

data class WeatherSettings(
    val selectedCityId: String = WeatherCities.FOLLOW_ID,
    val effectsEnabled: Boolean = true,
    val debugOverride: WeatherType? = null
)

data class WeatherState(
    val settings: WeatherSettings = WeatherSettings(),
    val type: WeatherType = WeatherType.CLOUDY,
    val cityName: String = "跟随定位",
    val temperatureC: Int? = null,
    val updatedAt: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val isDebug: Boolean = false
)

class WeatherRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val selectedKey = stringPreferencesKey("selected_city")
    private val effectsKey = booleanPreferencesKey("weather_effects_enabled")
    private val debugKey = stringPreferencesKey("debug_weather_override")
    private var cache: Cache? = null

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        scope.launch {
            val prefs = runCatching { appContext.weatherDataStore.data.first() }.getOrNull()
            val settings = WeatherSettings(
                selectedCityId = prefs?.get(selectedKey) ?: WeatherCities.FOLLOW_ID,
                effectsEnabled = prefs?.get(effectsKey) ?: true,
                debugOverride = prefs?.get(debugKey)?.let(::parseType)
            )
            _state.update {
                it.copy(settings = settings, cityName = cityLabel(settings.selectedCityId))
            }
            refresh(force = true)
        }
    }

    fun selectCity(cityId: String) {
        val city = WeatherCities.find(cityId) ?: return
        if (_state.value.settings.selectedCityId == cityId) return
        updateSettings { it.copy(selectedCityId = cityId) }
        _state.update { it.copy(cityName = city.name, loading = true) }
        scope.launch {
            appContext.weatherDataStore.edit { it[selectedKey] = cityId }
        }
        refresh(force = true)
    }

    fun setEffectsEnabled(enabled: Boolean) {
        updateSettings { it.copy(effectsEnabled = enabled) }
        scope.launch {
            appContext.weatherDataStore.edit { it[effectsKey] = enabled }
        }
    }

    fun setDebugOverride(type: WeatherType?) {
        updateSettings { it.copy(debugOverride = type) }
        scope.launch {
            appContext.weatherDataStore.edit {
                if (type == null) it.remove(debugKey) else it[debugKey] = type.name
            }
        }
        if (type != null) {
            _state.update {
                it.copy(
                    type = type,
                    isDebug = true,
                    loading = false,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
        } else {
            refresh(force = true)
        }
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            refreshMutex.withLock { doRefresh(force) }
        }
    }

    private fun updateSettings(transform: (WeatherSettings) -> WeatherSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
    }

    private suspend fun doRefresh(force: Boolean) {
        val settings = _state.value.settings
        val override = settings.debugOverride
        if (override != null) {
            _state.update {
                it.copy(
                    type = override,
                    isDebug = true,
                    loading = false,
                    error = null,
                    cityName = if (settings.selectedCityId == WeatherCities.FOLLOW_ID) {
                        "跟随定位"
                    } else {
                        cityLabel(settings.selectedCityId)
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
            return
        }

        val target = resolveTarget(settings.selectedCityId)
        if (!force) {
            val cached = cache
            if (cached != null && cached.selectedCityId == settings.selectedCityId &&
                System.currentTimeMillis() - cached.timestamp < CACHE_MS
            ) {
                _state.update {
                    it.copy(
                        type = cached.type,
                        cityName = cached.cityName,
                        temperatureC = cached.temperatureC,
                        updatedAt = cached.timestamp,
                        loading = false,
                        error = null,
                        isDebug = false
                    )
                }
                return
            }
        }

        _state.update {
            it.copy(
                loading = true,
                cityName = target.cityName,
                error = null,
                isDebug = false
            )
        }
        val result = runCatching { fetchOpenMeteo(target) }
        result.onSuccess { fetched ->
            if (_state.value.settings.debugOverride != null) return@onSuccess
            cache = Cache(
                selectedCityId = settings.selectedCityId,
                type = fetched.type,
                temperatureC = fetched.temperatureC,
                cityName = target.cityName,
                timestamp = System.currentTimeMillis()
            )
            _state.update {
                it.copy(
                    type = fetched.type,
                    cityName = target.cityName,
                    temperatureC = fetched.temperatureC,
                    updatedAt = System.currentTimeMillis(),
                    loading = false,
                    error = null
                )
            }
        }.onFailure { error ->
            val cached = cache
            _state.update {
                it.copy(
                    type = cached?.type ?: WeatherType.CLOUDY,
                    cityName = cached?.cityName ?: target.cityName,
                    temperatureC = cached?.temperatureC,
                    updatedAt = cached?.timestamp ?: 0L,
                    loading = false,
                    error = error.message ?: "天气获取失败"
                )
            }
        }
    }

    private fun resolveTarget(cityId: String): Target {
        if (cityId != WeatherCities.FOLLOW_ID) {
            val city = WeatherCities.find(cityId) ?: WeatherCities.defaultCity
            return Target(city.name, city.latitude, city.longitude)
        }
        if (!hasLocationPermission()) {
            return Target(WeatherCities.defaultCity.name, WeatherCities.defaultCity.latitude, WeatherCities.defaultCity.longitude)
        }
        val location = lastKnownLocation()
            ?: return Target(WeatherCities.defaultCity.name, WeatherCities.defaultCity.latitude, WeatherCities.defaultCity.longitude)
        val name = reverseGeocode(location.latitude, location.longitude)
            ?: WeatherCities.defaultCity.name
        return Target(name, location.latitude, location.longitude)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return runCatching {
            val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
        }.getOrNull()
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(appContext, Locale.CHINA)
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lon, 1).orEmpty()
            results.firstOrNull()?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun fetchOpenMeteo(target: Target): Fetched {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${target.lat}&longitude=${target.lon}" +
                "&current=temperature_2m,weather_code&timezone=auto"
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SagittariusWeather/1.0")
            if (connection.responseCode !in 200..299) {
                error("天气服务 HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val current = JSONObject(body).getJSONObject("current")
            val code = current.getInt("weather_code")
            val temp = current.getDouble("temperature_2m").toInt()
            Fetched(mapWeatherCode(code), temp)
        } finally {
            connection.disconnect()
        }
    }

    private fun mapWeatherCode(code: Int): WeatherType = when (code) {
        0, 1 -> WeatherType.SUNNY
        2, 3, 45, 48 -> WeatherType.CLOUDY
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        80, 81, 82, 95, 96, 99 -> WeatherType.RAINY
        71, 73, 75, 77, 85, 86 -> WeatherType.SNOWY
        else -> WeatherType.CLOUDY
    }

    private fun cityLabel(id: String): String =
        WeatherCities.find(id)?.name ?: WeatherCities.follow.name

    private fun parseType(value: String): WeatherType? =
        WeatherType.entries.firstOrNull { it.name == value }

    private data class Target(val cityName: String, val lat: Double, val lon: Double)
    private data class Fetched(val type: WeatherType, val temperatureC: Int)
    private data class Cache(
        val selectedCityId: String,
        val type: WeatherType,
        val temperatureC: Int?,
        val cityName: String,
        val timestamp: Long
    )

    companion object {
        private const val CACHE_MS = 30 * 60 * 1000L

        @Volatile
        private var instance: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}
