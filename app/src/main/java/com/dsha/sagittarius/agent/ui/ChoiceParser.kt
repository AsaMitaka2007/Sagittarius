package com.dsha.sagittarius.agent.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ChatChoice(val label: String, val value: String)

data class ChoiceParseResult(val text: String, val choices: List<ChatChoice>)

/** 解析 AI 的 [CHOICES] 选项块；流式未闭合时先截断不显示。 */
object ChoiceParser {
    private val block = Regex("\\[CHOICES\\]([\\s\\S]*?)\\[/CHOICES\\]", RegexOption.IGNORE_CASE)
    private val open = Regex("\\[CHOICES\\]", RegexOption.IGNORE_CASE)

    fun parse(source: String): ChoiceParseResult {
        val match = block.find(source)
        if (match == null) {
            val openIndex = open.find(source)?.range?.first
            val text = if (openIndex != null) source.substring(0, openIndex).trimEnd() else source
            return ChoiceParseResult(text, emptyList())
        }
        val body = match.groupValues[1].trim()
        val text = (source.substring(0, match.range.first) + source.substring(match.range.last + 1)).trim()
        return ChoiceParseResult(text, parseOptions(body))
    }

    private fun parseOptions(body: String): List<ChatChoice> {
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(body)
            val array: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> root["options"] as? JsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            return array.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val label = obj["label"]?.jsonPrimitive?.content?.trim().orEmpty()
                val value = obj["value"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (label.isEmpty()) null else ChatChoice(label, value.ifEmpty { label })
            }.take(4)
        }
        // 容错：每行 label|value
        return body.lineSequence().mapNotNull { line ->
            val text = line.trim().removePrefix("-").trim()
            if (text.isEmpty()) null else {
                val parts = text.split('|', limit = 2)
                val label = parts[0].trim()
                ChatChoice(label, parts.getOrNull(1)?.trim()?.ifEmpty { label } ?: label)
            }
        }.take(4).toList()
    }
}
