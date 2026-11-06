package com.dsha.sagittarius.agent.llm

data class LlmError(
    val kind: Kind,
    val message: String,
    val retryable: Boolean = false,
    val requestId: String? = null
) {
    enum class Kind { AUTH, BALANCE, RATE_LIMIT, BAD_REQUEST, TOOLS_UNSUPPORTED, SERVER, NETWORK, CANCELLED, PARSE, UNKNOWN }
}
