package com.dsha.sagittarius.agent.llm

enum class ThinkingStyle { DEEPSEEK_EFFORT, TYPE_ONLY, NONE }

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val fallbackModels: List<String>,
    val supportsTools: Boolean,
    val thinkingStyle: ThinkingStyle,
    val canDisableThinking: Boolean,
    val echoReasoningContent: Boolean,
    val validateWithModelsEndpoint: Boolean
)

object ProviderRegistry {
    val all: List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-flash",
            fallbackModels = listOf("deepseek-flash", "deepseek-v4-pro"),
            supportsTools = true,
            thinkingStyle = ThinkingStyle.DEEPSEEK_EFFORT,
            canDisableThinking = true,
            echoReasoningContent = true,
            validateWithModelsEndpoint = true
        ),
        ProviderConfig(
            id = "zhipu",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-5.3-flash",
            fallbackModels = listOf("glm-5.3-flash", "glm-5.3", "glm-5.3-flashx"),
            supportsTools = true,
            thinkingStyle = ThinkingStyle.TYPE_ONLY,
            canDisableThinking = false,
            echoReasoningContent = true,
            validateWithModelsEndpoint = true
        ),
        ProviderConfig(
            id = "siliconflow",
            displayName = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen3-8B",
            fallbackModels = listOf("Qwen/Qwen3-8B"),
            supportsTools = true,
            thinkingStyle = ThinkingStyle.NONE,
            canDisableThinking = false,
            echoReasoningContent = false,
            validateWithModelsEndpoint = true
        ),
        ProviderConfig(
            id = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openrouter/auto",
            fallbackModels = listOf("openrouter/auto"),
            supportsTools = true,
            thinkingStyle = ThinkingStyle.NONE,
            canDisableThinking = false,
            echoReasoningContent = false,
            validateWithModelsEndpoint = true
        )
    )

    fun get(id: String): ProviderConfig? = all.firstOrNull { it.id == id }
}
