package com.dsha.sagittarius.agent.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.sagittariusDataStore by preferencesDataStore(name = "sagittarius_prefs")

object AgentPrefsKeys {
    val PROVIDER = stringPreferencesKey("provider_id")
    val API_KEY_CIPHER = stringPreferencesKey("api_key_cipher")
    val API_KEY_IV = stringPreferencesKey("api_key_iv")
    val MODEL = stringPreferencesKey("model_id")
    val EFFORT = stringPreferencesKey("reasoning_effort")
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
    val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
    val PERSONA_DESCRIPTION = stringPreferencesKey("persona_description")
    val PERSONA_GREETING = stringPreferencesKey("persona_greeting")
    val TOOL_PERMISSION = stringPreferencesKey("tool_permission")
    val LIVELINESS = stringPreferencesKey("ai_liveliness")
    val NEW_SESSION_ON_ENTER = booleanPreferencesKey("new_session_on_enter")
    val TOTAL_TOKENS = longPreferencesKey("total_tokens")
    fun model(provider: String) = stringPreferencesKey("model_id_$provider")
    fun thinking(provider: String) = stringPreferencesKey("thinking_$provider")
}

class SagittariusPrefs(private val context: Context) {
    private val _provider = MutableStateFlow("deepseek")
    private val _model = MutableStateFlow("deepseek-flash")
    private val _thinking = MutableStateFlow("off")
    private val _customBaseUrl = MutableStateFlow("")
    private val _webSearchEnabled = MutableStateFlow(false)
    private val _personaDescription = MutableStateFlow("")
    private val _personaGreeting = MutableStateFlow("")
    private val _toolPermission = MutableStateFlow(ToolPermissionMode.ASK)
    private val _liveliness = MutableStateFlow("high")
    private val _newSessionOnEnter = MutableStateFlow(true)
    private val _totalTokens = MutableStateFlow(0L)

    val provider: StateFlow<String> = _provider
    val model: StateFlow<String> = _model
    val thinking: StateFlow<String> = _thinking
    val customBaseUrl: StateFlow<String> = _customBaseUrl
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled
    val personaDescription: StateFlow<String> = _personaDescription
    val personaGreeting: StateFlow<String> = _personaGreeting
    val toolPermission: StateFlow<ToolPermissionMode> = _toolPermission
    val liveliness: StateFlow<String> = _liveliness
    val newSessionOnEnter: StateFlow<Boolean> = _newSessionOnEnter
    val totalTokens: StateFlow<Long> = _totalTokens

    init {
        runBlocking(Dispatchers.IO) {
            val prefs = runCatching { context.sagittariusDataStore.data.first() }.getOrNull()
            val provider = prefs?.get(AgentPrefsKeys.PROVIDER)
                ?.takeIf { it in setOf("deepseek", "zhipu", "siliconflow", "openrouter", "custom") }
                ?: "deepseek"
            _provider.value = provider
            val legacyModel = if (provider == "deepseek") prefs?.get(AgentPrefsKeys.MODEL) else null
            val legacyEffort = if (provider == "deepseek") prefs?.get(AgentPrefsKeys.EFFORT) else null
            _model.value = prefs?.get(AgentPrefsKeys.model(provider))
                ?: legacyModel
                ?: defaultModel(provider)
            _thinking.value = prefs?.get(AgentPrefsKeys.thinking(provider))
                ?: legacyEffort
                ?: defaultThinking(provider)
            _customBaseUrl.value = prefs?.get(AgentPrefsKeys.CUSTOM_BASE_URL).orEmpty()
            _webSearchEnabled.value = prefs?.get(AgentPrefsKeys.WEB_SEARCH_ENABLED) == true
            _personaDescription.value = prefs?.get(AgentPrefsKeys.PERSONA_DESCRIPTION).orEmpty()
            _personaGreeting.value = prefs?.get(AgentPrefsKeys.PERSONA_GREETING).orEmpty()
            _toolPermission.value = ToolPermissionMode.fromStorage(
                prefs?.get(AgentPrefsKeys.TOOL_PERMISSION)
            )
            _liveliness.value = prefs?.get(AgentPrefsKeys.LIVELINESS)
                ?.takeIf { it in setOf("off", "low", "high", "ultra") } ?: "high"
            _newSessionOnEnter.value = prefs?.get(AgentPrefsKeys.NEW_SESSION_ON_ENTER) ?: true
            _totalTokens.value = prefs?.get(AgentPrefsKeys.TOTAL_TOKENS) ?: 0L
            runCatching {
                context.sagittariusDataStore.edit { mutable ->
                    listOf(
                        "off_enabled",
                        "data_source_off",
                        "off_switch"
                    ).forEach { key -> mutable.remove(stringPreferencesKey(key)) }
                }
            }
        }
    }

