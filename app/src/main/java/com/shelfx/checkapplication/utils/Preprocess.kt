// utils/Preprocess.kt
package com.shelfx.checkapplication.utils

import android.graphics.Bitmap
import android.util.Log

class Preprocess(
    private val faceDetector: FaceDetector,
    private val faceAlign: FaceAlign,

) {
    companion object {
        private const val TAG = "Preprocess"
    }
    suspend fun preprocessForRecognition(bitmap: Bitmap): Bitmap? {
        return try {
            // Detect largest face
            val faceResult = faceDetector.detectLargestFace(bitmap)

            if (faceResult == null) {
                Log.w(TAG, "No face detected in image")
                return null
            }

            Log.d(TAG, "Face detected at: ${faceResult.boundingBox}")

            // Align face
            val alignedFace = faceAlign.alignFaceWithLandmarks(faceResult = faceResult, bitmap = bitmap)

            if (alignedFace == null) {
                Log.w("Face alignment", "Face alignment failed")
                return null
            }

            Log.d(TAG, "Face aligned successfully: ${alignedFace.width}x${alignedFace.height}")
            alignedFace

        } catch (e: Exception) {
            Log.e(TAG, "Error in preprocessing: ${e.message}")
            null
        }
    }

    /**
     * Preprocess and return both aligned face and detection result
     */
    suspend fun preprocessWithDetails(bitmap: Bitmap): PreprocessResult? {
        return try {
            val faceResult = faceDetector.detectLargestFace(bitmap)

            if (faceResult == null) {
                return null
            }

            val alignedFace = faceAlign.alignFaceWithLandmarks(bitmap, faceResult)

            if (alignedFace == null) {
                return null
            }

            PreprocessResult(
                alignedFace = alignedFace,
                faceDetectionResult = faceResult,
                originalBitmap = bitmap
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in preprocessing: ${e.message}")
            null
        }
    }


}

data class PreprocessResult(
    val alignedFace: Bitmap,
    val faceDetectionResult: FaceDetectionResult,
    val originalBitmap: Bitmap
)