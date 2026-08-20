package com.example.accessiblevideoeditor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SelfieBackgroundRemover {

    /**
     * Processes the source video, segments the person/subject using Google ML Kit,
     * and overlays them on the specified background type (Green, Blue, Transparent, or Custom Image).
     */
    suspend fun removeBackground(
        context: Context,
        sourceVideoPath: String,
        tempOutputDir: File,
        bgType: String = "auto_subject", // auto_subject, green_screen, blue_screen, transparent, custom_bg
        customBgPath: String? = null,
        fpsOption: String = "auto"
    ): String? = withContext(Dispatchers.IO) {
        val segmenterOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(segmenterOptions)

        val framesDir = File(tempOutputDir, "frames")
        if (!framesDir.exists()) {
            framesDir.mkdirs()
        }

        val uniqueId = System.currentTimeMillis()
        val tempAudioPath = File(tempOutputDir, "temp_audio_$uniqueId.aac").absolutePath
        val tempOutputVideoPath = File(tempOutputDir, "temp_bg_$uniqueId.mp4").absolutePath

        var customBgBitmap: Bitmap? = null
        if (bgType == "custom_bg" && !customBgPath.isNullOrBlank()) {
            try {
                customBgBitmap = BitmapFactory.decodeFile(customBgPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            // 1. Extract Audio from source video
            val audioArgs = arrayOf("-y", "-i", sourceVideoPath, "-vn", "-c:a", "copy", tempAudioPath)
            FFmpegProcessor.executeWithProgress(audioArgs)

            // 2. Determine target FPS
            val activeFps = when (fpsOption) {
                "15" -> 15f
                "24" -> 24f
                "30" -> 30f
                "60" -> 60f
                else -> {
                    val detected = FFmpegProcessor.getVideoFrameRate(sourceVideoPath)
                    if (detected <= 0f) 30f else detected
                }
            }
            val fpsStr = String.format(java.util.Locale.US, "%.2f", activeFps)

            // 3. Extract frames
            val frameExtension = if (bgType == "transparent") "png" else "jpg"
            val extractFramesArgs = arrayOf(
                "-y",
                "-i", sourceVideoPath,
                "-vf", "fps=$fpsStr",
                "${framesDir.absolutePath}/frame_%04d.$frameExtension"
            )
            FFmpegProcessor.executeWithProgress(extractFramesArgs)

            val frameFiles = framesDir.listFiles { _, name -> name.startsWith("frame_") && name.endsWith(".$frameExtension") }
                ?.sortedBy { it.name } ?: emptyList()

            if (frameFiles.isEmpty()) return@withContext null

            var scaledBgBitmap: Bitmap? = null
            var bgPixels: IntArray? = null

            // 4. Run segmentation on each frame
            for (file in frameFiles) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()

                val width = mutableBitmap.width
                val height = mutableBitmap.height

                // Lazy initialize and scale custom background bitmap to fit the frame size
                if (bgType == "custom_bg" && customBgBitmap != null && scaledBgBitmap == null) {
                    try {
                        scaledBgBitmap = Bitmap.createScaledBitmap(customBgBitmap, width, height, true)
                        bgPixels = IntArray(width * height)
                        scaledBgBitmap.getPixels(bgPixels, 0, width, 0, 0, width, height)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val image = InputImage.fromBitmap(mutableBitmap, 0)
                try {
                    val task = segmenter.process(image)
                    val mask = Tasks.await(task) as com.google.mlkit.vision.segmentation.SegmentationMask

                    val maskWidth = mask.width
                    val maskHeight = mask.height
                    val maskBuffer = mask.buffer
                    maskBuffer.rewind()

                    val maskFloats = FloatArray(maskWidth * maskHeight)
                    maskBuffer.asFloatBuffer().get(maskFloats)

                    val pixels = IntArray(width * height)
                    mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    val lowThreshold = 0.20f
                    val highThreshold = 0.80f

                    for (y in 0 until height) {
                        val maskY = (y * maskHeight) / height
                        val rowOffset = y * width
                        val maskRowOffset = maskY * maskWidth
                        for (x in 0 until width) {
                            val maskX = (x * maskWidth) / width
                            val confidence = maskFloats[maskRowOffset + maskX]
                            val i = rowOffset + x
                            val origPixel = pixels[i]

                            // Normalized subject alpha (0.0 = full background, 1.0 = full subject)
                            val subjectAlpha = ((confidence - lowThreshold) / (highThreshold - lowThreshold)).coerceIn(0.0f, 1.0f)
                            val bgAlpha = 1.0f - subjectAlpha

                            if (subjectAlpha < 1.0f) {
                                when (bgType) {
                                    "transparent" -> {
                                        val origA = (origPixel ushr 24) and 0xFF
                                        val finalA = (origA * subjectAlpha).toInt().coerceIn(0, 255)
                                        pixels[i] = (finalA shl 24) or (origPixel and 0x00FFFFFF)
                                    }
                                    "blue_screen" -> {
                                        val origR = (origPixel ushr 16) and 0xFF
                                        val origG = (origPixel ushr 8) and 0xFF
                                        val origB = origPixel and 0xFF

                                        val r = (origR * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (origG * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (origB * subjectAlpha + 255 * bgAlpha).toInt().coerceIn(0, 255)

                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    "custom_bg" -> {
                                        val targetBgPixel = if (bgPixels != null) bgPixels[i] else 0xFF00FF00.toInt()
                                        val origR = (origPixel ushr 16) and 0xFF
                                        val origG = (origPixel ushr 8) and 0xFF
                                        val origB = origPixel and 0xFF

                                        val bgR = (targetBgPixel ushr 16) and 0xFF
                                        val bgG = (targetBgPixel ushr 8) and 0xFF
                                        val bgB = targetBgPixel and 0xFF

                                        val r = (origR * subjectAlpha + bgR * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (origG * subjectAlpha + bgG * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (origB * subjectAlpha + bgB * bgAlpha).toInt().coerceIn(0, 255)

                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    else -> { // auto_subject / green_screen
                                        val origR = (origPixel ushr 16) and 0xFF
                                        val origG = (origPixel ushr 8) and 0xFF
                                        val origB = origPixel and 0xFF

                                        val r = (origR * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (origG * subjectAlpha + 255 * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (origB * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)

                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                }
                            }
                        }
                    }

                    mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                    FileOutputStream(file).use { out ->
                        if (bgType == "transparent") {
                            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else {
                            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    mutableBitmap.recycle()
                }
            }

            scaledBgBitmap?.recycle()

            // 5. Re-compile frames back into a video and mix original audio
            val compileArgs = if (bgType == "transparent") {
                // Transparent output: VP9 codec with alpha inside MP4 container
                if (File(tempAudioPath).exists() && File(tempAudioPath).length() > 0) {
                    arrayOf(
                        "-y",
                        "-f", "image2",
                        "-framerate", fpsStr,
                        "-i", "${framesDir.absolutePath}/frame_%04d.png",
                        "-i", tempAudioPath,
                        "-map", "0:v",
                        "-map", "1:a",
                        "-c:v", "libvpx-vp9",
                        "-pix_fmt", "yuva420p",
                        "-c:a", "aac",
                        tempOutputVideoPath
                    )
                } else {
                    arrayOf(
                        "-y",
                        "-f", "image2",
                        "-framerate", fpsStr,
                        "-i", "${framesDir.absolutePath}/frame_%04d.png",
                        "-c:v", "libvpx-vp9",
                        "-pix_fmt", "yuva420p",
                        tempOutputVideoPath
                    )
                }
            } else {
                // Standard x264 MP4 output
                if (File(tempAudioPath).exists() && File(tempAudioPath).length() > 0) {
                    arrayOf(
                        "-y",
                        "-f", "image2",
                        "-framerate", fpsStr,
                        "-i", "${framesDir.absolutePath}/frame_%04d.jpg",
                        "-i", tempAudioPath,
                        "-map", "0:v",
                        "-map", "1:a",
                        "-c:v", "libx264",
                        "-preset", "ultrafast",
                        "-crf", "23",
                        "-c:a", "aac",
                        "-pix_fmt", "yuv420p",
                        tempOutputVideoPath
                    )
                } else {
                    arrayOf(
                        "-y",
                        "-f", "image2",
                        "-framerate", fpsStr,
                        "-i", "${framesDir.absolutePath}/frame_%04d.jpg",
                        "-c:v", "libx264",
                        "-preset", "ultrafast",
                        "-crf", "23",
                        "-pix_fmt", "yuv420p",
                        tempOutputVideoPath
                    )
                }
            }

            val compileSuccess = FFmpegProcessor.executeWithProgress(compileArgs)
            if (compileSuccess && File(tempOutputVideoPath).exists()) {
                return@withContext tempOutputVideoPath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Clean up temporary frames and audio
            try {
                framesDir.deleteRecursively()
                File(tempAudioPath).delete()
            } catch (_: Exception) {}
            segmenter.close()
            customBgBitmap?.recycle()
        }

        return@withContext null
    }
}
