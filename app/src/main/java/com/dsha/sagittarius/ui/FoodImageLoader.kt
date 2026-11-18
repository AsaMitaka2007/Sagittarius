package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * 食物图片加载。
 *
 * 支持两种来源：
 * - `asset:food_images/xxx.jpg`：随 APK 打包的预置图片；
 * - 普通文件路径：用户裁剪后保存到应用目录的图片。
 */
object FoodImageLoader {
    private const val ASSET_PREFIX = "asset:"

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        cache.get(path)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = runCatching {
            if (path.startsWith(ASSET_PREFIX)) {
                context.assets.open(path.removePrefix(ASSET_PREFIX)).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } else {
                val file = File(path)
                if (!file.exists()) null else BitmapFactory.decodeFile(path, options)
            }
        }.getOrNull() ?: return null
        cache.put(path, bitmap)
        return bitmap
    }
}
