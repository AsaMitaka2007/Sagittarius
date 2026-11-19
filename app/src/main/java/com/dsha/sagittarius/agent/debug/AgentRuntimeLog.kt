package com.dsha.sagittarius.agent.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 常驻内存运行日志：release 也记录，环形 500 条，自动脱敏 Key。
 * 终端页读取它，导出时再拼接为文本。
 */
object AgentRuntimeLog {

    enum class Level { INFO, TOOL, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val message: String
    ) {
        fun format(timeFormat: SimpleDateFormat): String =
            "[${timeFormat.format(Date(timestamp))}] ${level.name} $message"
    }

    private const val MAX = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
    private val buffer = ArrayDeque<Entry>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: Level, message: String) {
        val safe = redact(message)
        buffer.addLast(Entry(System.currentTimeMillis(), level, safe))
        while (buffer.size > MAX) buffer.removeFirst()
        _entries.value = buffer.toList()
    }

    fun info(message: String) = log(Level.INFO, message)
    fun tool(message: String) = log(Level.TOOL, message)
    fun error(message: String) = log(Level.ERROR, message)

    @Synchronized
    fun snapshot(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    fun format(entries: List<Entry>): String =
        entries.joinToString("\n") { it.format(timeFormat) }

    private fun redact(text: String): String = text
        .replace(Regex("""sk-[A-Za-z0-9_\-]{8,}"""), "sk-***")
        .replace(Regex("""Bearer\s+[A-Za-z0-9._\-]{8,}"""), "Bearer ***")
        .replace(Regex("""("?api[_-]?key"?\s*[:=]\s*"?)[^"\s,}]+""", RegexOption.IGNORE_CASE), "$1***")
}
