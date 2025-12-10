//// utils/FaceDetector.kt
//package com.shelfx.checkapplication.utils
//
//import android.graphics.Bitmap
//import android.graphics.PointF
//import android.graphics.Rect
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.Face
//import com.google.mlkit.vision.face.FaceDetection
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import com.google.mlkit.vision.face.FaceLandmark
//import kotlinx.coroutines.tasks.await
//
//data class FaceDetectionResult(
//    val boundingBox: Rect,
//    val leftEye: PointF?,
//    val rightEye: PointF?,
//    val nose: PointF?,
//    val leftMouth: PointF?,
//    val rightMouth: PointF?,
//    val confidence: Float?
//)
//
//class FaceDetector {
//    private val options = FaceDetectorOptions.Builder()
//        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//        .setMinFaceSize(0.15f)
//        .enableTracking()
//        .build()
//
//    private val detector = FaceDetection.getClient(options)
//
//    suspend fun detectFaces(bitmap: Bitmap): List<FaceDetectionResult> {
//        val image = InputImage.fromBitmap(bitmap, 0)
//        val faces = detector.process(image).await()
//        return faces.map { face ->
//            FaceDetectionResult(
//                boundingBox = face.boundingBox,
//                leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
//                rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
//                nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
//                leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
//                rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position,
//                confidence = face.trackingId?.toFloat()
//            )
//        }
//    }
//
//    suspend fun detectLargestFace(bitmap: Bitmap): FaceDetectionResult? {
//        val faces = detectFaces(bitmap)
//        return faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
//    }
//
//    fun close() {
//        detector.close()
//    }
//}



// utils/FaceDetector.kt
package com.shelfx.checkapplication.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

data class FaceDetectionResult(
    val boundingBox: Rect,
    val leftEye: PointF?,
    val rightEye: PointF?,
    val nose: PointF?,
    val leftMouth: PointF?,
    val rightMouth: PointF?,
    val confidence: Float?
)

data class ProcessedFaceResult(
    val originalBitmap: Bitmap,
    val croppedFace: Bitmap,
    val detectionResult: FaceDetectionResult,
    val visualizationBitmap: Bitmap // Shows bounding box on original
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

    /**
     * Crop face from the bitmap with optional padding
     * @param bitmap Original image
     * @param paddingRatio Percentage of padding to add around face (0.0 to 1.0)
     * @return Cropped face bitmap or null if no face detected
     */


    suspend fun cropFace(bitmap: Bitmap, paddingRatio: Float = 0.2f): Bitmap? {
        val faceResult = detectLargestFace(bitmap) ?: return null
        return cropFaceFromBoundingBox(bitmap, faceResult.boundingBox, paddingRatio)
    }

    /**
     * Convenience helper: detect the largest face and return cropped bitmap.
     * Returns null if no face is found.
     */
    suspend fun detectAndCropLargestFace(bitmap: Bitmap, paddingRatio: Float = 0.2f): Bitmap? {
        val face = detectLargestFace(bitmap) ?: return null
        return cropFaceFromBoundingBox(bitmap, face.boundingBox, paddingRatio)
    }

    /**
     * Crop face from bounding box
     */
    private fun cropFaceFromBoundingBox(
        bitmap: Bitmap,
        boundingBox: Rect,
        paddingRatio: Float = 0.2f
    ): Bitmap {
        // Calculate padding based on face size
        val faceWidth = boundingBox.width()
        val faceHeight = boundingBox.height()
        val maxDimension = max(faceWidth, faceHeight)
        val padding = (maxDimension * paddingRatio).toInt()

        // Apply padding while staying within image bounds
        val left = max(0, boundingBox.left - padding)
        val top = max(0, boundingBox.top - padding)
        val right = min(bitmap.width, boundingBox.right + padding)
        val bottom = min(bitmap.height, boundingBox.bottom + padding)

        val width = right - left
        val height = bottom - top

        // Create cropped bitmap
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Process face with complete visualization
     * Returns original, cropped, and visualization with bounding box
     */
    suspend fun processAndVisualizeFace(
        bitmap: Bitmap,
        paddingRatio: Float = 0.2f
    ): ProcessedFaceResult? {
        val faceResult = detectLargestFace(bitmap) ?: return null

        // Crop the face
        val croppedFace = cropFaceFromBoundingBox(bitmap, faceResult.boundingBox, paddingRatio)

        // Create visualization with bounding box
        val visualizationBitmap = drawBoundingBox(bitmap, faceResult)

        return ProcessedFaceResult(
            originalBitmap = bitmap,
            croppedFace = croppedFace,
            detectionResult = faceResult,
            visualizationBitmap = visualizationBitmap
        )
    }

    /**
     * Draw bounding box and landmarks on the image
     */
    private fun drawBoundingBox(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Draw bounding box
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(faceResult.boundingBox, boxPaint)

        // Draw landmarks
        val landmarkPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        val landmarkRadius = 12f

        faceResult.leftEye?.let {
            canvas.drawCircle(it.x, it.y, landmarkRadius, landmarkPaint)
        }
        faceResult.rightEye?.let {
            canvas.drawCircle(it.x, it.y, landmarkRadius, landmarkPaint)
        }
        faceResult.nose?.let {
            canvas.drawCircle(it.x, it.y, landmarkRadius, landmarkPaint)
        }
        faceResult.leftMouth?.let {
            canvas.drawCircle(it.x, it.y, landmarkRadius, landmarkPaint)
        }
        faceResult.rightMouth?.let {
            canvas.drawCircle(it.x, it.y, landmarkRadius, landmarkPaint)
        }

        // Draw labels
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            style = Paint.Style.FILL
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        val text = "Face: ${faceResult.boundingBox.width()}x${faceResult.boundingBox.height()}"
        canvas.drawText(
            text,
            faceResult.boundingBox.left.toFloat(),
            faceResult.boundingBox.top.toFloat() - 20,
            textPaint
        )

        return mutableBitmap
    }

    /**
     * Get multiple processed faces if needed
     */
    suspend fun processAllFaces(
        bitmap: Bitmap,
        paddingRatio: Float = 0.2f
    ): List<Bitmap> {
        val faces = detectFaces(bitmap)
        return faces.map { faceResult ->
            cropFaceFromBoundingBox(bitmap, faceResult.boundingBox, paddingRatio)
        }
    }


    
    fun close() {
        detector.close()
    }
}