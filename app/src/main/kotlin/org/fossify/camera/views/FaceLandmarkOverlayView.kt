package org.fossify.camera.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

data class FaceLandmarkPoint(
    val x: Float,
    val y: Float,
    val kind: String,
)

data class FaceOverlayResult(
    val bounds: RectF,
    val points: List<FaceLandmarkPoint>,
    val smileProbability: Float?,
    val trackingId: Int?,
)

class FaceLandmarkOverlayView(context: Context) : View(context) {
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        style = Paint.Style.FILL
    }

    private var results: List<FaceOverlayResult> = emptyList()

    init {
        setWillNotDraw(false)
        elevation = 20f
    }

    fun setResults(newResults: List<FaceOverlayResult>) {
        results = newResults
        postInvalidateOnAnimation()
    }

    fun clear() {
        setResults(emptyList())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results.forEach { face ->
            facePaint.color = 0xFF00E5FF.toInt()
            canvas.drawRoundRect(face.bounds, 28f, 28f, facePaint)

            face.points.forEach { point ->
                pointPaint.color = when (point.kind) {
                    "eye", "mouth" -> 0xFFFFD54F.toInt()
                    "nose" -> 0xFF69F0AE.toInt()
                    else -> 0xFF40C4FF.toInt()
                }
                canvas.drawCircle(point.x, point.y, 7f, pointPaint)
            }

            val label = buildString {
                append("Nova AI")
                face.trackingId?.let { append("  #").append(it) }
                face.smileProbability?.let {
                    append("  🙂 ").append((it * 100).toInt()).append("%")
                }
            }

            textPaint.color = 0xFFFFFFFF.toInt()
            val labelWidth = textPaint.measureText(label)
            val labelX = max(8f, face.bounds.left)
            val labelY = max(36f, face.bounds.top - 12f)
            canvas.drawText(label, labelX, labelY, textPaint)
        }
    }
}
