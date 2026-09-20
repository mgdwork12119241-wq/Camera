package org.fossify.camera.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

enum class NovaFaceFilter(val title: String) {
    OFF("إيقاف"),
    GLASSES("نظارات"),
    BUNNY("أرنب"),
    CROWN("تاج"),
    HEARTS("قلوب"),
    PARTY("حفلة"),
    ROBOT("روبوت"),
}

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
        textSize = 30f
        style = Paint.Style.FILL
        isFakeBoldText = true
    }

    private val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = 5f
    }

    private val filterStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private var results: List<FaceOverlayResult> = emptyList()
    private var currentFilter = NovaFaceFilter.OFF

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

    fun cycleFilter(): NovaFaceFilter {
        val values = NovaFaceFilter.values()
        currentFilter = values[(currentFilter.ordinal + 1) % values.size]
        invalidate()
        return currentFilter
    }

    fun setFilter(filter: NovaFaceFilter) {
        currentFilter = filter
        invalidate()
    }

    fun getFilter(): NovaFaceFilter = currentFilter

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results.forEach { face ->
            if (currentFilter == NovaFaceFilter.OFF) {
                drawDebugFace(canvas, face)
            } else {
                drawFilter(canvas, face)
            }
        }
    }

    private fun drawDebugFace(canvas: Canvas, face: FaceOverlayResult) {
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
        val labelX = max(8f, face.bounds.left)
        val labelY = max(36f, face.bounds.top - 12f)
        canvas.drawText(label, labelX, labelY, textPaint)
    }

    private fun drawFilter(canvas: Canvas, face: FaceOverlayResult) {
        val eyes = face.points.filter { it.kind == "eye" }
        val mouths = face.points.filter { it.kind == "mouth" }
        val nose = face.points.firstOrNull { it.kind == "nose" }
        val leftEye = eyes.getOrNull(0)
        val rightEye = eyes.getOrNull(1)
        val eyeDistance = if (leftEye != null && rightEye != null) {
            kotlin.math.hypot(
                (leftEye.x - rightEye.x).toDouble(),
                (leftEye.y - rightEye.y).toDouble()
            ).toFloat()
        } else {
            face.bounds.width() * .35f
        }

        when (currentFilter) {
            NovaFaceFilter.GLASSES -> drawGlasses(canvas, leftEye, rightEye, eyeDistance)
            NovaFaceFilter.BUNNY -> drawBunny(canvas, face)
            NovaFaceFilter.CROWN -> drawCrown(canvas, face)
            NovaFaceFilter.HEARTS -> drawHearts(canvas, face, mouths)
            NovaFaceFilter.PARTY -> drawParty(canvas, face, eyeDistance)
            NovaFaceFilter.ROBOT -> drawRobot(canvas, face, leftEye, rightEye, nose)
            NovaFaceFilter.OFF -> Unit
        }

        if ((face.smileProbability ?: 0f) >= .65f) {
            drawSmileSparkles(canvas, face, eyeDistance)
        }
    }

    private fun drawGlasses(canvas: Canvas, left: FaceLandmarkPoint?, right: FaceLandmarkPoint?, distance: Float) {
        if (left == null || right == null) return
        val r = max(18f, distance * .23f)
        filterStroke.color = 0xFF111111.toInt()
        filterStroke.strokeWidth = max(4f, distance * .045f)
        canvas.drawCircle(left.x, left.y, r, filterStroke)
        canvas.drawCircle(right.x, right.y, r, filterStroke)
        canvas.drawLine(left.x + r, left.y, right.x - r, right.y, filterStroke)
        canvas.drawLine(left.x - r, left.y, left.x - r * 1.7f, left.y - r * .15f, filterStroke)
        canvas.drawLine(right.x + r, right.y, right.x + r * 1.7f, right.y - r * .15f, filterStroke)
        filterPaint.color = 0x5533B5E5
        canvas.drawCircle(left.x, left.y, r * .82f, filterPaint)
        canvas.drawCircle(right.x, right.y, r * .82f, filterPaint)
    }

    private fun drawBunny(canvas: Canvas, face: FaceOverlayResult) {
        val cx = face.bounds.centerX()
        val top = face.bounds.top
        val w = face.bounds.width()
        val earW = w * .22f
        val earH = w * .48f
        filterPaint.color = 0xFFFFB6D9.toInt()
        canvas.drawOval(RectF(cx - w * .36f, top - earH, cx - w * .36f + earW, top + earW), filterPaint)
        canvas.drawOval(RectF(cx + w * .14f, top - earH, cx + w * .14f + earW, top + earW), filterPaint)
        filterPaint.color = 0xFFFFE0EF.toInt()
        canvas.drawOval(RectF(cx - w * .36f + earW * .27f, top - earH * .82f, cx - w * .36f + earW * .73f, top + earW * .55f), filterPaint)
        canvas.drawOval(RectF(cx + w * .14f + earW * .27f, top - earH * .82f, cx + w * .14f + earW * .73f, top + earW * .55f), filterPaint)
        filterPaint.color = 0xFFFF69B4.toInt()
        canvas.drawCircle(cx, face.bounds.bottom - w * .18f, w * .055f, filterPaint)
    }

    private fun drawCrown(canvas: Canvas, face: FaceOverlayResult) {
        val b = face.bounds
        val y = b.top - b.height() * .12f
        val path = Path().apply {
            moveTo(b.left + b.width() * .08f, y)
            lineTo(b.left + b.width() * .22f, y - b.height() * .30f)
            lineTo(b.left + b.width() * .40f, y - b.height() * .08f)
            lineTo(b.left + b.width() * .52f, y - b.height() * .36f)
            lineTo(b.left + b.width() * .68f, y - b.height() * .08f)
            lineTo(b.left + b.width() * .86f, y - b.height() * .30f)
            lineTo(b.right - b.width() * .08f, y)
            close()
        }
        filterPaint.color = 0xFFFFD54F.toInt()
        canvas.drawPath(path, filterPaint)
        filterStroke.color = 0xFFFFA000.toInt()
        canvas.drawPath(path, filterStroke)
        filterPaint.color = 0xFFE91E63.toInt()
        canvas.drawCircle(b.centerX(), y - b.height() * .22f, b.width() * .035f, filterPaint)
    }

    private fun drawHearts(canvas: Canvas, face: FaceOverlayResult, mouths: List<FaceLandmarkPoint>) {
        val size = face.bounds.width() * .13f
        val y = mouths.firstOrNull()?.y ?: face.bounds.bottom - face.bounds.height() * .3f
        drawHeart(canvas, face.bounds.left + face.bounds.width() * .18f, y, size)
        drawHeart(canvas, face.bounds.right - face.bounds.width() * .18f, y, size)
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path().apply {
            moveTo(x, y + size * .9f)
            cubicTo(x - size * 1.6f, y - size * .2f, x - size, y - size * 1.4f, x, y - size * .55f)
            cubicTo(x + size, y - size * 1.4f, x + size * 1.6f, y - size * .2f, x, y + size * .9f)
            close()
        }
        filterPaint.color = 0xFFFF4081.toInt()
        canvas.drawPath(path, filterPaint)
    }

    private fun drawParty(canvas: Canvas, face: FaceOverlayResult, distance: Float) {
        filterPaint.color = 0xFF7C4DFF.toInt()
        canvas.drawCircle(face.bounds.left + face.bounds.width() * .18f, face.bounds.top, max(12f, distance * .16f), filterPaint)
        filterPaint.color = 0xFFFF4081.toInt()
        canvas.drawCircle(face.bounds.right - face.bounds.width() * .18f, face.bounds.top, max(12f, distance * .16f), filterPaint)
        filterPaint.color = 0xFF00BCD4.toInt()
        canvas.drawCircle(face.bounds.centerX(), face.bounds.top - face.bounds.height() * .12f, max(10f, distance * .10f), filterPaint)
        drawSparkle(canvas, face.bounds.centerX(), face.bounds.bottom + face.bounds.height() * .06f, distance * .10f)
    }

    private fun drawRobot(
        canvas: Canvas,
        face: FaceOverlayResult,
        leftEye: FaceLandmarkPoint?,
        rightEye: FaceLandmarkPoint?,
        nose: FaceLandmarkPoint?
    ) {
        val b = RectF(face.bounds)
        b.inset(-b.width() * .04f, -b.height() * .04f)
        filterStroke.color = 0xFF80DEEA.toInt()
        filterStroke.strokeWidth = max(4f, b.width() * .025f)
        canvas.drawRoundRect(b, b.width() * .12f, b.width() * .12f, filterStroke)
        leftEye?.let { drawRobotEye(canvas, it.x, it.y, b.width() * .06f) }
        rightEye?.let { drawRobotEye(canvas, it.x, it.y, b.width() * .06f) }
        nose?.let {
            filterStroke.color = 0xFF26C6DA.toInt()
            canvas.drawLine(it.x, it.y, it.x, it.y + b.height() * .10f, filterStroke)
        }
        val mouthY = face.points.filter { it.kind == "mouth" }.map { it.y }.average().toFloat()
        if (mouthY.isFinite()) {
            filterStroke.color = 0xFF26C6DA.toInt()
            canvas.drawLine(b.left + b.width() * .28f, mouthY, b.right - b.width() * .28f, mouthY, filterStroke)
        }
    }

    private fun drawRobotEye(canvas: Canvas, x: Float, y: Float, radius: Float) {
        filterPaint.color = 0xFF00E5FF.toInt()
        canvas.drawCircle(x, y, radius, filterPaint)
        filterPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, radius * .35f, filterPaint)
    }

    private fun drawSmileSparkles(canvas: Canvas, face: FaceOverlayResult, distance: Float) {
        drawSparkle(canvas, face.bounds.left, face.bounds.centerY(), max(8f, distance * .08f))
        drawSparkle(canvas, face.bounds.right, face.bounds.centerY(), max(8f, distance * .08f))
    }

    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, size: Float) {
        filterStroke.color = 0xFFFFF176.toInt()
        filterStroke.strokeWidth = max(3f, size * .18f)
        canvas.drawLine(x - size, y, x + size, y, filterStroke)
        canvas.drawLine(x, y - size, x, y + size, filterStroke)
    }
}