    suspend fun setProvider(id: String) {
        val prefs = context.sagittariusDataStore.data.first()
        val model = prefs[AgentPrefsKeys.model(id)] ?: defaultModel(id)
        val thinking = prefs[AgentPrefsKeys.thinking(id)] ?: defaultThinking(id)
        context.sagittariusDataStore.edit {
            it[AgentPrefsKeys.PROVIDER] = id
            it[AgentPrefsKeys.model(id)] = model
            it[AgentPrefsKeys.thinking(id)] = thinking
        }
        _provider.value = id
        _model.value = model
        _thinking.value = thinking
    }

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        _webSearchEnabled.value = enabled
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.WEB_SEARCH_ENABLED] = enabled }
    }

    suspend fun setPersona(description: String, greeting: String) {
        _personaDescription.value = description.trim()
        _personaGreeting.value = greeting.trim()
        context.sagittariusDataStore.edit {
            it[AgentPrefsKeys.PERSONA_DESCRIPTION] = description.trim()
            it[AgentPrefsKeys.PERSONA_GREETING] = greeting.trim()
        }
    }

    suspend fun setLiveliness(value: String) {
        val safe = value.takeIf { it in setOf("off", "low", "high", "ultra") } ?: "high"
        _liveliness.value = safe
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.LIVELINESS] = safe }
    }

    suspend fun setNewSessionOnEnter(enabled: Boolean) {
        _newSessionOnEnter.value = enabled
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.NEW_SESSION_ON_ENTER] = enabled }
    }

    suspend fun addTokens(delta: Long) {
        if (delta <= 0) return
        val current = _totalTokens.value
        val next = current + delta
        _totalTokens.value = next
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.TOTAL_TOKENS] = next }
    }

    suspend fun setToolPermission(mode: ToolPermissionMode) {
        _toolPermission.value = mode
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.TOOL_PERMISSION] = mode.name }
    }

    suspend fun setCustomBaseUrl(value: String) {
        _customBaseUrl.value = value.trim()
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.CUSTOM_BASE_URL] = value.trim() }
    }

    suspend fun setModel(provider: String, id: String) {
        if (provider == _provider.value) _model.value = id
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.model(provider)] = id }
    }

    suspend fun setThinking(provider: String, value: String) {
        if (provider == _provider.value) _thinking.value = value
        context.sagittariusDataStore.edit { it[AgentPrefsKeys.thinking(provider)] = value }
    }

    suspend fun onboarded(): Boolean =
        context.sagittariusDataStore.data.first()[AgentPrefsKeys.ONBOARDED] == true

    private fun defaultModel(id: String): String = when (id) {
        "deepseek" -> "deepseek-flash"
        "zhipu" -> "glm-5.3-flash"
        "siliconflow" -> "Qwen/Qwen3-8B"
        "custom" -> "deepseek-chat"
        else -> "openrouter/auto"
    }

    private fun defaultThinking(id: String): String = when (id) {
        "zhipu" -> "high"
        else -> "off"
    }

    enum class ToolPermissionMode(val label: String) {
        VIEW_ONLY("仅可查看"),
        ASK("人工批准"),
        FULL("完全权限");

        companion object {
            fun fromStorage(value: String?): ToolPermissionMode =
                entries.firstOrNull { it.name == value } ?: ASK
        }
    }

    companion object {
        const val DEFAULT_PROVIDER = "deepseek"
    }
}
