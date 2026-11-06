package com.dsha.sagittarius.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

object FoodImageLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        cache.get(path)?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull() ?: return null
        cache.put(path, bitmap)
        return bitmap
    }
}
