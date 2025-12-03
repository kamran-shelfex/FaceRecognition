// utils/FaceAlign.kt
package com.shelfx.checkapplication.utils

import android.graphics.*
import kotlin.math.*

class FaceAlign {

    /**
     * Align face using eye positions for better accuracy
     */
    fun alignFaceWithLandmarks(
        bitmap: Bitmap,
        faceResult: FaceDetectionResult,
        targetSize: Int = 112
    ): Bitmap? {
        val leftEye = faceResult.leftEye
        val rightEye = faceResult.rightEye

        // If we have eye landmarks, use them for precise alignment
        return if (leftEye != null && rightEye != null) {
            alignFaceUsingEyes(bitmap, leftEye, rightEye, targetSize)
        } else {
            // Fallback to bounding box alignment
            alignFaceUsingBoundingBox(bitmap, faceResult.boundingBox, targetSize)
        }
    }

    /**
     * Align face using eye positions (more accurate)
     */
    private fun alignFaceUsingEyes(
        bitmap: Bitmap,
        leftEye: PointF,
        rightEye: PointF,
        targetSize: Int
    ): Bitmap {
        // Calculate angle between eyes
        val dY = rightEye.y - leftEye.y
        val dX = rightEye.x - leftEye.x
        val angle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()

        // Calculate center point between eyes
        val eyesCenterX = (leftEye.x + rightEye.x) / 2
        val eyesCenterY = (leftEye.y + rightEye.y) / 2

        // Calculate scale
        val eyeDistance = sqrt((dX * dX + dY * dY).toDouble()).toFloat()
        val desiredEyeDistance = targetSize * 0.4f // Eyes should be ~40% of face width
        val scale = desiredEyeDistance / eyeDistance

        // Create transformation matrix
        val matrix = Matrix().apply {
            // Rotate around eyes center
            postRotate(angle, eyesCenterX, eyesCenterY)
            // Scale
            postScale(scale, scale, eyesCenterX, eyesCenterY)
            // Translate to center
            postTranslate(targetSize / 2f - eyesCenterX * scale, targetSize / 3f - eyesCenterY * scale)
        }

        // Apply transformation
        return Bitmap.createBitmap(
            targetSize,
            targetSize,
            Bitmap.Config.ARGB_8888
        ).also { output ->
            val canvas = Canvas(output)
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    /**
     * Align face using bounding box (fallback method)
     */
    fun alignFaceUsingBoundingBox(
        bitmap: Bitmap,
        faceRect: Rect,
        targetSize: Int = 112,
        padding: Float = 0.3f
    ): Bitmap {
        // Add padding around face
        val width = faceRect.width()
        val height = faceRect.height()
        val paddingX = (width * padding).toInt()
        val paddingY = (height * padding).toInt()

        val left = maxOf(0, faceRect.left - paddingX)
        val top = maxOf(0, faceRect.top - paddingY)
        val right = minOf(bitmap.width, faceRect.right + paddingX)
        val bottom = minOf(bitmap.height, faceRect.bottom + paddingY)

        // Crop face with padding
        val croppedFace = Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )

        // Resize to target size
        return Bitmap.createScaledBitmap(croppedFace, targetSize, targetSize, true)
    }

    /**
     * Simple alignment - just crop and resize
     */
    fun simpleAlign(bitmap: Bitmap, faceRect: Rect, targetSize: Int = 112): Bitmap {
        return alignFaceUsingBoundingBox(bitmap, faceRect, targetSize, 0.2f)
    }
}