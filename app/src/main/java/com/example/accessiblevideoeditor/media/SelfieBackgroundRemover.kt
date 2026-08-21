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

                    // 1. Erosion to contract the mask slightly (radius = 1 pixel on 256x256 mask)
                    val erodedMask = FloatArray(maskWidth * maskHeight)
                    val erodeRadius = 1
                    for (my in 0 until maskHeight) {
                        val myOffset = my * maskWidth
                        for (mx in 0 until maskWidth) {
                            var minVal = 1.0f
                            for (dy in -erodeRadius..erodeRadius) {
                                val ny = (my + dy).coerceIn(0, maskHeight - 1)
                                val nyOffset = ny * maskWidth
                                for (dx in -erodeRadius..erodeRadius) {
                                    val nx = (mx + dx).coerceIn(0, maskWidth - 1)
                                    val v = maskFloats[nyOffset + nx]
                                    if (v < minVal) {
                                        minVal = v
                                    }
                                }
                            }
                            erodedMask[myOffset + mx] = minVal
                        }
                    }

                    // 2. Box Blur to smooth out the mask's edges
                    val processedMask = FloatArray(maskWidth * maskHeight)
                    val blurRadius = 1
                    for (my in 0 until maskHeight) {
                        val myOffset = my * maskWidth
                        for (mx in 0 until maskWidth) {
                            var sum = 0.0f
                            var count = 0
                            for (dy in -blurRadius..blurRadius) {
                                val ny = (my + dy).coerceIn(0, maskHeight - 1)
                                val nyOffset = ny * maskWidth
                                for (dx in -blurRadius..blurRadius) {
                                    val nx = (mx + dx).coerceIn(0, maskWidth - 1)
                                    sum += erodedMask[nyOffset + nx]
                                    count++
                                }
                            }
                            processedMask[myOffset + mx] = sum / count
                        }
                    }

                    val lowThreshold = 0.20f
                    val highThreshold = 0.80f
                    val xFactor = (maskWidth - 1).toFloat() / width.coerceAtLeast(1)
                    val yFactor = (maskHeight - 1).toFloat() / height.coerceAtLeast(1)

                    // 3. Bilinear Interpolation mapping from high-res image to low-res mask
                    for (y in 0 until height) {
                        val gy = y * yFactor
                        val y0 = gy.toInt()
                        val y1 = (y0 + 1).coerceAtMost(maskHeight - 1)
                        val dy = gy - y0
                        val y0Offset = y0 * maskWidth
                        val y1Offset = y1 * maskWidth
                        val rowOffset = y * width

                        for (x in 0 until width) {
                            val gx = x * xFactor
                            val x0 = gx.toInt()
                            val x1 = (x0 + 1).coerceAtMost(maskWidth - 1)
                            val dx = gx - x0

                            val val00 = processedMask[y0Offset + x0]
                            val val10 = processedMask[y0Offset + x1]
                            val val01 = processedMask[y1Offset + x0]
                            val val11 = processedMask[y1Offset + x1]

                            val top = val00 * (1.0f - dx) + val10 * dx
                            val bottom = val01 * (1.0f - dx) + val11 * dx
                            val confidence = top * (1.0f - dy) + bottom * dy

                            val i = rowOffset + x
                            val origPixel = pixels[i]

                            // Normalized subject alpha (0.0 = full background, 1.0 = full subject)
                            val subjectAlpha = ((confidence - lowThreshold) / (highThreshold - lowThreshold)).coerceIn(0.0f, 1.0f)
                            val bgAlpha = 1.0f - subjectAlpha

                            if (subjectAlpha < 1.0f) {
                                // Extract original channels
                                var cleanR = (origPixel ushr 16) and 0xFF
                                var cleanG = (origPixel ushr 8) and 0xFF
                                var cleanB = origPixel and 0xFF

                                // 4. Dynamic Spill Suppression on subject edges
                                if (cleanG > cleanR && cleanG > cleanB) {
                                    val avg = (cleanR + cleanB) / 2
                                    cleanG = (cleanG * subjectAlpha + avg * (1.0f - subjectAlpha)).toInt().coerceIn(0, 255)
                                } else if (cleanB > cleanR && cleanB > cleanG) {
                                    val avg = (cleanR + cleanG) / 2
                                    cleanB = (cleanB * subjectAlpha + avg * (1.0f - subjectAlpha)).toInt().coerceIn(0, 255)
                                }

                                when (bgType) {
                                    "transparent" -> {
                                        val origA = (origPixel ushr 24) and 0xFF
                                        val finalA = (origA * subjectAlpha).toInt().coerceIn(0, 255)
                                        pixels[i] = (finalA shl 24) or (cleanR shl 16) or (cleanG shl 8) or cleanB
                                    }
                                    "blue_screen" -> {
                                        val r = (cleanR * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (cleanG * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (cleanB * subjectAlpha + 255 * bgAlpha).toInt().coerceIn(0, 255)
                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    "custom_bg" -> {
                                        val targetBgPixel = if (bgPixels != null) bgPixels[i] else 0xFF00FF00.toInt()
                                        val bgR = (targetBgPixel ushr 16) and 0xFF
                                        val bgG = (targetBgPixel ushr 8) and 0xFF
                                        val bgB = targetBgPixel and 0xFF

                                        val r = (cleanR * subjectAlpha + bgR * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (cleanG * subjectAlpha + bgG * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (cleanB * subjectAlpha + bgB * bgAlpha).toInt().coerceIn(0, 255)
                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    else -> { // auto_subject / green_screen
                                        val r = (cleanR * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
                                        val g = (cleanG * subjectAlpha + 255 * bgAlpha).toInt().coerceIn(0, 255)
                                        val b = (cleanB * subjectAlpha + 0 * bgAlpha).toInt().coerceIn(0, 255)
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
