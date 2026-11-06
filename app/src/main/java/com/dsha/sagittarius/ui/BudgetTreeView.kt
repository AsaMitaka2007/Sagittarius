package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.data.BudgetTreeDisplayNode
import com.dsha.sagittarius.data.BudgetTreeModel
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.Money
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.min

/**
 * 球形预算树。
 *
 * - 根球 = 当前范围总预算，一级球 = 一级分配，二级球 = 子分配；
 * - 球之间用渐变细线连接，线从父球边缘到子球边缘；
 * - 点击球展开/收起，展开时子球从父球方向淡入 + 位移；
 * - 长按球回调编辑菜单；
 * - 接近上限橙黄描边，超额红色描边 + 轻微脉动；
 * - 低端机关闭动画和脉动，球内文字过小时只显示金额。
 */
class BudgetTreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val lowEnd = MotionUtils.isLowRam(context)
    private val reduceMotion = MotionUtils.reduceMotion(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelRect = android.graphics.RectF()
    private val namePaint = textPaint(10f, Color.WHITE, medium = true)
    private val amountPaint = textPaint(11f, Color.WHITE, medium = true, tabular = true)
    private val rootTitlePaint = textPaint(10f, Color.WHITE, medium = true)
    private val rootAmountPaint = textPaint(17f, Color.WHITE, medium = true, tabular = true)
    private val ratioPaint = textPaint(9f, tokens.textTertiary)
    private val emptyPaint = textPaint(11f, tokens.textTertiary)
    private val ballPath = Path()
    private val connectorPath = Path()
    private val liquidPath = Path()
    private val liquidSurfacePath = Path()
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val ballTextColor = if (ThemeManager.isLight) tokens.textPrimary else Color.WHITE

    private var model = BudgetTreeModel(0L, 0L, 0L, false, emptyList(), emptyMap())
    private var categoryColors: Map<Long, Int> = emptyMap()
    private var totalUsedCents = 0L
    private var rootCollapsed = false
    private val collapsedIds = mutableSetOf<Long>()

    private var rootBall: Ball? = null
    private val balls = mutableListOf<Ball>()
    private val ballsById = mutableMapOf<Long, Ball>()
    private var hasOver = false

    private var pulsePhase = 0.4f
    private var pulseAnimator: ValueAnimator? = null
    private var revealParentId: Long? = null
    private var revealProgress = 1f
    private var revealAnimator: ValueAnimator? = null

    var onNodeLongClick: ((BudgetTreeDisplayNode) -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = findBall(e.x, e.y) ?: return false
                if (hit.isRoot) {
                    if (model.nodes.isNotEmpty()) {
                        rootCollapsed = !rootCollapsed
                        if (!rootCollapsed) startReveal(ROOT_ID) else activateReveal(null)
                        requestLayout()
                        invalidate()
                        animateToggleFeedback()
                    }
                } else {
                    val display = hit.display ?: return false
                    if (display.children.isNotEmpty()) {
                        val expanding = if (!collapsedIds.add(display.node.id)) {
                            collapsedIds.remove(display.node.id)
                            false
                        } else {
                            true
                        }
                        if (expanding) startReveal(display.node.id) else activateReveal(null)
                        requestLayout()
                        invalidate()
                        animateToggleFeedback()
                    }
                }
                performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val hit = findBall(e.x, e.y) ?: return
                if (!hit.isRoot) hit.display?.let { onNodeLongClick?.invoke(it) }
            }
        }
    )

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        contentDescription = "预算树"
        layoutDirection = LAYOUT_DIRECTION_LTR
        emptyPaint.textAlign = Paint.Align.CENTER
        ratioPaint.textAlign = Paint.Align.CENTER
        namePaint.textAlign = Paint.Align.CENTER
        amountPaint.textAlign = Paint.Align.CENTER
        rootTitlePaint.textAlign = Paint.Align.CENTER
        rootAmountPaint.textAlign = Paint.Align.CENTER
    }

    fun setVisualData(totalUsedCents: Long, categoryColors: Map<Long, Int>) {
        this.totalUsedCents = totalUsedCents
        this.categoryColors = categoryColors
        invalidate()
    }

    fun setModel(model: BudgetTreeModel) {
        this.model = model
        val allIds = mutableListOf<Long>()
        fun collect(nodes: List<BudgetTreeDisplayNode>) {
            nodes.forEach {
                allIds += it.node.id
                collect(it.children)
            }
        }
        collect(model.nodes)
        collapsedIds.retainAll(allIds.toSet())
        if (model.nodes.isEmpty()) rootCollapsed = false
        hasOver = model.overAllocated || model.nodes.any { anyOver(it) }
        contentDescription = "预算树，总预算 ${Money.formatCents(model.totalCents)}，" +
            "未分配 ${Money.formatSignedCents(model.unallocatedCents)}"
        updatePulse()
        requestLayout()
        invalidate()
    }

    private fun anyOver(node: BudgetTreeDisplayNode): Boolean =
        node.overAllocated || node.usedShare > 1f || node.children.any { anyOver(it) }

    private fun visibleChildren(node: BudgetTreeDisplayNode): List<BudgetTreeDisplayNode> =
        if (node.node.id in collapsedIds) emptyList() else node.children

    private fun leafCount(node: BudgetTreeDisplayNode): Int {
        val children = visibleChildren(node)
        if (children.isEmpty()) return 1
        return children.sumOf { leafCount(it) }
    }

    private fun topNodes(): List<BudgetTreeDisplayNode> =
        if (rootCollapsed) emptyList() else model.nodes

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(320f).toInt(), widthMeasureSpec)
        val desiredHeight = calculateHeight(width)
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    private fun calculateHeight(width: Int): Int {
        val w = width.toFloat()
        val radius = rootRadius(w)
        val top = topNodes()
        val rootTop = dp(8f) + dp(22f)
        val rootCenterY = rootTop + radius
        val rootBottom = rootCenterY + radius
        if (top.isEmpty()) return (rootBottom + dp(46f)).toInt()
        val level1Base = level1Radius(w)
        val level1Y = rootBottom + dp(30f) + level1Base
        val hasSecond = top.any { it.node.id !in collapsedIds && it.children.isNotEmpty() }
        return if (hasSecond) {
            val level2Base = level2Radius(w)
            (level1Y + level1Base + dp(36f) + level2Base + dp(38f)).toInt()
        } else {
            (level1Y + level1Base + dp(38f)).toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrim(canvas)
        layoutBalls()

        val root = rootBall
        if (root == null) {
            emptyPaint.color = tokens.textTertiary
            canvas.drawText("别急，先设置预算", width / 2f, height / 2f, emptyPaint)
            return
        }

        // 连接线先画，球体覆盖在线的端点上，保证线不穿过球体。
        topNodes().forEach { node ->
            ballsById[node.node.id]?.let { drawConnector(canvas, root, it) }
            if (node.node.id !in collapsedIds) {
                val parent = ballsById[node.node.id]
                node.children.forEach { child ->
                    if (parent != null) {
                        ballsById[child.node.id]?.let { drawConnector(canvas, parent, it) }
                    }
                }
            }
        }

        drawRoot(canvas, root)
        balls.forEach { drawBall(canvas, it) }

        if (topNodes().isEmpty()) {
            emptyPaint.color = tokens.textTertiary
            val y = root.centerY + root.radius + dp(34f)
            val hint = when {
                model.totalCents <= 0L -> "尚未设置预算"
                model.nodes.isNotEmpty() && rootCollapsed -> "一级分配已收起，点击总预算球展开"
                else -> "尚未分配，点击下方按钮开始"
            }
            canvas.drawText(hint, width / 2f, y, emptyPaint)
        }
    }

    private fun drawScrim(canvas: Canvas) {
        if (!ThemeManager.isGlass) return
        scrimPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillBottom, 24)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    private fun layoutBalls() {
        balls.clear()
        ballsById.clear()
        val w = width.toFloat()
        val radius = rootRadius(w)
        val rootCenterX = w / 2f
        val rootCenterY = dp(8f) + dp(22f) + radius
        rootBall = Ball(
            centerX = rootCenterX,
            centerY = rootCenterY,
            radius = radius,
            isRoot = true,
            display = null
        )

        val top = topNodes()
        if (top.isEmpty()) return
        val usable = w - dp(16f)
        val topLeaves = top.sumOf { leafCount(it) }.coerceAtLeast(1)
        val slot = usable / topLeaves
        var left = dp(8f)
        val level1Y = rootCenterY + radius + dp(30f) + level1Radius(w)
        val level2Y = level1Y + level1Radius(w) + dp(36f) + level2Radius(w)
        top.forEach { node ->
            val span = slot * leafCount(node)
            layoutSubtree(node, 1, left, span, level1Y, level2Y)
            left += span
        }
    }

    private fun layoutSubtree(
        node: BudgetTreeDisplayNode,
        depth: Int,
        left: Float,
        span: Float,
        level1Y: Float,
        level2Y: Float
    ) {
        val base = if (depth == 1) level1Radius(width.toFloat()) else level2Radius(width.toFloat())
        val radius = min(base, span * 0.42f).coerceAtLeast(dp(10f))
        val ball = Ball(
            centerX = left + span / 2f,
            centerY = if (depth == 1) level1Y else level2Y,
            radius = radius,
            isRoot = false,
            display = node
        )
        balls += ball
        ballsById[node.node.id] = ball

        val children = visibleChildren(node)
        if (children.isEmpty()) return
        val childLeaves = children.sumOf { leafCount(it) }.coerceAtLeast(1)
        val childSlot = span / childLeaves
        var childLeft = left
        children.forEach { child ->
            val childSpan = childSlot * leafCount(child)
            layoutSubtree(child, depth + 1, childLeft, childSpan, level1Y, level2Y)
            childLeft += childSpan
        }
    }

    private fun drawConnector(canvas: Canvas, parent: Ball, child: Ball) {
        val dx = child.centerX - parent.centerX
        val dy = child.centerY - parent.centerY
        val distance = hypot(dx, dy)
        if (distance <= parent.radius + child.radius) return
        val ux = dx / distance
        val uy = dy / distance
        val startX = parent.centerX + ux * parent.radius
        val startY = parent.centerY + uy * parent.radius
        val endX = child.centerX - ux * child.radius
        val endY = child.centerY - uy * child.radius
        connectorPaint.shader = LinearGradient(
            startX, startY, endX, endY,
            ColorUtils.setAlphaComponent(tokens.gradientStart, 190),
            ColorUtils.setAlphaComponent(tokens.gradientEnd, 150),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(startX, startY, endX, endY, connectorPaint)
    }

    private fun drawRoot(canvas: Canvas, ball: Ball) {
        val over = model.overAllocated
        val near = model.totalCents > 0 &&
            model.topAllocatedCents.toFloat() / model.totalCents >= 0.9f
        drawSphere(
            canvas,
            ball,
            over = over,
            near = near,
            alpha = 1f
        )
        val label = if (model.totalCents > 0) "未分配" else "未设置预算"
        val value = if (model.totalCents > 0) {
            Money.formatSignedCents(model.unallocatedCents)
        } else {
            "点击下方设置"
        }
        val paintColor = if (model.unallocatedCents < 0) tokens.warning else tokens.textSecondary
        emptyPaint.color = paintColor
        canvas.drawText("$label $value", width / 2f, dp(18f), emptyPaint)
        ratioPaint.color = tokens.textTertiary
        drawRatioLabel(
            canvas,
            "已分配 ${(if (model.totalCents > 0) model.topAllocatedCents * 100 / model.totalCents else 0)}%",
            ball.centerX,
            ball.centerY + ball.radius + dp(13f),
            1f
        )
    }

    private fun drawBall(canvas: Canvas, ball: Ball) {
        val display = ball.display ?: return
        val parent = if (display.node.parentId == null) {
            rootBall
        } else {
            ballsById[display.node.parentId]
        }
        var drawX = ball.centerX
        var drawY = ball.centerY
        var alpha = 1f
        if (revealProgress < 1f && revealParentId != null) {
            val matches = (revealParentId == ROOT_ID && display.node.parentId == null) ||
                display.node.parentId == revealParentId
            if (matches) {
                alpha = revealProgress.coerceIn(0.05f, 1f)
                if (parent != null) {
                    drawX -= (ball.centerX - parent.centerX) * (1f - revealProgress) * 0.45f
                    drawY -= (ball.centerY - parent.centerY) * (1f - revealProgress) * 0.25f
                }
            }
        }
        val drawBall = ball.copy(centerX = drawX, centerY = drawY)
        drawSphere(
            canvas,
            drawBall,
            over = display.overAllocated || display.usedShare > 1f,
            near = display.nearLimit,
            alpha = alpha
        )
        ratioPaint.alpha = (alpha * 255).toInt()
        ratioPaint.color = when {
            display.usedShare > 1f -> tokens.warning
            display.nearLimit -> tokens.nodeNear
            else -> tokens.textTertiary
        }
        drawRatioLabel(
            canvas,
            "分配 ${(display.allocationShare * 100).toInt()}% · 已用 ${(display.usedShare * 100).toInt()}%",
            drawBall.centerX,
            drawBall.centerY + drawBall.radius + dp(13f),
            alpha
        )
        ratioPaint.alpha = 255
    }

    private fun drawSphere(
        canvas: Canvas,
        ball: Ball,
        over: Boolean,
        near: Boolean,
        alpha: Float
    ) {
        if (alpha <= 0.02f) return
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val c1 = when {
            ball.isRoot -> tokens.gradientStart
            ball.display?.depth == 1 -> tokens.gradientStart
            else -> tokens.gradientMid
        }
        val c2 = when {
            ball.isRoot -> tokens.gradientEnd
            ball.display?.depth == 1 -> tokens.gradientMid
            else -> tokens.gradientEnd
        }
        val light = ColorUtils.blendARGB(c1, Color.WHITE, 0.36f)
        fillPaint.alpha = a
        fillPaint.shader = RadialGradient(
            ball.centerX - ball.radius * 0.3f,
            ball.centerY - ball.radius * 0.35f,
            ball.radius * 1.25f,
            intArrayOf(light, c1, c2),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ball.centerX, ball.centerY, ball.radius, fillPaint)
        fillPaint.shader = null

        highlightPaint.alpha = (a * 0.7f).toInt()
        highlightPaint.shader = RadialGradient(
            ball.centerX - ball.radius * 0.34f,
            ball.centerY - ball.radius * 0.38f,
            ball.radius * 0.55f,
            intArrayOf(ColorUtils.setAlphaComponent(Color.WHITE, 100), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ball.centerX, ball.centerY, ball.radius, highlightPaint)
        highlightPaint.shader = null

        drawLiquid(canvas, ball, a)

        borderPaint.alpha = a
        borderPaint.strokeWidth = if (over) 2.2f * density else 1.4f * density
        borderPaint.color = when {
            over -> tokens.warning
            near -> tokens.nodeNear
            else -> ColorUtils.setAlphaComponent(tokens.cardStrokeEnd, 210)
        }
        canvas.drawCircle(ball.centerX, ball.centerY, ball.radius, borderPaint)

        if (over) {
            ringPaint.alpha = (a * (if (lowEnd || reduceMotion) 0.8f else 0.35f + 0.5f * pulsePhase)).toInt()
                .coerceIn(0, 255)
            ringPaint.color = tokens.warning
            canvas.drawCircle(ball.centerX, ball.centerY, ball.radius + dp(4f), ringPaint)
        }

        drawBallText(canvas, ball, a)
    }

    /**
     * 球内液体：按节点已用/分配比例从底部上涨，颜色优先跟随关联类别。
     * 根球按总支出/总预算；无关联类别的节点使用主题渐变。
     */
    private fun drawLiquid(canvas: Canvas, ball: Ball, alpha: Int) {
        val ratio = if (ball.isRoot) {
            if (model.totalCents > 0L) {
                totalUsedCents.toFloat() / model.totalCents
            } else {
                0f
            }
        } else {
            ball.display?.usedShare ?: 0f
        }.coerceIn(0f, 1f)
        if (ratio <= 0.001f) return

        val categoryId = ball.display?.node?.categoryId
        val baseColor = categoryId?.let { categoryColors[it] } ?: when {
            ball.isRoot -> tokens.gradientStart
            ball.display?.depth == 1 -> tokens.gradientStart
            else -> tokens.gradientMid
        }
        val topColor = ColorUtils.blendARGB(baseColor, Color.WHITE, 0.28f)
        val bottomColor = ColorUtils.blendARGB(baseColor, Color.BLACK, 0.18f)
        val surfaceY = ball.centerY + ball.radius - 2f * ball.radius * ratio
        val left = ball.centerX - ball.radius
        val right = ball.centerX + ball.radius
        val bottom = ball.centerY + ball.radius
        val amp = if (ratio < 1f) dp(2.2f) else dp(0.8f)

        ballPath.reset()
        ballPath.addCircle(ball.centerX, ball.centerY, ball.radius - dp(0.8f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(ballPath)

        liquidPath.reset()
        liquidSurfacePath.reset()
        liquidPath.moveTo(left, surfaceY)
        liquidSurfacePath.moveTo(left, surfaceY)
        val steps = 18
        for (i in 1..steps) {
            val x = left + (right - left) * i / steps
            val wave = sin(i * 0.72f + 0.7f) * amp
            liquidPath.lineTo(x, surfaceY + wave)
            liquidSurfacePath.lineTo(x, surfaceY + wave)
        }
        liquidPath.lineTo(right, bottom)
        liquidPath.lineTo(left, bottom)
        liquidPath.close()
        liquidPaint.alpha = (alpha * 0.90f).toInt().coerceIn(0, 255)
        liquidPaint.shader = LinearGradient(
            ball.centerX, surfaceY, ball.centerX, bottom,
            topColor, bottomColor, Shader.TileMode.CLAMP
        )
        canvas.drawPath(liquidPath, liquidPaint)
        liquidPaint.shader = null

        foamPaint.alpha = (alpha * 0.55f).toInt().coerceIn(0, 255)
        foamPaint.color = Color.WHITE
        canvas.drawPath(liquidSurfacePath, foamPaint)
        canvas.restore()
    }

    private fun drawBallText(canvas: Canvas, ball: Ball, alpha: Int) {
        ballPath.reset()
        ballPath.addCircle(ball.centerX, ball.centerY, ball.radius - dp(1f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(ballPath)
        namePaint.alpha = alpha
        amountPaint.alpha = alpha
        rootTitlePaint.alpha = alpha
        rootAmountPaint.alpha = alpha

        if (ball.isRoot) {
            rootTitlePaint.color = ballTextColor
            rootAmountPaint.color = ballTextColor
            rootTitlePaint.textSize = (ball.radius * 0.22f).coerceIn(dp(8f), dp(11f))
            rootAmountPaint.textSize = (ball.radius * 0.36f).coerceIn(dp(13f), dp(20f))
            canvas.drawText("总预算", ball.centerX, ball.centerY - dp(5f), rootTitlePaint)
            canvas.drawText(
                if (model.totalCents > 0) Money.formatCents(model.totalCents) else "—",
                ball.centerX, ball.centerY + dp(15f), rootAmountPaint
            )
        } else {
            val display = ball.display ?: return
            namePaint.color = ballTextColor
            amountPaint.color = ballTextColor
            val showName = ball.radius >= dp(25f) && !lowEnd
            val nameSize = (ball.radius * 0.29f).coerceIn(dp(8f), dp(12f))
            val amountSize = (ball.radius * 0.27f).coerceIn(dp(8f), dp(12f))
            namePaint.textSize = nameSize
            amountPaint.textSize = amountSize
            val maxTextWidth = ball.radius * 1.55f
            if (showName) {
                val fitted = fitText(namePaint, display.node.budgetName, maxTextWidth)
                canvas.drawText(fitted, ball.centerX, ball.centerY - dp(5f), namePaint)
                canvas.drawText(
                    compactAmount(display.node.amountCents),
                    ball.centerX, ball.centerY + dp(9f), amountPaint
                )
            } else {
                canvas.drawText(
                    compactAmount(display.node.amountCents),
                    ball.centerX, ball.centerY + dp(3f), amountPaint
                )
            }
        }
        canvas.restore()
    }

    private fun drawRatioLabel(canvas: Canvas, text: String, centerX: Float, y: Float, alpha: Float) {
        val width = ratioPaint.measureText(text) + dp(8f)
        labelBgPaint.alpha = (alpha * 210).toInt().coerceIn(0, 255)
        labelBgPaint.color = tokens.cardFillBottom
        labelRect.set(centerX - width / 2f, y - dp(10f), centerX + width / 2f, y + dp(4f))
        canvas.drawRoundRect(labelRect, dp(7f), dp(7f), labelBgPaint)
        ratioPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawText(text, centerX, y, ratioPaint)
    }

    private fun compactAmount(cents: Long): String {
        val yuan = cents / 100.0
        return when {
            yuan >= 10000 -> "¥" + String.format(Locale.CHINA, "%.1f万", yuan / 10000)
            yuan >= 1000 -> "¥" + String.format(Locale.CHINA, "%.1fk", yuan / 1000)
            else -> Money.formatCents(cents)
        }
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length - 1
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end <= 0) "…" else text.substring(0, end) + "…"
    }

    private fun findBall(x: Float, y: Float): Ball? {
        val root = rootBall
        if (root != null && hypot(x - root.centerX, y - root.centerY) <= root.radius + dp(7f)) {
            return root
        }
        return balls.firstOrNull {
            hypot(x - it.centerX, y - it.centerY) <= it.radius + dp(7f)
        }
    }

    private fun startReveal(parentId: Long) {
        revealParentId = parentId
        revealProgress = 1f
        revealAnimator?.cancel()
        if (lowEnd || reduceMotion || !isShown) return
        revealProgress = 0f
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                revealProgress = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
    }

    private fun activateReveal(parentId: Long?) {
        revealParentId = parentId
        revealProgress = 1f
    }

    private fun animateToggleFeedback() {
        if (lowEnd || reduceMotion) return
        scaleX = 0.982f
        scaleY = 0.982f
        SpringAnimation(this, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = 0.55f
            spring.stiffness = 320f
            start()
        }
        SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = 0.55f
            spring.stiffness = 320f
            start()
        }
    }

    private fun ensurePulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofFloat(0.2f, 1f).apply {
            duration = 1_500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                invalidate()
            }
        }
    }

    private fun updatePulse() {
        val shouldRun = hasOver && !lowEnd && !reduceMotion &&
            isAttachedToWindow && isShown && windowVisibility == View.VISIBLE
        if (shouldRun) {
            ensurePulse()
            pulseAnimator?.let {
                if (it.isPaused) it.resume() else if (!it.isStarted) it.start()
            }
        } else if (pulseAnimator?.isStarted == true) {
            pulseAnimator?.pause()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        revealAnimator?.cancel()
        revealAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            revealAnimator?.cancel()
            revealProgress = 1f
        }
        if (isAttachedToWindow) updatePulse()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updatePulse()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rootRadius(width: Float): Float =
        min(dp(54f), width * 0.16f).coerceAtLeast(dp(38f))

    private fun level1Radius(width: Float): Float =
        min(dp(38f), width * 0.11f).coerceAtLeast(dp(22f))

    private fun level2Radius(width: Float): Float =
        min(dp(28f), width * 0.08f).coerceAtLeast(dp(16f))

    private fun textPaint(
        sp: Float,
        color: Int,
        medium: Boolean = false,
        tabular: Boolean = false
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        this.color = color
        typeface = android.graphics.Typeface.create(
            if (medium) "sans-serif-medium" else "sans-serif",
            android.graphics.Typeface.NORMAL
        )
        if (tabular) fontFeatureSettings = "tnum"
    }

    private fun dp(value: Float): Float = value * density

    private data class Ball(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val isRoot: Boolean,
        val display: BudgetTreeDisplayNode?
    )

    companion object {
        private const val ROOT_ID = -1L
    }
}
