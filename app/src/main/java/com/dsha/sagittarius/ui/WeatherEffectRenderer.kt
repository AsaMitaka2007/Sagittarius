package com.dsha.sagittarius.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.dsha.sagittarius.data.weather.WeatherType
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random

/**
 * 顶栏/悬浮窗的天气微动效。雨丝、水珠、云层、噪点均在初始化时创建
 * Bitmap/Shader/Paint；绘制期只做坐标变换，避免每帧产生新对象。
 *
 * highQuality=false（低端机/减少动画）时关闭模糊、混合模式、噪点和
 * 多层水珠，只保留轻量渐变。
 */
class WeatherEffectRenderer(
    private val density: Float,
    private val lightTheme: Boolean,
    private val allowBlur: Boolean
) {

    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.32f, -0.38f, 1.55f,
            intArrayOf(
                Color.argb(58, 255, 255, 255),
                Color.argb(20, 215, 238, 252),
                Color.argb(34, 105, 152, 192),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.28f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val dropHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.22f, -0.38f, 0.38f,
            intArrayOf(Color.argb(235, 255, 255, 255), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }
    private val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(10, 25, 55, 85)
    }
    private val dropRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.07f
        color = Color.argb(110, 220, 242, 255)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudLightShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(62, 108, 132, 162), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    private val cloudDarkShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(72, 24, 32, 60), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    private val cloudRimShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(26, 255, 255, 255), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    private val cloudRimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunUnitShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(40, 255, 241, 190), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    private val tyndallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(62, 255, 249, 226), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }
    private val trailUnitShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(Color.TRANSPARENT, Color.argb(26, 210, 235, 255)),
        null,
        Shader.TileMode.CLAMP
    )
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.75f * density
    }
    private val snowPath = Path()
    private val frostTopPath = Path()
    private val frostSidePath = Path()
    private val frostEdgeShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(78, 245, 252, 255), Color.argb(26, 190, 226, 255), Color.TRANSPARENT),
        floatArrayOf(0f, 0.52f, 1f),
        Shader.TileMode.CLAMP
    )
    private val frostEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val frostLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.75f * density
    }
    private val iceDustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainBitmap: Bitmap? = if (allowBlur) createNoiseBitmap(64) else null
    private val dropPath = Path().apply { buildTeardrop(this) }
    private val capsulePath = Path()
    private val rightEdgePath = Path()
    private val leftEdgePath = Path()
    private val rightMeasure = android.graphics.PathMeasure()
    private val leftMeasure = android.graphics.PathMeasure()
    private val rainPosition = FloatArray(2)
    private val markPos1 = FloatArray(2)
    private val markTan1 = FloatArray(2)
    private val markPos2 = FloatArray(2)
    private val markTan2 = FloatArray(2)
    private val rainTangent = FloatArray(2)
    private val rainDrops = Array(5) { RainDrop() }
    private val trailMarks = Array(64) { TrailMark() }
    private val splashParticles = Array(12) { SplashParticle() }
    private var trailMarkCursor = 0
    private var splashCursor = 0
    private val rainRandom = Random(17)
    private var rainWidth = 0f
    private var rainHeight = 0f
    private var rainCorner = 0f
    private var lastRainTime = -1f
    private val cloudRect = RectF()
    private val trailRect = RectF()

    init {
        grainBitmap?.let {
            grainPaint.shader = BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            grainPaint.alpha = if (lightTheme) 9 else 7
        }
    }

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        corner: Float,
        type: WeatherType,
        time: Float,
        compact: Boolean,
        reducedMotion: Boolean,
        lowQuality: Boolean = false
    ) {
        val t = if (reducedMotion) 0f else time
        if (!lowQuality) {
            grainBitmap?.let {
                grainPaint.alpha = if (lightTheme) 8 else 6
                canvas.drawRect(bounds, grainPaint)
            }
        }
        when (type) {
            WeatherType.RAINY -> drawRain(canvas, bounds, corner, t, compact, lowQuality)
            WeatherType.SNOWY -> drawSnow(canvas, bounds, corner, t, compact, lowQuality)
            WeatherType.SUNNY -> drawSun(canvas, bounds, t, compact)
            WeatherType.CLOUDY -> drawCloud(canvas, bounds, t, compact, lowQuality)
        }
    }

    private fun drawRain(canvas: Canvas, bounds: RectF, corner: Float, time: Float, compact: Boolean, lowQuality: Boolean) {
        buildRainPaths(bounds, corner)
        val dt = if (lastRainTime < 0f || time < lastRainTime) 0f else (time - lastRainTime).coerceIn(0f, 0.05f)
        lastRainTime = time
        updateTrailMarks(dt)
        updateSplash(dt)
        if (!lowQuality) drawTrailMarks(canvas)
        val count = if (lowQuality) 3 else if (compact) 4 else 5
        for (i in 0 until count) {
            val drop = rainDrops[i]
            if (!drop.active) resetRainDrop(drop, bounds)
            drop.speed += dp(28f + drop.radius * 2.6f) * dt
            drop.distance += drop.speed * dt
            val measure = if (drop.rightSide) rightMeasure else leftMeasure
            val length = measure.length
            if (length <= 0f) continue
            val endDistance = drop.detachDistance.coerceIn(0f, length)
            if (drop.distance >= endDistance) {
                measure.getPosTan(endDistance, rainPosition, rainTangent)
                if (!lowQuality) {
                    addTrailMark(drop.rightSide, (endDistance - drop.radius * 2f).coerceAtLeast(0f), endDistance, drop.radius)
                }
                // 不在底边立起主水滴；直接碎成 4 颗透明水花碎片。
                spawnSplash(rainPosition[0], bounds.bottom - dp(1f), drop.radius)
                drop.active = false
                continue
            }
            measure.getPosTan(drop.distance.coerceAtMost(endDistance), rainPosition, rainTangent)
            if (!lowQuality) {
                if (drop.distance - drop.lastMarkDistance > dp(5f)) {
                    addTrailMark(drop.rightSide, drop.lastMarkDistance, drop.distance, drop.radius)
                    drop.lastMarkDistance = drop.distance
                }
            }
            val stretch = (1f + drop.speed / dp(210f)).coerceIn(1f, 1.65f)
            drawRainDrop(
                canvas, rainPosition[0], rainPosition[1], drop.radius, drop.speed,
                bounds, stretch, lowQuality,
                tangentX = rainTangent[0], tangentY = rainTangent[1]
            )
        }
        drawSplashParticles(canvas)
    }

    private fun addTrailMark(rightSide: Boolean, start: Float, end: Float, radius: Float) {
        val mark = trailMarks[trailMarkCursor]
        trailMarkCursor = (trailMarkCursor + 1) % trailMarks.size
        mark.active = true
        mark.rightSide = rightSide
        mark.start = start
        mark.end = end
        mark.radius = radius
        mark.age = 0f
    }

    private fun updateTrailMarks(dt: Float) {
        if (dt <= 0f) return
        trailMarks.forEach { mark ->
            if (!mark.active) return@forEach
            mark.age += dt
            if (mark.age > 3.2f) mark.active = false
        }
    }

    private fun drawTrailMarks(canvas: Canvas) {
        trailMarks.forEach { mark ->
            if (!mark.active) return@forEach
            val measure = if (mark.rightSide) rightMeasure else leftMeasure
            if (measure.length <= 0f) return@forEach
            val start = mark.start.coerceIn(0f, measure.length)
            val end = mark.end.coerceIn(0f, measure.length)
            if (end <= start) return@forEach
            measure.getPosTan(start, markPos1, markTan1)
            measure.getPosTan(end, markPos2, markTan2)
            val fade = (1f - mark.age / 3.0f).coerceIn(0f, 1f)
            if (fade <= 0f) return@forEach
            val nx1 = -markTan1[1]
            val ny1 = markTan1[0]
            val nx2 = -markTan2[1]
            val ny2 = markTan2[0]
            val half = mark.radius * 0.82f
            trailPaint.style = Paint.Style.STROKE
            trailPaint.strokeWidth = dp(0.72f)
            trailPaint.strokeCap = Paint.Cap.ROUND
            trailPaint.color = Color.argb(
                ((if (lightTheme) 48 else 62) * fade).toInt().coerceIn(0, 255),
                200, 234, 255
            )
            // 只画水迹两侧的亮边，中心保持透明，形成玻璃上薄薄的水膜。
            canvas.drawLine(
                markPos1[0] + nx1 * half, markPos1[1] + ny1 * half,
                markPos2[0] + nx2 * half, markPos2[1] + ny2 * half,
                trailPaint
            )
            canvas.drawLine(
                markPos1[0] - nx1 * half, markPos1[1] - ny1 * half,
                markPos2[0] - nx2 * half, markPos2[1] - ny2 * half,
                trailPaint
            )
            trailPaint.style = Paint.Style.FILL
        }
    }

    private fun spawnSplash(x: Float, y: Float, radius: Float) {
        repeat(4) { index ->
            val p = splashParticles[splashCursor]
            splashCursor = (splashCursor + 1) % splashParticles.size
            p.active = true
            p.x = x
            p.y = y
            p.vx = dp((index - 1.5f) * 7f + rainRandom.nextFloat() * 10f - 5f)
            p.vy = -dp(8f + rainRandom.nextFloat() * 26f)
            p.radius = dp(0.45f + rainRandom.nextFloat() * 0.55f) * (0.75f + radius / dp(8f) * 0.25f)
            p.life = 0.45f + rainRandom.nextFloat() * 0.14f
        }
    }

    private fun updateSplash(dt: Float) {
        if (dt <= 0f) return
        splashParticles.forEach { p ->
            if (!p.active) return@forEach
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += dp(220f) * dt
            p.life -= dt
            if (p.life <= 0f) p.active = false
        }
    }

    private fun drawSplashParticles(canvas: Canvas) {
        splashParticles.forEach { p ->
            if (!p.active) return@forEach
            splashPaint.color = Color.argb(
                (120 * (p.life / 0.5f)).toInt().coerceIn(0, 255),
                230, 246, 255
            )
            canvas.drawCircle(p.x, p.y, p.radius, splashPaint)
        }
    }

    private fun buildRainPaths(bounds: RectF, corner: Float) {
        if (bounds.width() == rainWidth && bounds.height() == rainHeight && corner == rainCorner) return
        rainWidth = bounds.width()
        rainHeight = bounds.height()
        rainCorner = corner
        val c = corner.coerceIn(0f, minOf(bounds.width(), bounds.height()) / 2f)
        capsulePath.reset()
        capsulePath.addRoundRect(bounds, c, c, Path.Direction.CW)

        rightEdgePath.reset()
        rightEdgePath.moveTo(bounds.centerX(), bounds.top)
        rightEdgePath.lineTo(bounds.right - c, bounds.top)
        rightEdgePath.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + c)
        rightEdgePath.lineTo(bounds.right, bounds.bottom - c)
        rightEdgePath.quadTo(bounds.right, bounds.bottom, bounds.right - c, bounds.bottom)
        rightEdgePath.lineTo(bounds.centerX(), bounds.bottom)
        rightMeasure.setPath(rightEdgePath, false)

        leftEdgePath.reset()
        leftEdgePath.moveTo(bounds.centerX(), bounds.top)
        leftEdgePath.lineTo(bounds.left + c, bounds.top)
        leftEdgePath.quadTo(bounds.left, bounds.top, bounds.left, bounds.top + c)
        leftEdgePath.lineTo(bounds.left, bounds.bottom - c)
        leftEdgePath.quadTo(bounds.left, bounds.bottom, bounds.left + c, bounds.bottom)
        leftEdgePath.lineTo(bounds.centerX(), bounds.bottom)
        leftMeasure.setPath(leftEdgePath, false)

        rainDrops.forEach { it.active = false }
        trailMarks.forEach { it.active = false }
        splashParticles.forEach { it.active = false }
        lastRainTime = -1f
    }

    private fun resetRainDrop(drop: RainDrop, bounds: RectF) {
        drop.active = true
        drop.falling = false
        drop.rightSide = rainRandom.nextBoolean()
        drop.radius = dp(3f + rainRandom.nextFloat() * 5f)
        drop.speed = dp(14f + rainRandom.nextFloat() * 8f)
        drop.distance = rainRandom.nextFloat() * (bounds.right - bounds.centerX()) * 0.55f
        val measure = if (drop.rightSide) rightMeasure else leftMeasure
        val bottomHalf = (bounds.right - bounds.centerX()).coerceAtLeast(1f)
        drop.detachDistance = if (measure.length > 0f) {
            (measure.length - rainRandom.nextFloat() * bottomHalf * 0.92f)
                .coerceIn(0f, measure.length)
        } else {
            0f
        }
        drop.hang = 0f
        drop.splash = 0f
        drop.lastMarkDistance = drop.distance
        drop.fallX = 0f
        drop.fallY = 0f
        drop.fallVy = 0f
    }

    private fun drawRainDrop(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        speed: Float,
        bounds: RectF,
        stretch: Float,
        lowQuality: Boolean,
        hanging: Boolean = false,
        falling: Boolean = false,
        vertical: Boolean = false,
        tangentX: Float = 0f,
        tangentY: Float = 1f
    ) {
        val edge = edgeFactor(x, bounds)
        val rimBoost = 1f + edge * 0.55f
        val ry = radius * stretch
        if (lowQuality) {
            dropPaint.alpha = if (falling) 130 else 210
            canvas.drawCircle(x, y, radius, dropPaint)
            return
        }
        val angle = if (vertical || falling) {
            0f
        } else {
            Math.toDegrees(atan2(tangentY.toDouble(), tangentX.toDouble())).toFloat() - 90f
        }
        canvas.save()
        // 悬挂时把水珠主体收回到胶囊内，只让圆润液滴贴在底边，避免整颗水珠被裁掉后只剩一条黑边。
        canvas.translate(x, if (hanging) y - ry * 1.02f else y)
        canvas.rotate(angle)
        canvas.scale(radius, ry)
        dropPaint.alpha = (220 - (speed / dp(260f)).coerceIn(0f, 0.4f) * 70f).toInt().coerceIn(140, 220)
        canvas.drawPath(dropPath, dropPaint)
        dropHighlightPaint.alpha = (215 * rimBoost).toInt().coerceIn(0, 255)
        canvas.drawCircle(-0.28f, -0.46f, 0.16f, dropHighlightPaint)
        dropHighlightPaint.alpha = (130 * rimBoost).toInt().coerceIn(0, 255)
        canvas.drawCircle(-0.02f, -0.55f, 0.09f, dropHighlightPaint)
        dropRimPaint.alpha = (160 * rimBoost).toInt().coerceIn(0, 255)
        dropRimPaint.color = Color.WHITE
        canvas.drawPath(dropPath, dropRimPaint)
        canvas.restore()
    }

    private fun drawSnow(canvas: Canvas, bounds: RectF, corner: Float, time: Float, compact: Boolean, lowQuality: Boolean) {
        drawFrost(canvas, bounds, corner, time, lowQuality)
        val top = bounds.top
        val left = bounds.left
        val right = bounds.right
        snowPath.reset()
        snowPath.moveTo(left + corner, top)
        snowPath.lineTo(right - corner, top)
        snowPath.quadTo(right - corner * 0.3f, top + dp(4f), right - corner * 0.9f, top + dp(2f))
        var x = right - corner
        var i = 0
        while (x > left + corner) {
            val wave = dp(1.5f) * sin((i * 0.8f).toDouble()).toFloat()
            snowPath.lineTo(x - dp(8f), top + dp(2.5f) + wave)
            x -= dp(8f)
            i++
        }
        snowPath.lineTo(left + corner, top + dp(1.5f))
        snowPath.close()
        snowPaint.style = Paint.Style.FILL
        snowPaint.color = Color.argb(if (compact) 18 else 28, 20, 30, 45)
        canvas.save()
        canvas.translate(0f, dp(1.4f))
        canvas.drawPath(snowPath, snowPaint)
        canvas.restore()
        snowPaint.color = Color.argb(if (compact) 45 else 70, 255, 255, 255)
        canvas.drawPath(snowPath, snowPaint)
        snowPaint.color = Color.argb(if (compact) 28 else 45, 255, 255, 255)
        var dotX = left + corner + dp(4f)
        while (dotX < right - corner - dp(4f)) {
            canvas.drawCircle(dotX, top + dp(2.2f), dp(0.65f), snowPaint)
            dotX += dp(9f)
        }

        val flakes = if (lowQuality) 2 else if (compact) 3 else 5
        flakePaint.color = Color.argb(if (compact) 55 else 95, 255, 255, 255)
        for (index in 0 until flakes) {
            val phase = (time * 0.025f + index * 0.21f) % 1f
            val fx = bounds.left + bounds.width() * (0.12f + index * 0.19f)
            val fy = bounds.top + dp(8f) + phase * (bounds.height() - dp(18f))
            val size = dp(if (compact) 1.8f else 2.6f)
            canvas.drawLine(fx - size, fy, fx + size, fy, flakePaint)
            canvas.drawLine(fx, fy - size, fx, fy + size, flakePaint)
            canvas.drawLine(fx - size * 0.7f, fy - size * 0.7f, fx + size * 0.7f, fy + size * 0.7f, flakePaint)
            canvas.drawLine(fx - size * 0.7f, fy + size * 0.7f, fx + size * 0.7f, fy - size * 0.7f, flakePaint)
        }
    }

    private fun drawFrost(canvas: Canvas, bounds: RectF, corner: Float, time: Float, lowQuality: Boolean) {
        val t = if (lowQuality) 0f else time
        val breathe = if (lowQuality) 1f else (0.82f + 0.18f * sin(t * 0.16f + 0.4f)).coerceIn(0.7f, 1f)
        val left = bounds.left
        val right = bounds.right
        val top = bounds.top
        val bottom = bounds.bottom
        val c = corner.coerceIn(0f, minOf(bounds.width(), bounds.height()) / 2f)

        // 顶部不规则结霜带：细碎贝塞尔边界 + 由外向内渐隐。
        frostTopPath.reset()
        frostTopPath.moveTo(left + c, top)
        frostTopPath.lineTo(right - c, top)
        frostTopPath.quadTo(right - c * 0.30f, top + dp(4f), right - c * 0.85f, top + dp(2f))
        var x = right - c
        var i = 0
        while (x > left + c) {
            val wave = (dp(1.2f) + dp(1.0f) * sin((i * 0.92f + t * 0.15f).toDouble()).toFloat())
            frostTopPath.lineTo(x - dp(7f), top + dp(3f) + wave)
            x -= dp(7f)
            i++
        }
        frostTopPath.lineTo(left + c, top + dp(1.4f))
        frostTopPath.close()
        frostPaint.color = Color.argb((34 * breathe).toInt().coerceIn(0, 255), 232, 248, 255)
        canvas.drawPath(frostTopPath, frostPaint)
        frostPaint.color = Color.argb((18 * breathe).toInt().coerceIn(0, 255), 188, 224, 255)
        canvas.drawPath(frostTopPath, frostPaint)

        // 边缘向内渐变：顶部两角、左右两侧。
        frostEdgePaint.shader = frostEdgeShader
        frostEdgePaint.alpha = (210 * breathe).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(left + c, top)
        canvas.scale(dp(38f), dp(24f))
        canvas.drawCircle(0f, 0f, 1f, frostEdgePaint)
        canvas.restore()
        canvas.save()
        canvas.translate(right - c, top)
        canvas.scale(dp(38f), dp(24f))
        canvas.drawCircle(0f, 0f, 1f, frostEdgePaint)
        canvas.restore()
        canvas.save()
        canvas.translate(left, top + bounds.height() * 0.45f)
        canvas.scale(dp(24f), bounds.height() * 0.38f)
        canvas.drawCircle(0f, 0f, 1f, frostEdgePaint)
        canvas.restore()
        canvas.save()
        canvas.translate(right, top + bounds.height() * 0.45f)
        canvas.scale(dp(24f), bounds.height() * 0.38f)
        canvas.drawCircle(0f, 0f, 1f, frostEdgePaint)
        canvas.restore()
        frostEdgePaint.shader = null

        // 极细冰晶边线，左右不对称。
        frostLinePaint.color = Color.argb((52 * breathe).toInt().coerceIn(0, 255), 225, 244, 255)
        frostSidePath.reset()
        var y = top + c
        var edgeIndex = 0
        while (y < bottom - c) {
            val wx = left + dp(1.2f) + dp(1.4f) * sin((edgeIndex * 0.9f + t * 0.12f).toDouble()).toFloat()
            if (edgeIndex == 0) frostSidePath.moveTo(wx, y) else frostSidePath.lineTo(wx, y)
            y += dp(7f)
            edgeIndex++
        }
        canvas.drawPath(frostSidePath, frostLinePaint)
        y = top + c
        edgeIndex = 0
        frostSidePath.reset()
        while (y < bottom - c) {
            val wx = right - dp(1.2f) - dp(1.4f) * sin((edgeIndex * 0.8f + 1.2f + t * 0.12f).toDouble()).toFloat()
            if (edgeIndex == 0) frostSidePath.moveTo(wx, y) else frostSidePath.lineTo(wx, y)
            y += dp(7f)
            edgeIndex++
        }
        canvas.drawPath(frostSidePath, frostLinePaint)

        // 少量冰粉沿边缘缓慢下落。
        val dust = if (lowQuality) 2 else 4
        for (index in 0 until dust) {
            val phase = (t * 0.018f + index * 0.24f) % 1f
            val dx = if (index % 2 == 0) left + dp(3f) else right - dp(3f)
            val dy = top + dp(8f) + phase * (bounds.height() - dp(16f))
            iceDustPaint.color = Color.argb((if (lowQuality) 35 else 65) * (1f - phase * 0.5f).toInt(), 235, 249, 255)
            canvas.drawCircle(dx, dy, dp(0.65f), iceDustPaint)
        }
    }

    private fun drawSun(canvas: Canvas, bounds: RectF, time: Float, compact: Boolean) {
        val sweep = (time * 0.035f) % 1f
        val cx = bounds.left + bounds.width() * (0.12f + sweep * 0.76f)
        val cy = bounds.top - dp(2f)
        val radius = bounds.height() * (if (compact) 0.5f else 0.7f)
        sunPaint.style = Paint.Style.FILL
        sunPaint.shader = sunUnitShader
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, sunPaint)
        canvas.restore()
        sunPaint.shader = null
        sunPaint.color = Color.argb(if (compact) 22 else 30, 255, 238, 190)
        canvas.drawCircle(cx, cy, radius * 0.62f, sunPaint)
        sunPaint.color = Color.argb(if (compact) 36 else 48, 255, 250, 225)
        canvas.drawCircle(cx, cy, radius * 0.28f, sunPaint)
        sunPaint.color = Color.argb(if (compact) 18 else 28, 255, 245, 210)
        sunPaint.style = Paint.Style.STROKE
        sunPaint.strokeWidth = 1f * density
        val lineY = bounds.top + bounds.height() * 0.22f
        canvas.drawLine(
            bounds.left + bounds.width() * 0.1f, lineY,
            bounds.left + bounds.width() * 0.9f, lineY, sunPaint
        )
    }

    @SuppressLint("NewApi")
    private fun drawCloud(canvas: Canvas, bounds: RectF, time: Float, compact: Boolean, lowQuality: Boolean) {
        val count = if (lowQuality) 2 else if (compact) 3 else 4
        cloudPaint.shader = if (lightTheme) cloudLightShader else cloudDarkShader
        cloudPaint.blendMode = if (!lowQuality && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BlendMode.OVERLAY
        } else {
            null
        }
        for (i in 0 until count) {
            val drift = (time * (0.010f + i * 0.003f) + i * 0.24f) % 1f
            val breathe = 1f + 0.035f * sin((time * 0.23f + i * 1.4f).toDouble()).toFloat()
            val cx = bounds.left + bounds.width() * drift
            val cy = bounds.top + bounds.height() * (0.30f + i * 0.11f)
            val rx = bounds.width() * (if (compact) 0.30f else 0.36f) * breathe
            val ry = bounds.height() * (if (compact) 0.42f else 0.55f) * breathe
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, cloudPaint)
            canvas.restore()
        }
        cloudPaint.blendMode = null
        cloudPaint.shader = null
        if (!lowQuality) {
            val tx = bounds.left + bounds.width() * (0.25f + 0.45f * ((time * 0.014f) % 1f))
            val ty = bounds.top + bounds.height() * 0.30f
            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(bounds.width() * 0.18f, bounds.height() * 0.34f)
            canvas.drawCircle(0f, 0f, 1f, tyndallPaint)
            canvas.restore()
            // 云层贴近胶囊上/下边缘时的菲涅尔提亮。
            cloudRimPaint.shader = cloudRimShader
            canvas.save()
            canvas.translate(bounds.centerX(), bounds.top)
            canvas.scale(bounds.width() * 0.50f, bounds.height() * 0.10f)
            canvas.drawCircle(0f, 0f, 1f, cloudRimPaint)
            canvas.restore()
            canvas.save()
            canvas.translate(bounds.centerX(), bounds.bottom)
            canvas.scale(bounds.width() * 0.46f, bounds.height() * 0.07f)
            canvas.drawCircle(0f, 0f, 1f, cloudRimPaint)
            canvas.restore()
            cloudRimPaint.shader = null
        }
    }

    private fun edgeFactor(x: Float, bounds: RectF): Float {
        val left = (x - bounds.left) / dp(24f)
        val right = (bounds.right - x) / dp(24f)
        val edge = minOf(left, right).coerceIn(0f, 1f)
        return 1f - edge
    }

    private fun createNoiseBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = Random(42)
        for (i in pixels.indices) {
            val v = random.nextInt(255)
            val alpha = if (v > 128) random.nextInt(18, 34) else random.nextInt(0, 8)
            pixels[i] = Color.argb(alpha, v, v, v)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun buildTeardrop(path: Path) {
        path.moveTo(0f, -1f)
        path.cubicTo(0.55f, -0.42f, 1f, 0.18f, 0.96f, 0.56f)
        path.cubicTo(0.90f, 1.10f, 0.12f, 1.22f, -0.96f, 0.56f)
        path.cubicTo(-1f, 0.18f, -0.55f, -0.42f, 0f, -1f)
        path.close()
    }

    private class TrailMark {
        var active = false
        var rightSide = true
        var start = 0f
        var end = 0f
        var radius = 0f
        var age = 0f
    }

    private class SplashParticle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var radius = 0f
        var life = 0f
    }

    private class RainDrop {
        var active = false
        var rightSide = true
        var distance = 0f
        var detachDistance = 0f
        var speed = 0f
        var radius = 0f
        var hang = 0f
        var falling = false
        var fallX = 0f
        var fallY = 0f
        var fallVy = 0f
        var fallVx = 0f
        var splash = 0f
        var lastMarkDistance = 0f
    }

    private fun dp(value: Float): Float = value * density
}
