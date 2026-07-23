package com.example.accessiblevideoeditor.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class OcrProcessor {

    private suspend fun describeImageWithGemini(context: Context, bitmap: Bitmap, prompt: String): String {
        val apiKey = SettingsManager.geminiApiKey.trim()
        if (apiKey.isBlank()) {
            return "Gemini API Key is missing. Please set it in Settings."
        }
        
        val userModel = SettingsManager.geminiModel
        return try {
            val model = GenerativeModel(
                modelName = if (userModel.isNotBlank()) userModel else "gemini-2.5-flash",
                apiKey = apiKey
            )
            val inputContent = content {
                image(bitmap)
                text(prompt)
            }
            model.generateContent(inputContent).text ?: "Failed to extract text."
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback
            try {
                val fallbackModel = GenerativeModel(
                    modelName = "gemini-2.0-flash",
                    apiKey = apiKey
                )
                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }
                fallbackModel.generateContent(inputContent).text ?: "Failed to extract text."
            } catch (ex: Exception) {
                ex.printStackTrace()
                val errorMsg = ex.message ?: ""
                if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("Unexpected Response")) {
                    try {
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_228)
                    } catch (_: Exception) {
                        "الخدمة مشغولة حالياً، يرجى المحاولة لاحقاً"
                    }
                } else {
                    "Error processing image: $errorMsg"
                }
            }
        }
    }

    suspend fun extractTextFromImage(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }
            describeImageWithGemini(context, bitmap, "Please extract all text from this image exactly as written. If there is no text, say 'No text found in image.'")
        } catch (e: Exception) {
            e.printStackTrace()
            "Error extracting text: ${e.message}"
        }
    }

    suspend fun extractTextFromVideoFrame(context: Context, videoUri: Uri, timeInSeconds: Long): String = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val timeInMicroseconds = timeInSeconds * 1000000L
            val frame: Bitmap? = retriever.getFrameAtTime(timeInMicroseconds, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            
            if (frame != null) {
                describeImageWithGemini(context, frame, "Please extract all text from this image exactly as written. If there is no text, say 'No text found at this time.'")
            } else {
                "Failed to extract frame at ${timeInSeconds}s."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error processing video frame: ${e.message}"
        } finally {
            retriever?.release()
        }
    }

    fun release() {
        // Nothing to release
    }
}
