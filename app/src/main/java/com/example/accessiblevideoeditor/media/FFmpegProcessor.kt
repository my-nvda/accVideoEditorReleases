package com.example.accessiblevideoeditor.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.accessiblevideoeditor.ui.ProcessingManager

/**
 * Wrapper for FFmpeg Kit to handle common media operations.
 */
object FFmpegProcessor {

    suspend fun executeWithProgress(commandArgs: Array<String>, sourceVideo: String? = null): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        var durationMs = 1f
        if (sourceVideo != null) {
            val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(sourceVideo)
            val durationString = mediaInfo.mediaInformation?.duration
            val parsedDuration = durationString?.toFloatOrNull() ?: 0f
            if (parsedDuration > 0) {
                durationMs = parsedDuration * 1000f
            } else {
                durationMs = 1000000000f // Prevent fast progress if duration is unknown
            }
        }

        var startTime = System.currentTimeMillis()
        var maxPercentage = 0f

        val session = FFmpegKit.executeWithArgumentsAsync(
            commandArgs,
            { session ->
                if (continuation.isActive) {
                    val isSuccess = ReturnCode.isSuccess(session.returnCode)
                    try {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(isSuccess))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            { log -> },
            { statistics ->
                if (durationMs > 1f) {
                    val timeProcessedMs = statistics.time
                    var percentage = ((timeProcessedMs.toFloat() / durationMs) * 100f)
                    if (percentage < 0f) percentage = 0f
                    if (percentage > 100f) percentage = 100f

                    if (percentage < maxPercentage) {
                        percentage = maxPercentage
                    } else {
                        maxPercentage = percentage
                    }

                    val elapsedTime = System.currentTimeMillis() - startTime
                    var etaMessage = ""
                    if (percentage > 0) {
                        val estimatedTotalTime = (elapsedTime / (percentage / 100f)).toLong()
                        val remainingTime = estimatedTotalTime - elapsedTime
                        if (remainingTime > 0) {
                            val seconds = (remainingTime / 1000) % 60
                            val minutes = (remainingTime / (1000 * 60)) % 60
                            etaMessage = String.format(java.util.Locale.getDefault(), "الوقت المتبقي: %02d:%02d", minutes, seconds)
                        }
                    }

                    ProcessingManager.updateProgress(percentage / 100f, etaMessage)
                }
            }
        )
        
        // This is important: updating the UI with the session ID so it can be canceled manually
        ProcessingManager.updateSessionId(session.sessionId)

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    /**
     * Removes audio from the source video.
     */
    suspend fun removeAudio(sourceVideo: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        // -c:v copy : Copy video stream as is (no re-encoding, very fast)
        // -an : No audio
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-c:v", "copy", "-an", outputPath)
        return@withContext executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Replaces the audio track of the video with a new audio file.
     */
    suspend fun replaceAudio(sourceVideo: String, newAudio: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        // -map 0:v:0 : Take video from first input
        // -map 1:a:0 : Take audio from second input
        // -c:v copy : Copy video stream
        // -c:a aac : Encode audio to AAC
        // Removed -shortest to avoid clipping video if audio is shorter
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-i", newAudio, "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", outputPath)
        return@withContext executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Mixes a new audio file with the original video audio.
     */
    suspend fun mixAudio(sourceVideo: String, newAudio: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-i", newAudio, "-filter_complex", "[0:a][1:a]amix=inputs=2:duration=longest[a]", "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", outputPath)
        return@withContext executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Adds an image overlay (like our Arabic text PNG) to the video at a specific time range.
     * @param startTimeInSeconds e.g., 5 for 00:05
     * @param endTimeInSeconds e.g., 10 for 00:10
     */
    suspend fun addTextOverlay(
        sourceVideo: String,
        overlayPngPath: String,
        startTimeInSeconds: Int,
        endTimeInSeconds: Int,
        outputPath: String,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        
        // 1. Get Video Duration
        val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(sourceVideo)
        val durationString = mediaInfo.mediaInformation?.duration
        val durationMs = (durationString?.toFloatOrNull() ?: 1f) * 1000f

        // 2. Build Command
        // 2. Build Command Array to avoid FFmpegKit string parsing issues with quotes and commas
        val filterComplex = "[0:v][1:v]overlay=enable='between(t,$startTimeInSeconds,$endTimeInSeconds)':shortest=1[out]"
        
        val commandArgs = arrayOf(
            "-y",
            "-i", sourceVideo,
            "-loop", "1",
            "-i", overlayPngPath,
            "-filter_complex", filterComplex,
            "-map", "[out]",
            "-map", "0:a?",
            "-c:a", "aac",
            "-c:v", "mpeg4",
            "-q:v", "2",
            "-pix_fmt", "yuv420p",
            outputPath
        )
        
        // 3. Execute Async to get statistics
        var resultLog = "SUCCESS"
        val latch = java.util.concurrent.CountDownLatch(1)
        
        FFmpegKit.executeWithArgumentsAsync(
            commandArgs,
            { session -> // Complete Callback
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    resultLog = session.allLogsAsString ?: "Unknown FFmpeg Error"
                }
                latch.countDown()
            },
            { log -> }, // Log Callback
            { statistics -> // Statistics Callback
                val timeProcessedMs = statistics.time
                var percentage = ((timeProcessedMs.toFloat() / durationMs) * 100).toInt()
                if (percentage < 0) percentage = 0
                if (percentage > 100) percentage = 100
                onProgress(percentage)
            }
        )
        
        latch.await()
        return@withContext resultLog
    }

    /**
     * Trims a video without re-encoding.
     */
    suspend fun trimVideo(
        sourceVideo: String,
        startTimeInSeconds: String, // format "00:00:00" or seconds "10"
        durationInSeconds: String,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-ss", startTimeInSeconds, "-i", sourceVideo, "-t", durationInSeconds, "-c", "copy", outputPath)
        return@withContext executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Gets video dimensions to create a matching overlay, automatically handling rotation metadata 
     * and ensuring dimensions are even numbers (required by libx264).
     */
    fun getVideoDimensions(sourceVideo: String): Pair<Int, Int> {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(sourceVideo)
            val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            
            var width = widthStr?.toIntOrNull() ?: 1280
            var height = heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0
            
            // Swap width and height if the video is rotated 90 or 270 degrees (Portrait)
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }
            
            // libx264 requires even dimensions
            if (width % 2 != 0) width += 1
            if (height % 2 != 0) height += 1
            
            return Pair(width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(1280, 720)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    /**
     * Gets video duration in seconds.
     */
    fun getVideoDuration(sourceVideo: String): Float {
        val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(sourceVideo)
        val durationString = mediaInfo.mediaInformation?.duration
        return durationString?.toFloatOrNull() ?: 0f
    }

    /**
     * Boosts volume of the video using volume filter.
     * @param multiplier e.g., 2.0 for 200% volume
     */
    suspend fun boostVolume(sourceVideo: String, multiplier: Float, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-filter:a", "volume=$multiplier", "-c:v", "copy", "-c:a", "aac", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Extracts audio from video.
     */
    suspend fun extractAudio(sourceVideo: String, outputPath: String, format: String = "m4a"): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = mutableListOf("-y", "-i", sourceVideo, "-vn")
        
        when (format.lowercase()) {
            "mp3" -> commandArgs.addAll(arrayOf("-c:a", "libmp3lame", "-q:a", "0"))
            "wav" -> commandArgs.addAll(arrayOf("-c:a", "pcm_s16le"))
            "aac" -> commandArgs.addAll(arrayOf("-c:a", "aac", "-b:a", "256k"))
            "m4a" -> commandArgs.addAll(arrayOf("-c:a", "aac", "-b:a", "256k"))
            else -> commandArgs.addAll(arrayOf("-c:a", "copy"))
        }
        commandArgs.add(outputPath)
        
        executeWithProgress(commandArgs.toTypedArray(), sourceVideo)
    }

    /**
     * Compresses the video by reducing bitrate and re-encoding.
     */
    suspend fun compressVideo(sourceVideo: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-c:v", "mpeg4", "-b:v", "1M", "-c:a", "aac", "-b:a", "128k", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Changes video resolution/quality. 
     */
    suspend fun changeQuality(sourceVideo: String, outputPath: String, width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-vf", "scale=$width:$height", "-c:v", "mpeg4", "-c:a", "copy", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Extracts audio to WAV specifically for Speech-To-Text (16kHz, mono, PCM s16le).
     */
    suspend fun extractAudioToWav(sourceVideo: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf(
            "-y",
            "-i", sourceVideo,
            "-vn",
            "-acodec", "pcm_s16le",
            "-ar", "16000",
            "-ac", "1",
            outputPath
        )
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Merges multiple videos.
     * Requires writing a txt file with 'file path' lines for the concat demuxer.
     */
    suspend fun mergeVideos(concatListFile: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-f", "concat", "-safe", "0", "-i", concatListFile, "-c:v", "mpeg4", "-q:v", "2", "-c:a", "aac", outputPath)
        executeWithProgress(commandArgs) // no sourceVideo for ETA
    }

    /**
     * Reverses the video and audio of a media file.
     * Note: This is memory intensive for long videos.
     */
    suspend fun reverseMedia(sourceMedia: String, outputPath: String, isAudioOnly: Boolean): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = if (isAudioOnly) {
            arrayOf("-y", "-i", sourceMedia, "-af", "areverse", outputPath)
        } else {
            arrayOf("-y", "-i", sourceMedia, "-vf", "reverse", "-af", "areverse", "-c:v", "mpeg4", "-q:v", "2", "-c:a", "aac", outputPath)
        }
        executeWithProgress(commandArgs, sourceMedia)
    }

    /**
     * Applies a fade-in and fade-out effect to both video and audio.
     */
    suspend fun applyFadeEffect(sourceVideo: String, outputPath: String, fadeDurationSec: Int = 2): Boolean = withContext(Dispatchers.IO) {
        val duration = getVideoDuration(sourceVideo)
        val fadeOutStart = if (duration > fadeDurationSec * 2) duration - fadeDurationSec else duration / 2
        
        val vFilter = "fade=t=in:st=0:d=$fadeDurationSec,fade=t=out:st=$fadeOutStart:d=$fadeDurationSec"
        val aFilter = "afade=t=in:st=0:d=$fadeDurationSec,afade=t=out:st=$fadeOutStart:d=$fadeDurationSec"
        
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-vf", vFilter, "-af", aFilter, "-c:v", "mpeg4", "-q:v", "2", "-c:a", "aac", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Creates a slideshow video from a list of images and an audio file.
     */
    suspend fun createSlideshow(images: List<String>, audioFile: String?, durationPerImage: Int, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext false
        
        val concatFile = java.io.File(images[0]).parentFile?.absolutePath + "/slideshow_concat.txt"
        with(java.io.File(concatFile)) {
            writeText(images.joinToString("\n") { "file '$it'\nduration $durationPerImage" })
        }
        
        val commandArgs = if (audioFile != null) {
            arrayOf(
                "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile,
                "-i", audioFile,
                "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
                "-c:v", "mpeg4",
                "-q:v", "2",
                "-c:a", "aac",
                "-shortest",
                outputPath
            )
        } else {
            arrayOf(
                "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile,
                "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
                "-c:v", "mpeg4",
                "-q:v", "2",
                outputPath
            )
        }
        val result = executeWithProgress(commandArgs)
        java.io.File(concatFile).delete()
        result
    }
    /**
     * Fast lossless conversion using stream copy.
     */
    suspend fun fastConvert(sourceVideo: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-c", "copy", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Adds a watermark logo to the video.
     */
    suspend fun addWatermark(sourceVideo: String, logoPath: String, position: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val filter = when (position) {
            "top_left" -> "overlay=10:10"
            "top_right" -> "overlay=main_w-overlay_w-10:10"
            "bottom_left" -> "overlay=10:main_h-overlay_h-10"
            "bottom_right" -> "overlay=main_w-overlay_w-10:main_h-overlay_h-10"
            "center" -> "overlay=(main_w-overlay_w)/2:(main_h-overlay_h)/2"
            else -> "overlay=10:10"
        }
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-i", logoPath, "-filter_complex", filter, "-c:v", "mpeg4", "-q:v", "2", "-c:a", "copy", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    /**
     * Adds a moving ticker text.
     */
    suspend fun addTickerText(sourceVideo: String, text: String, speed: Int, color: String, fontSize: Int, fontFile: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val escapedText = text.replace("'", "\\'").replace(":", "\\:")
        val drawtext = "drawtext=fontfile='$fontFile':text='$escapedText':y=h-line_h-20:x=w-(t*$speed):fontcolor=$color:fontsize=$fontSize:shadowcolor=black:shadowx=2:shadowy=2"
        val commandArgs = arrayOf("-y", "-i", sourceVideo, "-vf", drawtext, "-c:v", "mpeg4", "-q:v", "2", "-c:a", "copy", outputPath)
        executeWithProgress(commandArgs, sourceVideo)
    }

    suspend fun drawTextOnImage(context: android.content.Context, sourceImage: String, options: com.example.accessiblevideoeditor.media.TextRenderer.TextOptions, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val fontPath = com.example.accessiblevideoeditor.utils.FileUtils.copyFontToCache(context)
        val escapedText = options.text.replace("'", "\\'").replace(":", "\\:")
        
        // Convert colors from Int to hex string for ffmpeg
        val fontColorHex = String.format("#%06X", 0xFFFFFF and options.textColor)
        
        // Map position
        val positionStr = when (options.position) {
            com.example.accessiblevideoeditor.media.TextRenderer.TextPosition.TOP -> "x=(w-text_w)/2:y=50"
            com.example.accessiblevideoeditor.media.TextRenderer.TextPosition.CENTER -> "x=(w-text_w)/2:y=(h-text_h)/2"
            com.example.accessiblevideoeditor.media.TextRenderer.TextPosition.BOTTOM -> "x=(w-text_w)/2:y=h-text_h-50"
        }

        val drawtext = "drawtext=fontfile='$fontPath':text='$escapedText':$positionStr:fontcolor=$fontColorHex:fontsize=${options.textSizeSp.toInt()}:shadowcolor=black:shadowx=2:shadowy=2"
        val commandArgs = arrayOf("-y", "-i", sourceImage, "-vf", drawtext, outputPath)
        executeWithProgress(commandArgs, sourceImage)
    }

    suspend fun applyAudioStudioEffects(sourceAudio: String, preset: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val filter = when (preset) {
            "bass_boost" -> "bass=g=15"
            "vocal_enhancer" -> "highpass=f=200,lowpass=f=3000"
            "echo" -> "aecho=0.8:0.9:1000:0.3"
            "chorus" -> "chorus=0.5:0.9:50|60|40:0.4|0.32|0.3:0.25|0.4|0.3:2|2.3|1.3"
            else -> "anull"
        }
        val commandArgs = arrayOf("-y", "-i", sourceAudio, "-af", filter, "-c:a", "aac", "-b:a", "192k", outputPath)
        executeWithProgress(commandArgs, sourceAudio)
    }

    /**
     * Smart Cut: removes specified segments from the video.
     */
    suspend fun smartCut(sourceVideo: String, segmentsToRemove: List<Pair<Int, Int>>, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        if (segmentsToRemove.isEmpty()) return@withContext false
        
        val duration = getVideoDuration(sourceVideo)
        val sortedCuts = segmentsToRemove.sortedBy { it.first }
        
        val keepSegments = mutableListOf<Pair<Int, Int>>()
        var currentStart = 0
        for (cut in sortedCuts) {
            if (cut.first > currentStart) {
                keepSegments.add(Pair(currentStart, cut.first))
            }
            currentStart = Math.max(currentStart, cut.second)
        }
        if (currentStart < duration.toInt()) {
            keepSegments.add(Pair(currentStart, duration.toInt()))
        }
        
        if (keepSegments.isEmpty()) return@withContext false
        
        val filterComplex = java.lang.StringBuilder()
        val concatStr = java.lang.StringBuilder()
        
        for (i in keepSegments.indices) {
            val start = keepSegments[i].first
            val end = keepSegments[i].second
            filterComplex.append("[0:v]trim=start=$start:end=$end,setpts=PTS-STARTPTS[v$i];")
            filterComplex.append("[0:a]atrim=start=$start:end=$end,asetpts=PTS-STARTPTS[a$i];")
            concatStr.append("[v$i][a$i]")
        }
        filterComplex.append(concatStr).append("concat=n=${keepSegments.size}:v=1:a=1[outv][outa]")
        
        val commandArgs = arrayOf(
            "-y", "-i", sourceVideo, 
            "-filter_complex", filterComplex.toString(),
            "-map", "[outv]", "-map", "[outa]",
            "-c:v", "mpeg4", "-q:v", "2", "-c:a", "aac",
            outputPath
        )
        
        executeWithProgress(commandArgs, sourceVideo)
    }
}
