package com.dsha.sagittarius.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/** 自定义裁剪：相册选择后单指拖动、双指缩放，按指定比例保存。 */
class ImageCropActivity : ComponentActivity() {

    private var aspectX = 3
    private var aspectY = 1
    private var forConsole = false
    private var prefix = "food"
    private lateinit var cropView: CropImageView

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        showCrop(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aspectX = intent.getIntExtra(EXTRA_ASPECT_X, 3).coerceAtLeast(1)
        aspectY = intent.getIntExtra(EXTRA_ASPECT_Y, 1).coerceAtLeast(1)
        forConsole = intent.getBooleanExtra(EXTRA_FOR_CONSOLE, false)
        prefix = intent.getStringExtra(EXTRA_PREFIX) ?: "food"
        if (savedInstanceState == null) picker.launch("image/*")
    }

    private fun showCrop(uri: Uri) {
        val bitmap = decode(uri)
        if (bitmap == null) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        cropView = CropImageView(this).apply {
            setAspectRatio(aspectX, aspectY)
            setBitmap(bitmap)
        }
        root.addView(cropView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val top = TextView(this).apply {
            text = "拖动 / 双指缩放  ·  取消"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(18f), dp(18f), dp(18f), dp(12f))
            setOnClickListener { finish() }
        }
        root.addView(top, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
        val bottom = FrameLayout(this)
        val reset = TextView(this).apply {
            text = "重置"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(Color.argb(90, 255, 255, 255))
            }
            setOnClickListener { cropView.reset() }
        }
        val done = TextView(this).apply {
            text = "完成"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(ThemeTokens.of(this@ImageCropActivity).gradientStart, ThemeTokens.of(this@ImageCropActivity).gradientMid)
            ).apply { cornerRadius = dp(16f).toFloat() }
            setOnClickListener { saveCrop() }
        }
        bottom.addView(reset, FrameLayout.LayoutParams(dp(96f), dp(46f), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(20f)
        })
        bottom.addView(done, FrameLayout.LayoutParams(dp(150f), dp(46f), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(20f)
        })
        root.addView(bottom, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(88f), Gravity.BOTTOM
        ))
        setContentView(root)
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun saveCrop() {
        val cropped = cropView.cropBitmap() ?: run { finish(); return }
        val dir = File(filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            file.outputStream().use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        }.getOrDefault(false)
        if (!saved) {
            finish()
            return
        }
        if (forConsole) DateConsoleImageManager.setPath(this, file.absolutePath)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OUTPUT_PATH, file.absolutePath))
        finish()
    }

    companion object {
        const val EXTRA_ASPECT_X = "aspect_x"
        const val EXTRA_ASPECT_Y = "aspect_y"
        const val EXTRA_FOR_CONSOLE = "for_console"
        const val EXTRA_PREFIX = "prefix"
        const val EXTRA_OUTPUT_PATH = "output_path"

        fun intent(context: android.content.Context, aspectX: Int, aspectY: Int, forConsole: Boolean, prefix: String): Intent =
            Intent(context, ImageCropActivity::class.java)
                .putExtra(EXTRA_ASPECT_X, aspectX)
                .putExtra(EXTRA_ASPECT_Y, aspectY)
                .putExtra(EXTRA_FOR_CONSOLE, forConsole)
                .putExtra(EXTRA_PREFIX, prefix)
    }
}
