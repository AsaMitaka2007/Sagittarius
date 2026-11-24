package com.dsha.sagittarius.agent.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dsha.sagittarius.agent.tools.ports.DietPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 给食材库中的食物设置图片。
 *
 * 优先使用调用方给的 image_url；没有时用维基共享资源（Wikimedia Commons）搜索，
 * 下载后压缩保存到应用私有目录，并写回 foods.imageUri。
 * 不依赖额外图片服务或 API Key。
 */
class SetFoodImageTool(
    private val diet: DietPort,
    private val context: Context,
    private val attachmentImagesProvider: () -> List<String> = { emptyList() }
) : AgentTool {

    override val name = "set_food_image"
    override val description =
        "给食材设置或更换图片：可用聊天里用户上传的图片、图片 URL，或自动从维基共享资源搜索下载"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("food_name", buildJsonObject { put("type", "string") })
            put("use_attachment", buildJsonObject {
                put("type", "boolean")
                put("description", "true 时使用用户本轮上传的图片附件")
            })
            put("attachment_index", buildJsonObject {
                put("type", "integer")
                put("description", "使用第几张图片附件，从 0 开始，默认 0")
            })
            put("image_url", buildJsonObject {
                put("type", "string")
                put("description", "可选；http/https 图片地址。为空时自动搜索")
            })
            put("search_query", buildJsonObject {
                put("type", "string")
                put("description", "可选；自动搜索用的关键词，中文或英文均可")
            })
            put("overwrite", buildJsonObject {
                put("type", "boolean")
                put("description", "默认 true；传 false 时已有图片不会被替换")
            })
        })
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("food_name"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val foodName = args["food_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (foodName.isEmpty()) return@withContext fail("缺少食物名称")
        val food = diet.findFoodByName(foodName) ?: return@withContext fail("食材库里没有「$foodName」，请先用 add_food_to_library 添加")
        val overwrite = args["overwrite"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        if (!overwrite && !food.imageUri.isNullOrBlank()) {
            return@withContext fail("「$foodName」已有图片；如需更换请不要传 overwrite=false")
        }

        val useAttachment = args["use_attachment"]?.jsonPrimitive?.content
            ?.toBooleanStrictOrNull() ?: false
        val saved: File
        val credit: String
        if (useAttachment) {
            val index = (args["attachment_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                .coerceAtLeast(0)
            val path = attachmentImagesProvider().getOrNull(index)
                ?: return@withContext fail("本轮没有可用的图片附件，请让用户重新发送图片")
            saved = copyAttachment(path, food.id)
                ?: return@withContext fail("图片附件读取失败")
            credit = "用户上传的聊天图片"
        } else {
            val provided = args["image_url"]?.jsonPrimitive?.content?.trim().orEmpty()
            val query = args["search_query"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotEmpty() } ?: foodName
            val candidate = if (provided.isNotEmpty()) {
                ImageCandidate(provided, "用户指定")
            } else {
                searchCommons(query) ?: return@withContext fail("没有搜到「$query」的图片，请换一个搜索词或直接给图片 URL")
            }
            saved = downloadAndSave(candidate.url, food.id)
                ?: return@withContext fail("图片下载或解码失败")
            credit = candidate.credit
        }
        if (!diet.updateFoodImage(food.id, saved.absolutePath)) {
            return@withContext fail("写入食材图片失败")
        }
        val message = "已为「$foodName」设置图片" +
            if (credit.isNotBlank()) "（$credit）" else ""
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("name", food.name)
                put("id", food.id)
                put("image_path", saved.absolutePath)
                put("source", credit)
            }.toString(),
            message
        )
    }

    private data class ImageCandidate(val url: String, val credit: String)

    private fun searchCommons(query: String): ImageCandidate? {
        val encoded = URLEncoder.encode("filetype:bitmap $query", "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=6" +
            "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=900&format=json&formatversion=2"
        val body = runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
                .execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.string().orEmpty()
                }
        }.getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonArray ?: return null
        for (page in pages) {
            val info = page.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
            val imageUrl = info["thumburl"]?.jsonPrimitive?.content
                ?: info["url"]?.jsonPrimitive?.content
                ?: continue
            if (!imageUrl.startsWith("http") || imageUrl.endsWith(".svg", true)) continue
            val meta = info["extmetadata"]?.jsonObject
            val license = meta?.get("LicenseShortName")?.jsonObject
                ?.get("value")?.jsonPrimitive?.content.orEmpty()
            val artist = meta?.get("Artist")?.jsonObject
                ?.get("value")?.jsonPrimitive?.content.orEmpty()
                .replace(Regex("<[^>]+>"), "").trim()
            val credit = listOf("Wikimedia Commons", artist, license)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            return ImageCandidate(imageUrl, credit)
        }
        return null
    }

    private fun downloadAndSave(url: String, foodId: Long): File? {
        if (!isSafeUrl(url)) return null
        val bytes = runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
                .execute().use { response ->
                    if (!response.isSuccessful) return null
                    val type = response.header("Content-Type").orEmpty()
                    if (!type.startsWith("image/", true)) return null
                    response.body?.byteStream()?.readLimited(MAX_BYTES)
                }
        }.getOrNull() ?: return null
        val bitmap = decodeScaled(bytes) ?: return null
        return saveBitmap(bitmap, foodId)
    }

    /** 把聊天附件图片复制成食材库图片。 */
    private fun copyAttachment(path: String, foodId: Long): File? {
        val file = File(path)
        if (!file.exists()) return null
        val bitmap = decodeScaled(file.readBytes()) ?: return null
        return saveBitmap(bitmap, foodId)
    }

    private fun saveBitmap(bitmap: Bitmap, foodId: Long): File? {
        val dir = File(context.filesDir, "food_images").apply { mkdirs() }
        val target = File(dir, "food_${foodId}_${System.currentTimeMillis()}.jpg")
        val ok = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }.isSuccess
        bitmap.recycle()
        return target.takeIf { ok && it.length() > 0 }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE && bounds.outHeight / sample > MAX_IMAGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > max) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** 只允许 http/https，拒绝本机与常见内网地址，避免被当作内网探针。 */
    private fun isSafeUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return false
        val ip = host.split('.').mapNotNull { it.toIntOrNull() }
        if (ip.size == 4) {
            if (ip[0] == 10 || ip[0] == 127) return false
            if (ip[0] == 192 && ip[1] == 168) return false
            if (ip[0] == 169 && ip[1] == 254) return false
            if (ip[0] == 172 && ip[1] in 16..31) return false
        }
        return true
    }

    private fun fail(message: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", message) }.toString()
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val UA = "Sagittarius/1.0 (Android; food image)"
        const val MAX_BYTES = 6 * 1024 * 1024
        const val MAX_IMAGE = 900
    }
}
