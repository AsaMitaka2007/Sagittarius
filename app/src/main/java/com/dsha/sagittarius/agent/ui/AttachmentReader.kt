package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * 聊天附件：图片转 data URL，文本/CSV/Word/Excel 提取纯文本。
 * 读取失败时返回 note，由调用方作为提示发给模型。
 */
data class ChatAttachment(
    val name: String,
    val mime: String,
    val text: String? = null,
    val imageBase64: String? = null,
    /** 压缩后的本地图片路径，供“设为食材图片”工具使用。 */
    val imagePath: String? = null,
    val note: String? = null
)

object AttachmentReader {
    private const val MAX_BYTES = 8 * 1024 * 1024L
    private const val MAX_TEXT = 12000
    private const val MAX_IMAGE = 1024

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    private val textExts = setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "log", "yml", "yaml")

    suspend fun read(context: Context, uri: Uri): ChatAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryName(context, uri)
        val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val size = querySize(context, uri)
        if (size != null && size > MAX_BYTES) {
            return@withContext ChatAttachment(name, mime, note = "文件超过 8MB，未读取")
        }
        runCatching {
            when {
                mime.startsWith("image/") || ext in imageExts -> readImage(context, uri, name, mime)
                ext == "docx" -> readDocx(context, uri, name, mime)
                ext == "xlsx" -> readXlsx(context, uri, name, mime)
                ext == "pdf" -> ChatAttachment(name, mime, note = "PDF 暂不支持提取文字")
                mime.startsWith("text/") || ext in textExts -> readText(context, uri, name, mime)
                else -> ChatAttachment(name, mime, note = "暂不支持解析此文件类型")
            }
        }.getOrElse {
            ChatAttachment(name, mime, note = "读取失败：${it.message ?: "未知错误"}")
        }
    }

    private fun readImage(context: Context, uri: Uri, name: String, mime: String): ChatAttachment {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_BYTES.toInt()) }
            ?: return ChatAttachment(name, mime, note = "无法打开图片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return ChatAttachment(name, mime, note = "图片解码失败")
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE || bounds.outHeight / sample > MAX_IMAGE) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return ChatAttachment(name, mime, note = "图片解码失败")
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        val jpeg = out.toByteArray()
        val base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val path = runCatching {
            val dir = File(context.cacheDir, "agent_attachments").apply { mkdirs() }
            File(dir, "attach_${System.nanoTime()}.jpg").also { it.writeBytes(jpeg) }.absolutePath
        }.getOrNull()
        return ChatAttachment(
            name,
            "image/jpeg",
            imageBase64 = "data:image/jpeg;base64,$base64",
            imagePath = path
        )
    }

    private fun readText(context: Context, uri: Uri, name: String, mime: String): ChatAttachment {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = stream.bufferedReader(Charsets.UTF_8)
            val buffer = CharArray(4096)
            val out = StringBuilder()
            while (out.length < MAX_TEXT) {
                val read = reader.read(buffer)
                if (read <= 0) break
                out.append(buffer, 0, read)
            }
            out.toString().take(MAX_TEXT)
        }.orEmpty()
        if (text.isBlank()) return ChatAttachment(name, mime, note = "文件是空的")
        return ChatAttachment(name, mime, text = text)
    }

    private fun readDocx(context: Context, uri: Uri, name: String, mime: String): ChatAttachment {
        val file = copyToCache(context, uri) ?: return ChatAttachment(name, mime, note = "无法读取文件")
        val xml = runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return@use ""
                zip.getInputStream(entry).bufferedReader().use { it.readText().take(MAX_TEXT * 8) }
            }
        }.getOrDefault("")
        file.delete()
        if (xml.isBlank()) return ChatAttachment(name, mime, note = "Word 文档没有可提取的文字")
        val text = xml.replace("</w:p>", "\n")
            .let { Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).findAll(it) }
            .joinToString("") { unescape(it.groupValues[1]) }
            .replace("\n\n\n", "\n\n")
            .take(MAX_TEXT)
        return ChatAttachment(name, mime, text = text)
    }

    private fun readXlsx(context: Context, uri: Uri, name: String, mime: String): ChatAttachment {
        val file = copyToCache(context, uri) ?: return ChatAttachment(name, mime, note = "无法读取文件")
        val shared = mutableListOf<String>()
        val sheet = runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { si ->
                        shared += Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(si.groupValues[1])
                            .joinToString("") { unescape(it.groupValues[1]) }
                    }
                }
                val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
                    ?: zip.entries().asSequence().firstOrNull {
                        it.name.startsWith("xl/worksheets/") && it.name.endsWith(".xml")
                    }
                    ?: return@use ""
                zip.getInputStream(sheetEntry).bufferedReader().use { it.readText().take(MAX_TEXT * 8) }
            }
        }.getOrDefault("")
        file.delete()
        if (sheet.isBlank()) return ChatAttachment(name, mime, note = "Excel 中没有可读取的工作表")
        val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            .findAll(sheet)
            .take(300)
            .map { row ->
                Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(row.groupValues[1])
                    .joinToString("\t") { cell ->
                        val attrs = cell.groupValues[1]
                        val body = cell.groupValues[2]
                        val raw = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                            .find(body)?.groupValues?.get(1)
                            ?: Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                                .find(body)?.groupValues?.get(1)
                            ?: ""
                        if (attrs.contains("t=\"s\"")) {
                            shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        } else {
                            unescape(raw)
                        }
                    }
            }
            .joinToString("\n")
            .take(MAX_TEXT)
        return ChatAttachment(name, mime, text = rows)
    }

    private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val file = File(context.cacheDir, "attach_${System.currentTimeMillis()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: return null
        file
    }.getOrNull()

    private fun queryName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        return uri.lastPathSegment ?: "附件"
    }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()

    private fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun java.io.InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (total < max) {
            val read = read(buffer)
            if (read <= 0) break
            val keep = minOf(read, max - total)
            out.write(buffer, 0, keep)
            total += keep
        }
        return out.toByteArray()
    }
}
