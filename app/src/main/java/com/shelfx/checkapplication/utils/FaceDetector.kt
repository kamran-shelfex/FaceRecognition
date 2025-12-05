// utils/FaceDetector.kt
package com.shelfx.checkapplication.utils

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await

data class FaceDetectionResult(
    val boundingBox: Rect,
    val leftEye: PointF?,
    val rightEye: PointF?,
    val nose: PointF?,
    val leftMouth: PointF?,
    val rightMouth: PointF?,
    val confidence: Float?
)

class FaceDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    suspend fun detectFaces(bitmap: Bitmap): List<FaceDetectionResult> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        return faces.map { face ->
            FaceDetectionResult(
                boundingBox = face.boundingBox,
                leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
                rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
                nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
                leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
                rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position,
                confidence = face.trackingId?.toFloat()
            )
        }
    }

    suspend fun detectLargestFace(bitmap: Bitmap): FaceDetectionResult? {
        val faces = detectFaces(bitmap)
        return faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    }

    fun close() {
        detector.close()
    }
}