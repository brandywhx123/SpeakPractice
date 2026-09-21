package com.example.pronunciationassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * WaveformView - 声音波形显示自定义视图
 *
 * 功能:
 * - 接收一个 FloatArray (振幅数组，取值范围 0.0~1.0)
 * - 以中央对称的柱状条形式绘制波形
 * - 上栏显示标准发音波形，下栏显示用户发音波形
 *
 * 绘制原理:
 * - onDraw() 中根据振幅数组为每个采样点画一根竖向柱条
 * - 柱条以视图垂直中线为基准上下对称展开，模拟音频波形外观
 * - 无数据时只绘制一条灰色基准线
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 振幅数据，取值范围 0.0 ~ 1.0
    private var amplitudes: FloatArray = FloatArray(0)

    // 柱条颜色 (默认蓝色，对应标准发音)
    private var barColor: Int = Color.parseColor("#2196F3")

    // 最大柱条数量，避免过密
    private val maxBars = 64

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor
        style = Paint.Style.FILL
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        strokeWidth = 2f
    }

    /**
     * 设置波形数据并触发重绘
     * @param data 振幅数组，元素取值 0.0 ~ 1.0
     */
    fun setWaveform(data: FloatArray) {
        amplitudes = data.copyOf()
        invalidate()
    }

    /**
     * 设置柱条颜色 (上栏=蓝，下栏=绿)
     */
    fun setBarColor(color: Int) {
        barColor = color
        barPaint.color = color
        invalidate()
    }

    /**
     * 清空波形
     */
    fun clearWaveform() {
        amplitudes = FloatArray(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        // 绘制中央基准线
        canvas.drawLine(0f, centerY, w, centerY, baselinePaint)

        if (amplitudes.isEmpty()) return

        // 左右各留 5% 内边距，避免波形紧贴 View 边缘
        val padLeft = w * 0.05f
        val padRight = w * 0.05f
        val availableWidth = w - padLeft - padRight

        // 计算柱条宽度与间距 (总间距占 20% 可用宽度)
        val barCount = minOf(amplitudes.size, maxBars)
        val totalGap = availableWidth * 0.2f
        val totalBarWidth = availableWidth - totalGap
        val barWidth = totalBarWidth / barCount
        // gap 在 barCount 个柱条两侧均匀分布 (含首尾两端)
        // 这样第一个柱条不紧贴左边缘，最后一个不紧贴右边缘，上下两栏完全对齐
        val gap = if (barCount > 1) totalGap / (barCount + 1) else 0f

        // 从原始振幅数组均匀取样到 barCount 个点
        val step = if (amplitudes.size > barCount) {
            amplitudes.size.toFloat() / barCount
        } else {
            1f
        }

        for (i in 0 until barCount) {
            val sampleIndex = (i * step).toInt().coerceAtMost(amplitudes.size - 1)
            val amp = amplitudes[sampleIndex].coerceIn(0f, 1f)
            // 添加 5% 最小振幅，保证小值也可见，避免波形断裂
            val effectiveAmp = maxOf(amp, 0.05f)
            val barHeight = effectiveAmp * (h * 0.85f)
            // 第 i 个柱条的 left = padLeft + gap + i * (barWidth + gap)
            // 首尾两侧都有 gap，让波形整体居中，上下两栏完全对齐
            val left = padLeft + gap + i * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f
            canvas.drawRect(left, top, right, bottom, barPaint)
        }
    }
}
