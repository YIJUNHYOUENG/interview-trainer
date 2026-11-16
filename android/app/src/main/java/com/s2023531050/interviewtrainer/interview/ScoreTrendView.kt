package com.s2023531050.interviewtrainer.interview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.s2023531050.interviewtrainer.R

/**
 * 면접 점수 변화를 꺾은선으로 그린다(히스토리의 "점수 변화", 홈의 "최근 점수 추이").
 * [scores] 는 오래된 순서의 0~100 점수이고, 마지막 점이 가장 최근이다.
 * 점수 차이가 작아도 변화가 보이도록 세로 범위를 점수의 최소~최대 주변으로 잡는다(최소 20점 폭).
 */
class ScoreTrendView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 오래된 순 점수. 값을 바꾸면 다시 그린다. */
    var scores: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** true 면 점마다 점수 숫자를 함께 쓴다(히스토리의 큰 그래프용) */
    var showLabels: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val accent = ContextCompat.getColor(context, R.color.acc)
    private val muted = ContextCompat.getColor(context, R.color.mut)
    private val track = ContextCompat.getColor(context, R.color.track)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = track
        strokeWidth = 1f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scores.isEmpty()) return

        val labelRoom = if (showLabels) 18f * density else 0f
        val pad = 8f * density
        val left = pad
        val right = width - pad
        val top = pad + labelRoom
        val bottom = height - pad

        // 가로 기준선(가운데)
        canvas.drawLine(left, (top + bottom) / 2, right, (top + bottom) / 2, gridPaint)

        var lo = scores.min()
        var hi = scores.max()
        if (hi - lo < 20) {
            val mid = (hi + lo) / 2
            lo = mid - 10
            hi = mid + 10
        }
        lo = (lo - 5).coerceAtLeast(0)
        hi = (hi + 5).coerceAtMost(100).coerceAtLeast(lo + 1)

        fun x(i: Int) = if (scores.size == 1) (left + right) / 2 else left + (right - left) * i / (scores.size - 1)
        fun y(score: Int) = bottom - (bottom - top) * (score - lo) / (hi - lo).toFloat()

        if (scores.size > 1) {
            val path = Path()
            scores.forEachIndexed { i, s -> if (i == 0) path.moveTo(x(i), y(s)) else path.lineTo(x(i), y(s)) }
            canvas.drawPath(path, linePaint)
        }
        scores.forEachIndexed { i, s ->
            val last = i == scores.size - 1
            canvas.drawCircle(x(i), y(s), (if (last) 5f else 3.5f) * density, dotPaint)
            if (showLabels) canvas.drawText(s.toString(), x(i), y(s) - 9f * density, textPaint)
        }
    }
}
