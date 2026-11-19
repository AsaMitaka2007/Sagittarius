package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

/** 从打包的星空视频中取一帧，作为最高档进度条的底图。 */
object StarrySkyFrameCache {
    private var bitmap: Bitmap? = null
    private var failed = false

    @Synchronized
    fun get(context: Context): Bitmap? {
        bitmap?.let { return it }
        if (failed) return null
        return runCatching {
            context.assets.openFd("starry_sky.mp4").use { afd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }
        }.onFailure { failed = true }.getOrNull()?.also { bitmap = it }
    }
}
