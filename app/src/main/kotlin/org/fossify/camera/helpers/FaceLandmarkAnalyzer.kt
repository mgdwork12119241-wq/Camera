package org.fossify.camera.helpers

import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.common.InputImage
import org.fossify.camera.views.FaceLandmarkPoint
import org.fossify.camera.views.FaceLandmarkOverlayView
import kotlin.math.max
import kotlin.math.min

class FaceLandmarkAnalyzer(
    private val previewView: PreviewView,
    private val overlayView: FaceLandmarkOverlayView,
    private val isFrontCamera: () -> Boolean,
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                val transformed = faces.mapNotNull { transformFace(it, imageProxy) }
                overlayView.setResults(transformed)
            }
            .addOnFailureListener {
                // Keep the camera usable even if a frame cannot be analyzed.
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun transformFace(face: Face, imageProxy: ImageProxy): org.fossify.camera.views.FaceOverlayResult? {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val sourceWidth = imageProxy.width
        val sourceHeight = imageProxy.height
        val rotatedSize = if (rotation == 90 || rotation == 270) {
            Size(sourceHeight, sourceWidth)
        } else {
            Size(sourceWidth, sourceHeight)
        }

        if (rotatedSize.width <= 0 || rotatedSize.height <= 0 ||
            previewView.width <= 0 || previewView.height <= 0
        ) {
            return null
        }

        fun map(x: Float, y: Float): Pair<Float, Float> {
            val rotated = when (rotation) {
                90 -> Pair(sourceHeight - y, x)
                180 -> Pair(sourceWidth - x, sourceHeight - y)
                270 -> Pair(y, sourceWidth - x)
                else -> Pair(x, y)
            }

            val scaleX = previewView.width / rotatedSize.width.toFloat()
            val scaleY = previewView.height / rotatedSize.height.toFloat()
            val scale = if (previewView.scaleType == PreviewView.ScaleType.FILL_CENTER) {
                max(scaleX, scaleY)
            } else {
                min(scaleX, scaleY)
            }

            val contentWidth = rotatedSize.width * scale
            val contentHeight = rotatedSize.height * scale
            val offsetX = (previewView.width - contentWidth) / 2f
            val offsetY = (previewView.height - contentHeight) / 2f

            var px = rotated.first * scale + offsetX
            val py = rotated.second * scale + offsetY

            if (isFrontCamera()) {
                px = previewView.width - px
            }
            return Pair(px, py)
        }

        val bounds = face.boundingBox
        val topLeft = map(bounds.left.toFloat(), bounds.top.toFloat())
        val bottomRight = map(bounds.right.toFloat(), bounds.bottom.toFloat())

        val mappedLeft = min(topLeft.first, bottomRight.first)
        val mappedTop = min(topLeft.second, bottomRight.second)
        val mappedRight = max(topLeft.first, bottomRight.first)
        val mappedBottom = max(topLeft.second, bottomRight.second)

        val points = mutableListOf<FaceLandmarkPoint>()
        val landmarks = listOf(
            FaceLandmark.LEFT_EYE to "eye",
            FaceLandmark.RIGHT_EYE to "eye",
            FaceLandmark.NOSE_BASE to "nose",
            FaceLandmark.LEFT_MOUTH to "mouth",
            FaceLandmark.RIGHT_MOUTH to "mouth",
            FaceLandmark.BOTTOM_MOUTH to "mouth",
            FaceLandmark.LEFT_CHEEK to "cheek",
            FaceLandmark.RIGHT_CHEEK to "cheek",
            FaceLandmark.LEFT_EAR to "ear",
            FaceLandmark.RIGHT_EAR to "ear",
        )

        landmarks.forEach { (type, kind) ->
            face.getLandmark(type)?.position?.let { point ->
                val mapped = map(point.x, point.y)
                points += FaceLandmarkPoint(mapped.first, mapped.second, kind)
            }
        }

        return org.fossify.camera.views.FaceOverlayResult(
            bounds = RectF(mappedLeft, mappedTop, mappedRight, mappedBottom),
            points = points,
            smileProbability = face.smilingProbability,
            trackingId = face.trackingId,
        )
    }

    fun close() {
        detector.close()
    }
}
