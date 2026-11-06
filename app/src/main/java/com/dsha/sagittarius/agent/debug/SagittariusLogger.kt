package com.dsha.sagittarius.agent.debug

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 最近 50 条 Agent 全链路日志；Debug UI 与 logcat 共用。 */
object SagittariusLogger {
    private const val MAX_ENTRIES = 50
    private val entries = ArrayDeque<String>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
    private val lock = Any()
    @Volatile
    private var logFile: File? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var publicLogUri: android.net.Uri? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        logFile = runCatching {
            File(context.getExternalFilesDir("logs") ?: context.filesDir, "agent.log")
        }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val name = "sagittarius-agent.log"
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(name),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        publicLogUri = android.content.ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(0)
                        )
                    }
                }
                if (publicLogUri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS
                        )
                    }
                    publicLogUri = context.contentResolver.insert(collection, values)
                }
            }
        }
    }

    fun add(tag: String, message: String) {
        val safe = sanitize(message)
        val line = "${format.format(Date())} [$tag] $safe"
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        runCatching {
            logFile?.parentFile?.mkdirs()
            logFile?.appendText(line + "\n")
        }
        writePublicSnapshot()
    }

    private fun writePublicSnapshot() {
        val context = appContext ?: return
        val uri = publicLogUri ?: return
        runCatching {
            val content = synchronized(lock) { entries.joinToString("\n") }
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun sanitize(value: String): String =
        value.replace(Regex("Bearer\\s+sk-[A-Za-z0-9_-]+"), "Bearer sk-***")
            .replace(Regex("(?i)(api[_-]?key\\\"?\\s*[:=]\\s*\\\")[^\\\"]+"), "$1***")
}
