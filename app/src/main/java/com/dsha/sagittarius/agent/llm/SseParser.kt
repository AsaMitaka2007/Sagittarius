package com.dsha.sagittarius.agent.llm

/** 手工 SSE 行解析：处理 CRLF、半包、空行与注释。 */
class SseParser {
    private val buffer = StringBuilder()

    fun feed(chunk: String): List<String> {
        buffer.append(chunk)
        val result = mutableListOf<String>()
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) break
            val line = buffer.substring(0, newline).trimEnd('\r')
            buffer.delete(0, newline + 1)
            parseLine(line)?.let(result::add)
        }
        return result
    }

    fun finish(): List<String> {
        if (buffer.isEmpty()) return emptyList()
        val line = buffer.toString().trimEnd('\r')
        buffer.clear()
        return listOfNotNull(parseLine(line))
    }

    private fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null
        return trimmed.removePrefix("data:").trimStart()
    }
}
