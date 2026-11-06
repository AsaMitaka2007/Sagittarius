package com.dsha.sagittarius.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsha.sagittarius.BuildConfig
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.agent.debug.LogcatDebugLogger
import com.dsha.sagittarius.agent.debug.NoOpAgentDebugLogger
import com.dsha.sagittarius.agent.engine.AgentEngine
import com.dsha.sagittarius.agent.engine.AgentEvent
import com.dsha.sagittarius.agent.engine.SystemPromptProvider
import com.dsha.sagittarius.agent.llm.DeepSeekBalance
import com.dsha.sagittarius.agent.llm.LlmClient
import com.dsha.sagittarius.agent.llm.ModelInfo
import com.dsha.sagittarius.agent.llm.OkHttpLlmClient
import com.dsha.sagittarius.agent.llm.ProviderConfig
import com.dsha.sagittarius.agent.llm.ProviderRegistry
import com.dsha.sagittarius.agent.llm.ThinkingStyle
import com.dsha.sagittarius.agent.engine.ChatMessage
import com.dsha.sagittarius.agent.data.SagittariusPrefs
import com.dsha.sagittarius.data.ChatHistoryEntity
import com.dsha.sagittarius.data.ChatSessionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import com.dsha.sagittarius.agent.tools.ToolRegistryFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class SagittariusViewModel(private val app: SagittariusApp) : ViewModel() {

    private val keyStore = app.apiKeyStore
    private val prefs = app.sagittariusPrefs
    private val idGen = AtomicLong(0)
    private var generateJob: Job? = null
    private var llmClient: LlmClient? = null
    private var engine: AgentEngine? = null
    private var toolsDisabled = false
    private val debugLogger = if (BuildConfig.DEBUG) LogcatDebugLogger else NoOpAgentDebugLogger
    private val approvalCounter = AtomicLong(0)
    private val pendingApprovals = mutableMapOf<Long, CompletableDeferred<Boolean>>()
    private val _approvalRequests = MutableSharedFlow<ToolApprovalRequest>(extraBufferCapacity = 4)
    val approvalRequests: SharedFlow<ToolApprovalRequest> = _approvalRequests.asSharedFlow()

    private val _messages = MutableStateFlow<List<ChatItem>>(emptyList())
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()
    private val _provider = MutableStateFlow(prefs.provider.value)
    val provider: StateFlow<String> = _provider.asStateFlow()
    private val _modelId = MutableStateFlow(prefs.model.value)
    val modelId: StateFlow<String> = _modelId.asStateFlow()
    private val _effort = MutableStateFlow(prefs.thinking.value)
    val effort: StateFlow<String> = _effort.asStateFlow()
    private val _supportedEfforts = MutableStateFlow(listOf("off", "low", "high"))
    val supportedEfforts: StateFlow<List<String>> = _supportedEfforts.asStateFlow()
    private val _forcedThinking = MutableStateFlow(false)
    val forcedThinking: StateFlow<Boolean> = _forcedThinking.asStateFlow()
    private val _totalTokens = MutableStateFlow(prefs.totalTokens.value)
    val totalTokens: StateFlow<Long> = _totalTokens.asStateFlow()
    private val _petVisible = MutableStateFlow(prefs.petVisible.value)
    val petVisible: StateFlow<Boolean> = _petVisible.asStateFlow()
    private val _petTextColor = MutableStateFlow(prefs.petTextColor.value)
    val petTextColor: StateFlow<Int> = _petTextColor.asStateFlow()
    private val _petScale = MutableStateFlow(prefs.petScale.value)
    val petScale: StateFlow<Int> = _petScale.asStateFlow()
    private val _petX = MutableStateFlow(prefs.petX.value)
    val petX: StateFlow<Float> = _petX.asStateFlow()
    private val _petY = MutableStateFlow(prefs.petY.value)
    val petY: StateFlow<Float> = _petY.asStateFlow()
    private val _petLocked = MutableStateFlow(prefs.petLocked.value)
    val petLocked: StateFlow<Boolean> = _petLocked.asStateFlow()
    private val _petPositionSet = MutableStateFlow(prefs.petPositionSet.value)
    val petPositionSet: StateFlow<Boolean> = _petPositionSet.asStateFlow()
    private val _balanceText = MutableStateFlow<String?>(null)
    val balanceText: StateFlow<String?> = _balanceText.asStateFlow()
    private val _balanceSupported = MutableStateFlow(false)
    val balanceSupported: StateFlow<Boolean> = _balanceSupported.asStateFlow()
    private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val sessions: StateFlow<List<ChatSessionEntity>> = _sessions.asStateFlow()
    private val _sessionId = MutableStateFlow(1L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()
    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private val _webSearchEnabled = MutableStateFlow(prefs.webSearchEnabled.value)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()
    private var hadToolInLastTurn = false

    init {
        _messages.value = listOf(
            ChatItem(
                id = idGen.incrementAndGet(),
                role = ChatItem.Role.ASSISTANT,
                content = prefs.personaGreeting.value.ifBlank { app.getString(R.string.agent_welcome) }
            )
        )
        viewModelScope.launch { prefs.webSearchEnabled.collect { _webSearchEnabled.value = it } }
        viewModelScope.launch { prefs.petVisible.collect { _petVisible.value = it } }
        viewModelScope.launch { prefs.totalTokens.collect { _totalTokens.value = it } }
        viewModelScope.launch { prefs.petTextColor.collect { _petTextColor.value = it } }
        viewModelScope.launch { prefs.petScale.collect { _petScale.value = it } }
        viewModelScope.launch { prefs.petX.collect { _petX.value = it } }
        viewModelScope.launch { prefs.petY.collect { _petY.value = it } }
        viewModelScope.launch { prefs.petLocked.collect { _petLocked.value = it } }
        viewModelScope.launch { prefs.petPositionSet.collect { _petPositionSet.value = it } }
        rebuildEngine()
        refreshBalance()
        loadModels()
        loadSessions()
    }

    private fun addWelcome() {
        _messages.value = listOf(
            ChatItem(
                id = idGen.incrementAndGet(),
                role = ChatItem.Role.ASSISTANT,
                content = prefs.personaGreeting.value.ifBlank { app.getString(R.string.agent_welcome) }
            )
        )
    }

    private fun loadSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            var all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            if (all.isEmpty()) {
                val id = runCatching {
                    app.chatSessionDao.insert(ChatSessionEntity(title = "新对话"))
                }.getOrDefault(1L)
                all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
                if (all.isEmpty()) all = listOf(ChatSessionEntity(id = id, title = "新对话"))
            }
            withContext(Dispatchers.Main) {
                _sessions.value = all
                val current = all.firstOrNull { it.id == _sessionId.value }?.id ?: all.first().id
                _sessionId.value = current
                loadHistory(current)
            }
        }
    }

    private fun loadHistory(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = runCatching { app.chatHistoryDao.recentForSession(sessionId, 60) }
                .getOrDefault(emptyList())
                .filter { it.role == "user" || it.role == "assistant" }
                .reversed()
            withContext(Dispatchers.Main) {
                if (_sessionId.value != sessionId) return@withContext
                engine?.clear()
                if (rows.isEmpty()) {
                    _messages.value = emptyList()
                    addWelcome()
                    return@withContext
                }
                var maxId = 0L
                val items = rows.map { row ->
                    maxId = maxOf(maxId, row.id)
                    ChatItem(
                        id = row.id,
                        role = if (row.role == "user") ChatItem.Role.USER else ChatItem.Role.ASSISTANT,
                        content = row.content
                    )
                }
                idGen.set(maxId + 1)
                _messages.value = items
                engine?.restoreHistory(items.map {
                    ChatMessage(
                        role = if (it.role == ChatItem.Role.USER) "user" else "assistant",
                        content = it.content
                    )
                })
            }
        }
    }

    fun switchSession(id: Long) {
        if (id == _sessionId.value) return
        stop()
        _sessionId.value = id
        _messages.value = emptyList()
        _tokenUsage.value = TokenUsage()
        _lastError.value = null
        loadHistory(id)
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.chatHistoryDao.deleteForSession(id)
                app.chatSessionDao.delete(id)
            }
            var all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            if (all.isEmpty()) {
                runCatching { app.chatSessionDao.insert(ChatSessionEntity(title = "新对话")) }
                all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            }
            withContext(Dispatchers.Main) {
                _sessions.value = all
                if (_sessionId.value == id) {
                    val next = all.first()
                    _sessionId.value = next.id
                    _messages.value = emptyList()
                    _tokenUsage.value = TokenUsage()
                    _lastError.value = null
                    loadHistory(next.id)
                }
            }
        }
    }

    fun newSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val id = runCatching {
                app.chatSessionDao.insert(ChatSessionEntity(title = "新对话"))
            }.getOrNull() ?: return@launch
            val all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                _sessions.value = all
                switchSession(id)
            }
        }
    }

    private fun maybeNameSession(firstUserText: String) {
        val current = _sessions.value.firstOrNull { it.id == _sessionId.value } ?: return
        if (current.title != "新对话") return
        val keyword = firstUserText.trim().take(12).ifBlank { "对话" }
        val title = "${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))} · $keyword"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.chatSessionDao.rename(current.id, title) }
            val all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { _sessions.value = all }
        }
    }

    private fun saveHistory(role: String, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        val session = _sessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.chatHistoryDao.insert(
                    ChatHistoryEntity(sessionId = session, role = role, content = text)
                )
                app.chatSessionDao.touch(session)
            }
            val all = runCatching { app.chatSessionDao.getAll() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { _sessions.value = all }
        }
    }

    private fun restoreEngineFromMessages() {
        engine?.restoreHistory(
            _messages.value
                .filter { it.role == ChatItem.Role.USER || it.role == ChatItem.Role.ASSISTANT }
                .map {
                    ChatMessage(
                        role = if (it.role == ChatItem.Role.USER) "user" else "assistant",
                        content = it.content
                    )
                }
        )
    }

    private fun config(): ProviderConfig {
        if (_provider.value == "custom") {
            val base = prefs.customBaseUrl.value.trim().ifBlank { "https://api.deepseek.com" }
            return ProviderConfig(
                id = "custom",
                displayName = "自定义",
                baseUrl = base,
                defaultModel = prefs.model.value,
                fallbackModels = listOf("deepseek-chat", "deepseek-reasoner"),
                supportsTools = true,
                thinkingStyle = ThinkingStyle.DEEPSEEK_EFFORT,
                canDisableThinking = true,
                echoReasoningContent = true,
                validateWithModelsEndpoint = false
            )
        }
        return ProviderRegistry.get(_provider.value) ?: ProviderRegistry.all.first()
    }

    private fun rebuildEngine() {
        val cfg = config()
        llmClient = OkHttpLlmClient(
            cfg.baseUrl,
            { keyStore.load(cfg.id) },
            debugLogger,
            cfg
        )
        engine = AgentEngine(
            llmClient!!,
            ToolRegistryFactory.default(
                app, app.repository, app.dietRepository,
                logger = debugLogger,
                bochaKeyProvider = { keyStore.load("bocha") }
            ),
            SystemPromptProvider(
                personaProvider = { prefs.personaDescription.value },
                livelinessProvider = { prefs.liveliness.value }
            ),
            debugLogger,
            permissionGate = { name, args -> requestToolPermission(name, args) },
            categoriesProvider = {
                app.repository.getCategories().map { it.name }
            }
        )
        _modelId.value = prefs.model.value
        _effort.value = prefs.thinking.value
        val fallback = cfg.fallbackModels.toMutableList()
        if (_modelId.value !in fallback) fallback.add(0, _modelId.value)
        _models.value = fallback.map { ModelInfo(it, it) }
        updateEfforts()
    }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = config()
            val loaded = runCatching { llmClient?.fetchModels().orEmpty() }.getOrDefault(emptyList())
                .ifEmpty { cfg.fallbackModels.map { ModelInfo(it, it) } }
            withContext(Dispatchers.Main) {
                val current = _modelId.value
                val merged = loaded.toMutableList()
                if (current.isNotBlank() && merged.none { it.id == current }) {
                    merged.add(0, ModelInfo(current, current))
                }
                _models.value = merged.ifEmpty { loaded }
                updateEfforts()
            }
        }
    }

    fun sendChoice(itemId: Long, choice: ChatChoice) {
        if (_generating.value) return
        val last = _messages.value.lastOrNull() ?: return
        if (last.id != itemId || last.role != ChatItem.Role.ASSISTANT) return
        send(choice.value)
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || _generating.value) return
        val engine = engine ?: return
        _lastError.value = null
        hadToolInLastTurn = false
        if (_messages.value.none { it.role == ChatItem.Role.USER }) maybeNameSession(input)
        _messages.value = _messages.value + ChatItem(idGen.incrementAndGet(), ChatItem.Role.USER, input)
        saveHistory("user", input)
        val assistantId = idGen.incrementAndGet()
        _messages.value = _messages.value + ChatItem(
            id = assistantId,
            role = ChatItem.Role.ASSISTANT,
            streaming = true
        )
        _generating.value = true
        generateJob = viewModelScope.launch {
            try {
                engine.send(
                    input,
                    _modelId.value,
                    _effort.value,
                    toolsEnabled = config().supportsTools && !toolsDisabled,
                    webSearchEnabled = _webSearchEnabled.value
                ).collect { event ->
                    when (event) {
                        is AgentEvent.Started -> Unit
                        is AgentEvent.ReasoningDelta -> updateAssistant(assistantId) {
                            it.copy(reasoning = it.reasoning + event.text)
                        }
                        is AgentEvent.TextDelta -> updateAssistant(assistantId) {
                            it.copy(content = it.content + event.text, error = null)
                        }
                        is AgentEvent.ToolStarted -> {
                            hadToolInLastTurn = true
                            updateAssistant(assistantId) {
                                it.copy(
                                    toolTraces = it.toolTraces + ToolTrace(
                                        name = event.name,
                                        args = event.args
                                    )
                                )
                            }
                        }
                        is AgentEvent.ToolFinished -> {
                            updateAssistant(assistantId) {
                                val traces = it.toolTraces.toMutableList()
                                val index = traces.indexOfLast { trace -> trace.name == event.name && !trace.done }
                                if (index >= 0) {
                                    traces[index] = traces[index].copy(
                                        display = event.result.display,
                                        result = event.result.contentForModel,
                                        failed = !event.result.ok,
                                        done = true
                                    )
                                }
                                it.copy(
                                    toolTraces = traces,
                                    toolFailed = event.result.let { r -> !r.ok }
                                )
                            }
                        }
                        is AgentEvent.ToolsUnsupported -> {
                            toolsDisabled = true
                        }
                        is AgentEvent.Usage -> {
                            val prompt = event.promptTokens ?: 0
                            val completion = event.completionTokens ?: 0
                            _tokenUsage.value = _tokenUsage.value + TokenUsage(prompt, completion)
                            val total = event.totalTokens ?: (prompt + completion)
                            if (total > 0) {
                                _totalTokens.value += total
                                viewModelScope.launch(Dispatchers.IO) { prefs.addTokens(total.toLong()) }
                            }
                        }
                        is AgentEvent.Finished -> updateAssistant(assistantId) {
                            it.copy(streaming = false, error = null)
                        }
                        is AgentEvent.Failed -> {
                            val friendly = friendlyError(event.error)
                            _lastError.value = friendly
                            updateAssistant(assistantId) {
                                it.copy(streaming = false, error = friendly)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                updateAssistant(assistantId) { it.copy(streaming = false, stopped = true) }
            } finally {
                _generating.value = false
                updateAssistant(assistantId) { it.copy(streaming = false) }
                val finalText = _messages.value.firstOrNull { it.id == assistantId }?.content.orEmpty()
                saveHistory("assistant", finalText)
            }
        }
    }

    private fun friendlyError(error: com.dsha.sagittarius.agent.llm.LlmError): String = when (error.kind) {
        com.dsha.sagittarius.agent.llm.LlmError.Kind.AUTH -> app.getString(R.string.agent_error_auth)
        com.dsha.sagittarius.agent.llm.LlmError.Kind.BALANCE -> app.getString(R.string.agent_error_balance)
        com.dsha.sagittarius.agent.llm.LlmError.Kind.NETWORK -> app.getString(R.string.agent_error_network)
        com.dsha.sagittarius.agent.llm.LlmError.Kind.RATE_LIMIT -> app.getString(R.string.agent_error_rate)
        com.dsha.sagittarius.agent.llm.LlmError.Kind.SERVER -> app.getString(R.string.agent_error_server)
        com.dsha.sagittarius.agent.llm.LlmError.Kind.PARSE -> app.getString(R.string.agent_error_parse)
        else -> app.getString(R.string.agent_error_bad)
    }

    private fun updateAssistant(id: Long, transform: (ChatItem) -> ChatItem) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    fun stop() {
        generateJob?.cancel()
        generateJob = null
        _generating.value = false
    }

    fun setToolPermission(mode: SagittariusPrefs.ToolPermissionMode) {
        viewModelScope.launch { prefs.setToolPermission(mode) }
    }

    fun respondApproval(id: Long, approved: Boolean) {
        pendingApprovals.remove(id)?.complete(approved)
    }

    private suspend fun requestToolPermission(name: String, args: String): Boolean {
        return when (prefs.toolPermission.value) {
            SagittariusPrefs.ToolPermissionMode.FULL -> true
            SagittariusPrefs.ToolPermissionMode.VIEW_ONLY -> name in READ_ONLY_TOOLS
            SagittariusPrefs.ToolPermissionMode.ASK -> {
                if (name in READ_ONLY_TOOLS) return true
                val id = approvalCounter.incrementAndGet()
                val deferred = CompletableDeferred<Boolean>()
                pendingApprovals[id] = deferred
                _approvalRequests.emit(ToolApprovalRequest(id, name, args))
                withTimeoutOrNull(60_000L) { deferred.await() } ?: false
            }
        }
    }

    data class ToolApprovalRequest(val id: Long, val toolName: String, val args: String)

    private val READ_ONLY_TOOLS = setOf(
        "search_food_cn_local",
        "get_cn_food_nutrition",
        "web_search",
        "query_app_data",
        "query_data",
        "list_categories",
        "toggle_network_search",
        "navigate_to_tab",
        "update_theme",
        "update_date_card_color",
        "control_weather_debug",
        "toggle_floating_window",
        "toggle_pet",
        "update_pet_config"
    )

    data class TokenUsage(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0
    ) {
        val total: Int get() = promptTokens + completionTokens
        operator fun plus(other: TokenUsage) = TokenUsage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens
        )
    }

    fun refreshPersonaGreeting() {
        val current = _messages.value
        if (current.size == 1 && current.first().role == ChatItem.Role.ASSISTANT) {
            _messages.value = listOf(
                current.first().copy(
                    content = prefs.personaGreeting.value.ifBlank {
                        app.getString(R.string.agent_welcome)
                    }
                )
            )
        }
    }

    fun deleteMessage(item: ChatItem) {
        if (item.role != ChatItem.Role.USER && item.role != ChatItem.Role.ASSISTANT) return
        _messages.value = _messages.value.filterNot { it.id == item.id }
        restoreEngineFromMessages()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.chatHistoryDeleteDao.deleteLatest(
                    _sessionId.value,
                    if (item.role == ChatItem.Role.USER) "user" else "assistant",
                    item.content
                )
            }
        }
    }

    fun clearConversation(showWelcome: Boolean = true) {
        stop()
        engine?.clear()
        _messages.value = emptyList()
        if (showWelcome) addWelcome()
        _lastError.value = null
        _tokenUsage.value = TokenUsage()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.chatHistoryDao.deleteForSession(_sessionId.value) }
        }
    }

    fun selectProvider(id: String) {
        if (id == _provider.value) return
        stop()
        _provider.value = id
        toolsDisabled = false
        viewModelScope.launch {
            prefs.setProvider(id)
            withContext(Dispatchers.Main) {
                rebuildEngine()
                restoreEngineFromMessages()
                _lastError.value = null
                loadModels()
                refreshBalance()
            }
        }
    }

    fun selectModel(id: String) {
        if (id == _modelId.value) return
        engine?.clear()
        toolsDisabled = false
        _modelId.value = id
        restoreEngineFromMessages()
        viewModelScope.launch { prefs.setModel(_provider.value, id) }
        updateEfforts()
    }

    fun selectEffort(value: String) {
        _effort.value = value
        viewModelScope.launch { prefs.setThinking(_provider.value, value) }
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
        viewModelScope.launch { prefs.setWebSearchEnabled(_webSearchEnabled.value) }
    }

    fun canRetry(): Boolean = !hadToolInLastTurn && _lastError.value != null

    fun retry() {
        val lastUser = _messages.value.lastOrNull { it.role == ChatItem.Role.USER }?.content ?: return
        clearConversation()
        send(lastUser)
    }

    fun reloadForKeyChange() {
        stop()
        viewModelScope.launch {
            _provider.value = prefs.provider.value
            _webSearchEnabled.value = prefs.webSearchEnabled.value
            rebuildEngine()
            restoreEngineFromMessages()
            _lastError.value = null
            loadModels()
            refreshBalance()
        }
    }

    fun setPetVisible(visible: Boolean) {
        _petVisible.value = visible
        viewModelScope.launch(Dispatchers.IO) { prefs.setPetVisible(visible) }
    }

    fun setPetTextColor(color: Int) {
        _petTextColor.value = color
        viewModelScope.launch(Dispatchers.IO) { prefs.setPetTextColor(color) }
    }

    fun setPetScale(percent: Int) {
        val safe = percent.coerceIn(50, 200)
        _petScale.value = safe
        viewModelScope.launch(Dispatchers.IO) { prefs.setPetScale(safe) }
    }

    fun setPetPosition(x: Float, y: Float) {
        _petX.value = x
        _petY.value = y
        viewModelScope.launch(Dispatchers.IO) { prefs.setPetPosition(x, y) }
    }

    fun setPetLocked(locked: Boolean) {
        _petLocked.value = locked
        viewModelScope.launch(Dispatchers.IO) { prefs.setPetLocked(locked) }
    }

    fun refreshBalance() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = config()
            val supported = cfg.id == "deepseek" || cfg.baseUrl.contains("api.deepseek.com")
            if (!supported) {
                withContext(Dispatchers.Main) {
                    _balanceSupported.value = false
                    _balanceText.value = null
                }
                return@launch
            }
            withContext(Dispatchers.Main) { _balanceSupported.value = true; _balanceText.value = "--" }
            val key = runCatching { keyStore.load(cfg.id) }.getOrNull()
            val balance = if (key.isNullOrBlank()) null else DeepSeekBalance.fetch(cfg.baseUrl, key)
            withContext(Dispatchers.Main) { _balanceText.value = balance ?: "--" }
        }
    }

    private fun updateEfforts() {
        val cfg = config()
        val model = _models.value.firstOrNull { it.id == _modelId.value }
        val levels = when (cfg.thinkingStyle) {
            ThinkingStyle.DEEPSEEK_EFFORT -> listOf("off", "low", "high", "max")
            ThinkingStyle.TYPE_ONLY -> if (cfg.canDisableThinking) listOf("off", "high") else listOf("high")
            ThinkingStyle.NONE -> listOf("off")
        }
        _forcedThinking.value = cfg.thinkingStyle == ThinkingStyle.TYPE_ONLY && !cfg.canDisableThinking
        if (_effort.value !in levels) _effort.value = levels.last()
        _supportedEfforts.value = levels
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    class Factory(private val app: SagittariusApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SagittariusViewModel(app) as T
    }
}
