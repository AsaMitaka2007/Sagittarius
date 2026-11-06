package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.animation.ValueAnimator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.util.Money
import kotlin.math.min
import kotlin.math.roundToInt

data class ExpenseBubbleLine(
    val categoryId: Long,
    val name: String,
    val color: Int,
    val amountCents: Long
)

/**
 * 透明气泡金额输入。EditText 文本透明，只负责键盘输入；金额由 Canvas 绘制。
 */
class BubbleAmountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }
    private val amountPaint = textPaint(38f, tokens.textPrimary, medium = true, tabular = true)
    private val hintPaint = textPaint(10f, tokens.textTertiary)
    private val linePaint = textPaint(8.2f, tokens.textSecondary)
    private val lineTagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circle = RectF()
    private var error = false
    private var amountListener: ((String) -> Unit)? = null
    private var lines: List<ExpenseBubbleLine> = emptyList()
    private val labelBounds = mutableListOf<RectF>()
    private val labelOwners = mutableListOf<Int>()
    private var pendingLabelIndex = -1
    private val undoButton: TextView
    private val clearButton: TextView

    var onUndo: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onRemoveLine: ((Int) -> Unit)? = null

    val editText: EditText = EditText(context).apply {
        background = null
        setTextColor(Color.TRANSPARENT)
        isCursorVisible = false
        gravity = Gravity.CENTER
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        imeOptions = EditorInfo.IME_ACTION_DONE
        filters = arrayOf(InputFilter.LengthFilter(12))
        setPadding(0, 0, 0, 0)
        textSize = 18f
        isSingleLine = true
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setSelectAllOnFocus(false)
        setOnClickListener { focusAndShowKeyboard() }
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showKeyboard()
            invalidate()
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                amountListener?.invoke(s?.toString().orEmpty())
                invalidate()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        contentDescription = "金额气泡，点击输入金额"
        addView(
            editText,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        undoButton = miniButton("↶").apply {
            setOnClickListener { onUndo?.invoke() }
            visibility = GONE
        }
        clearButton = miniButton("×").apply {
            setOnClickListener { onClear?.invoke() }
            visibility = GONE
        }
        addView(
            undoButton,
            LayoutParams(dpI(24f), dpI(24f), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dpI(32f)
                bottomMargin = dpI(28f)
            }
        )
        addView(
            clearButton,
            LayoutParams(dpI(24f), dpI(24f), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dpI(32f)
                bottomMargin = dpI(28f)
            }
        )
        setOnClickListener { focusAndShowKeyboard() }
    }

    fun setLines(value: List<ExpenseBubbleLine>) {
        lines = value
        val visible = value.isNotEmpty()
        undoButton.visibility = if (visible) VISIBLE else GONE
        clearButton.visibility = if (visible) VISIBLE else GONE
        invalidate()
    }

    fun amountText(): String = editText.text?.toString().orEmpty()

    fun doOnAmountChanged(listener: (String) -> Unit) {
        amountListener = listener
    }

    fun clearAmount() {
        editText.text?.clear()
        invalidate()
    }

    fun setError(value: Boolean) {
        error = value
        invalidate()
    }

    /** 空金额/无类别保存时的无弹窗反馈。 */
    fun flashError() {
        setError(true)
        animate().cancel()
        animate().scaleX(1.06f).scaleY(1.06f).setDuration(90L)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
            }
            .start()
        postDelayed({ setError(false) }, 520L)
    }

    fun requestAmountFocus(): Boolean {
        editText.requestFocus()
        editText.setSelection(editText.text?.length ?: 0)
        showKeyboard()
        return true
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
        editText.clearFocus()
    }

    private fun focusAndShowKeyboard() {
        editText.requestFocus()
        editText.setSelection(editText.text?.length ?: 0)
        showKeyboard()
    }

    private fun showKeyboard() {
        editText.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat() - dp(8f)
        val cx = width / 2f
        val cy = height / 2f
        val radius = diameter / 2f
        circle.set(cx - radius, cy - radius, cx + radius, cy + radius)

        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = 1.2f * density
        for (i in 5 downTo 1) {
            glowPaint.color = ColorUtils.setAlphaComponent(tokens.gradientStart, 4 + (5 - i) * 4)
            canvas.drawCircle(cx, cy, radius + i * dp(2.2f), glowPaint)
        }

        fillPaint.shader = RadialGradient(
            cx - radius * 0.34f,
            cy - radius * 0.4f,
            radius * 1.45f,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, 42),
                tokens.cardFillTop,
                tokens.cardFillBottom
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null

        highlightPaint.shader = RadialGradient(
            cx - radius * 0.36f,
            cy - radius * 0.42f,
            radius * 0.62f,
            intArrayOf(ColorUtils.setAlphaComponent(Color.WHITE, 100), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, highlightPaint)
        highlightPaint.shader = null

        borderPaint.color = if (error) tokens.warning else tokens.cardStrokeStart
        borderPaint.strokeWidth = if (error) 2.2f * density else 1.3f * density
        canvas.drawCircle(cx, cy, radius, borderPaint)
        // 顶部高光弧线
        borderPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 35)
        borderPaint.strokeWidth = 1f * density
        canvas.drawArc(
            RectF(cx - radius + dp(8f), cy - radius + dp(8f), cx + radius - dp(8f), cy + radius - dp(8f)),
            200f, 120f, false, borderPaint
        )

        val raw = editText.text?.toString().orEmpty()
        val total = lines.sumOf { it.amountCents }
        val display = when {
            raw.isNotEmpty() -> raw
            total > 0L -> Money.formatPlainCents(total)
            else -> "0.00"
        }
        val accent = lines.lastOrNull()?.color
        amountPaint.color = when {
            accent != null -> accent
            raw.isEmpty() -> tokens.textTertiary
            else -> tokens.textPrimary
        }
        amountPaint.textSize = fitTextSize(amountPaint, "¥$display", radius * 1.52f)
        amountPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("¥$display", cx, cy + dp(9f), amountPaint)

        if (lines.isNotEmpty()) {
            drawLineTags(canvas, cx, cy)
        } else if (raw.isEmpty()) {
            hintPaint.color = tokens.textTertiary
            hintPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("输入金额后点类别", cx, cy + dp(32f), hintPaint)
        }
    }

    private fun miniButton(textValue: String): TextView = TextView(context).apply {
        text = textValue
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(tokens.textSecondary)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(tokens.iconCircle, 220))
            setStroke(dpI(1f), ColorUtils.setAlphaComponent(tokens.textTertiary, 70))
        }
        isClickable = true
        isFocusable = false
    }

    private fun drawLineTags(canvas: Canvas, cx: Float, cy: Float) {
        labelBounds.clear()
        labelOwners.clear()
        val shown = lines.takeLast(2)
        val firstIndex = lines.size - shown.size
        linePaint.textSize = sp(7.2f)
        linePaint.textAlign = Paint.Align.CENTER
        val texts = shown.map {
            "${it.name} ${Money.formatPlainCents(it.amountCents)}"
        }.toMutableList()
        val gap = dp(4f)
        var widths = texts.map { linePaint.measureText(it) + dp(10f) }
        var total = widths.sum() + gap * (texts.size - 1).coerceAtLeast(0)
        if (total > dp(146f)) {
            linePaint.textSize = sp(6.3f)
            widths = texts.map { linePaint.measureText(it) + dp(8f) }
            total = widths.sum() + gap * (texts.size - 1).coerceAtLeast(0)
        }
        var x = cx - total / 2f
        val top = cy + dp(19f)
        val height = dp(14f)
        texts.forEachIndexed { shownIndex, text ->
            val width = widths[shownIndex]
            val rect = RectF(x, top, x + width, top + height)
            val path = Path().apply {
                addRoundRect(rect, height / 2f, height / 2f, Path.Direction.CW)
            }
            lineTagBgPaint.color = ColorUtils.setAlphaComponent(tokens.iconCircle, 190)
            canvas.drawPath(path, lineTagBgPaint)
            linePaint.color = tokens.textSecondary
            linePaint.alpha = 235
            canvas.drawText(text, rect.centerX(), top + height * 0.72f, linePaint)
            labelBounds += rect
            labelOwners += firstIndex + shownIndex
            x += width + gap
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            pendingLabelIndex = -1
            for (index in labelBounds.indices.reversed()) {
                if (labelBounds[index].contains(ev.x, ev.y)) {
                    pendingLabelIndex = labelOwners[index]
                    return true
                }
            }
        }
        if (pendingLabelIndex >= 0) {
            if (ev.actionMasked == MotionEvent.ACTION_UP) {
                onRemoveLine?.invoke(pendingLabelIndex)
            }
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                pendingLabelIndex = -1
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun fitLabel(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float): Float {
        val preferred = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 38f, resources.displayMetrics)
        var size = preferred
        while (size > dp(15f) && paint.measureText(text) > maxWidth) {
            size -= dp(1f)
            paint.textSize = size
        }
        return size
    }

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
    private fun dpI(value: Float): Int = (value * density).roundToInt()
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
