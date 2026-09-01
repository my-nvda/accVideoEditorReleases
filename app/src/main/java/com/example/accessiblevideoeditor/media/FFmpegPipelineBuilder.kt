package com.example.accessiblevideoeditor.media

import android.content.Context
import android.net.Uri
import com.example.accessiblevideoeditor.data.UnifiedProjectModel
import com.example.accessiblevideoeditor.data.TextOverlayConfig
import java.io.File

object FFmpegPipelineBuilder {

    suspend fun renderProject(
        context: Context,
        project: UnifiedProjectModel,
        outputPath: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean {
        var tempVideoFile: File? = null
        var tempWatermarkFile: File? = null
        var tempAutoSubjectFile: File? = null
        val textOverlayInputs = mutableListOf<Pair<TextOverlayConfig, String>>()

        try {
            // 1. Resolve source video URI/path to a local file path
            val rawVideoPath = project.videoPath
            var sourceVideoPath = if (rawVideoPath.startsWith("content://") || rawVideoPath.startsWith("file://")) {
                tempVideoFile = MediaUtils.copyUriToTempFile(context, Uri.parse(rawVideoPath), "project_src_${System.currentTimeMillis()}.mp4")
                tempVideoFile?.absolutePath ?: rawVideoPath
            } else {
                val f = File(rawVideoPath)
                if (!f.exists()) {
                    tempVideoFile = MediaUtils.copyUriToTempFile(context, Uri.parse(rawVideoPath), "project_src_${System.currentTimeMillis()}.mp4")
                    tempVideoFile?.absolutePath ?: rawVideoPath
                } else {
                    rawVideoPath
                }
            }

            // Preprocess with SelfieBackgroundRemover if background removal is enabled
            if (project.backgroundRemovalEnabled) {
                val tempDir = File(context.cacheDir, "bg_remove_${System.currentTimeMillis()}")
                val result = SelfieBackgroundRemover.removeBackground(
                    context,
                    sourceVideoPath,
                    tempDir,
                    project.backgroundRemovalType,
                    project.backgroundRemovalCustomBgPath,
                    project.backgroundRemovalFpsOption
                )
                if (result != null && File(result).exists()) {
                    tempAutoSubjectFile = File(result)
                    sourceVideoPath = result
                }
            }

            if (sourceVideoPath.isBlank() || !File(sourceVideoPath).exists()) {
                return false
            }

            // 2. Get Video Dimensions & Duration
            val (width, height) = FFmpegProcessor.getVideoDimensions(sourceVideoPath)
            val durationMs = FFmpegProcessor.getMediaDurationMs(sourceVideoPath)

            // 3. Build Inputs List
            val inputs = mutableListOf<String>()

            // Input 0: Video (with seeking if trim is enabled and valid)
            if (project.trimEnabled && project.trimEndMs > project.trimStartMs && project.trimEndMs > 0) {
                val startSec = project.trimStartMs / 1000.0
                val endSec = project.trimEndMs / 1000.0
                inputs.add("-ss")
                inputs.add(startSec.toString())
                inputs.add("-to")
                inputs.add(endSec.toString())
            }
            inputs.add("-i")
            inputs.add(sourceVideoPath)

            var currentInputIndex = 1
            var watermarkInputIndex = -1

            // Watermark Input
            if (project.watermarkEnabled && project.watermarkImagePath.isNotEmpty()) {
                val rawWmPath = project.watermarkImagePath
                val wmPath = if (rawWmPath.startsWith("content://") || rawWmPath.startsWith("file://")) {
                    tempWatermarkFile = MediaUtils.copyUriToTempFile(context, Uri.parse(rawWmPath), "project_wm_${System.currentTimeMillis()}.png")
                    tempWatermarkFile?.absolutePath ?: rawWmPath
                } else {
                    rawWmPath
                }
                
                if (File(wmPath).exists()) {
                    inputs.add("-i")
                    inputs.add(wmPath)
                    watermarkInputIndex = currentInputIndex
                    currentInputIndex++
                }
            }

        // Text Overlays Inputs
        if (project.textOverlays.isNotEmpty()) {
            for (overlay in project.textOverlays) {
                val overlayFile = File(context.cacheDir, "project_text_${overlay.id}.png")
                val options = TextRenderer.TextOptions(
                    text = overlay.text,
                    textColor = try { android.graphics.Color.parseColor(overlay.colorHex) } catch (_: Exception) { android.graphics.Color.WHITE },
                    bgColor = if (overlay.hasBackdrop) android.graphics.Color.parseColor("#B3000000") else android.graphics.Color.TRANSPARENT,
                    textSizeSp = overlay.fontSize.toFloat(),
                    position = when {
                        overlay.yPosPercent < 0.33f -> TextRenderer.TextPosition.TOP
                        overlay.yPosPercent > 0.66f -> TextRenderer.TextPosition.BOTTOM
                        else -> TextRenderer.TextPosition.CENTER
                    }
                )
                val created = TextRenderer.createOverlayPng(width, height, options, overlayFile)
                if (created && overlayFile.exists()) {
                    inputs.add("-loop")
                    inputs.add("1")
                    inputs.add("-i")
                    inputs.add(overlayFile.absolutePath)

                    textOverlayInputs.add(overlay to overlayFile.absolutePath)
                    currentInputIndex++
                }
            }
        }

        // 4. Build Filter Complex Pipeline
        val filterChains = mutableListOf<String>()
        var lastStreamName = "[0:v]"
        var streamCounter = 1

        // A. Video Stabilization (if enabled)
        if (project.stabilizationEnabled) {
            val nextStream = "[vstab]"
            filterChains.add("$lastStreamName deshake$nextStream")
            lastStreamName = nextStream
        }

        // B. Background Removal (handled upstream in preprocessing via SelfieBackgroundRemover)

        // C. Color Filters & Adjustments (Brightness, Contrast, Saturation, Presets)
        val eqParams = mutableMapOf<String, String>()
        var colorbalanceFilter: String? = null

        if (project.brightness != 0.0f) eqParams["brightness"] = project.brightness.toString()
        if (project.contrast != 1.0f) eqParams["contrast"] = project.contrast.toString()
        if (project.saturation != 1.0f) eqParams["saturation"] = project.saturation.toString()

        // Preset LUT filters
        when (project.colorFilterPreset) {
            "warm_cinematic" -> {
                eqParams["gamma_r"] = "1.2"
                eqParams["gamma_g"] = "1.0"
                eqParams["gamma_b"] = "0.8"
            }
            "cool_noir" -> {
                eqParams["saturation"] = "0.2"
                eqParams["contrast"] = "1.3"
            }
            "vivid_hdr" -> {
                eqParams["contrast"] = "1.2"
                eqParams["saturation"] = "1.4"
            }
            "vintage_sepia" -> {
                colorbalanceFilter = "colorbalance=rs=.3:gs=.1:bs=-.2"
            }
        }

        if (eqParams.isNotEmpty()) {
            val nextStream = "[vcol]"
            val filterString = eqParams.map { "${it.key}=${it.value}" }.joinToString(":")
            filterChains.add("$lastStreamName eq=$filterString$nextStream")
            lastStreamName = nextStream
        }

        if (colorbalanceFilter != null) {
            val nextStream = "[vcb]"
            filterChains.add("$lastStreamName $colorbalanceFilter$nextStream")
            lastStreamName = nextStream
        }

        // D. Motion Keyframing & Reframe Presets (zoompan)
        // Set d=1 to prevent 125x rendering delay on video input, and clamp x/y coordinate bounds
        when (project.keyframePreset) {
            "zoom_in_center" -> {
                val nextStream = "[vzoom]"
                filterChains.add("$lastStreamName zoompan=z='min(zoom+0.0015,1.3)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
            "pan_left_to_right" -> {
                val nextStream = "[vpan]"
                filterChains.add("$lastStreamName zoompan=z='1.2':x='min(iw-iw/zoom,on*1)':y='ih/2-(ih/zoom/2)':d=1:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
            "pan_right_to_left" -> {
                val nextStream = "[vpanrtl]"
                filterChains.add("$lastStreamName zoompan=z='1.2':x='max(0,(iw-iw/zoom)-on*1)':y='ih/2-(ih/zoom/2)':d=1:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
            "bounce_in" -> {
                val nextStream = "[vbounce]"
                filterChains.add("$lastStreamName zoompan=z='min(1.2,1.0+abs(sin(on*0.1))*0.1)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
        }

        // E. Geometric Masking Presets
        // Guard even dimensions to prevent yuv420p odd-dimension failure
        when (project.maskPreset) {
            "top_bottom_cinematic" -> {
                val nextStream = "[vmask]"
                val cropH = ((height * 0.8f).toInt() / 2) * 2
                val cropY = ((height - cropH) / 2 / 2) * 2
                filterChains.add("$lastStreamName crop=w=$width:h=$cropH:x=0:y=$cropY,pad=w=$width:h=$height:x=0:y=(oh-ih)/2:color=black$nextStream")
                lastStreamName = nextStream
            }
            "circle_center" -> {
                val nextStream = "[vmask]"
                filterChains.add("$lastStreamName vignette=angle=0.5$nextStream")
                lastStreamName = nextStream
            }
            "split_50_50" -> {
                val nextStream = "[vmask]"
                val cropW = (width / 2 / 2) * 2
                filterChains.add("$lastStreamName crop=w=$cropW:h=ih:x=0:y=0$nextStream")
                lastStreamName = nextStream
            }
        }

        // G. Watermark Overlay
        if (watermarkInputIndex != -1) {
            val prepStream = "[wm_prep]"
            val scaledWidth = (width * project.watermarkScale).toInt()
            filterChains.add("[$watermarkInputIndex:v]scale=$scaledWidth:-1,format=rgba,colorchannelmixer=aa=${project.watermarkOpacity}$prepStream")
            
            val overlayCoords = when (project.watermarkPosition) {
                "top_left" -> "x=10:y=10"
                "bottom_left" -> "x=10:y=H-h-10"
                "bottom_right" -> "x=W-w-10:y=H-h-10"
                "center" -> "x=(W-w)/2:y=(H-h)/2"
                else -> "x=W-w-10:y=10" // default top_right
            }
            val nextStream = "[v$streamCounter]"
            filterChains.add("$lastStreamName$prepStream overlay=$overlayCoords$nextStream")
            lastStreamName = nextStream
            streamCounter++
        }

        // H. Text Overlays with Animations
        val timeOffsetSec = if (project.trimEnabled) project.trimStartMs / 1000.0 else 0.0
        for (i in textOverlayInputs.indices) {
            val (overlay, _) = textOverlayInputs[i]
            val inputIdx = if (watermarkInputIndex != -1) watermarkInputIndex + 1 + i else 1 + i
            
            val startSec = (overlay.startTimeMs / 1000.0) - timeOffsetSec
            val endSec = (overlay.endTimeMs / 1000.0) - timeOffsetSec
            
            // Skip subtitle if it ends before the trim window
            if (endSec <= 0.0) continue

            val finalStartSec = maxOf(0.0, startSec)
            val finalEndSec = maxOf(0.1, endSec)

            val nextStream = "[v$streamCounter]"
            
            var currentOverlayInput = "[$inputIdx:v]"
            val overlayFilter = when (overlay.animationType) {
                "fade_in" -> {
                    val prepStream = "[fadein$i]"
                    filterChains.add("[$inputIdx:v]fade=t=in:start_time=$finalStartSec:duration=0.5:alpha=1$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "fade_out" -> {
                    val prepStream = "[fadeout$i]"
                    val fadeOutStart = maxOf(finalStartSec, finalEndSec - 0.5)
                    val fadeOutDur = maxOf(0.01, finalEndSec - fadeOutStart)
                    filterChains.add("[$inputIdx:v]fade=t=out:start_time=$fadeOutStart:duration=$fadeOutDur:alpha=1$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "fade_in_out" -> {
                    val prepStream = "[fadeinout$i]"
                    val fadeOutStart = maxOf(finalStartSec, finalEndSec - 0.5)
                    val fadeOutDur = maxOf(0.01, finalEndSec - fadeOutStart)
                    filterChains.add("[$inputIdx:v]fade=t=in:start_time=$finalStartSec:duration=0.5:alpha=1,fade=t=out:start_time=$fadeOutStart:duration=$fadeOutDur:alpha=1$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "zoom_in" -> {
                    val prepStream = "[zoomin$i]"
                    // Prevent 0x0 scale crashes by using max(0.001, ...)
                    filterChains.add("[$inputIdx:v]scale=eval=frame:w='iw*min(1,max(0.001,(t-$finalStartSec)/0.4))':h='ih*min(1,max(0.001,(t-$finalStartSec)/0.4))'$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=x='(W-w)/2':y='(H-h)/2':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "elastic_zoom" -> {
                    val prepStream = "[elastic$i]"
                    filterChains.add("[$inputIdx:v]scale=eval=frame:w='iw*min(1,max(0.001,(t-$finalStartSec)/0.5 + sin((t-$finalStartSec)*12)*0.15))':h='ih*min(1,max(0.001,(t-$finalStartSec)/0.5 + sin((t-$finalStartSec)*12)*0.15))'$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=x='(W-w)/2':y='(H-h)/2':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "pulse" -> {
                    val prepStream = "[pulse$i]"
                    filterChains.add("[$inputIdx:v]scale=eval=frame:w='iw*(1+0.05*sin((t-$finalStartSec)*8))':h='ih*(1+0.05*sin((t-$finalStartSec)*8))'$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=x='(W-w)/2':y='(H-h)/2':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "typewriter" -> {
                    val prepStream = "[type$i]"
                    // Progressive reveal crop from left to right over 1.5 seconds
                    filterChains.add("[$inputIdx:v]crop=eval=frame:w='min(iw,max(1,iw*(t-$finalStartSec)/1.5))':h=ih:x=0:y=0$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "slide_up" -> "overlay=y='if(lt(t,$finalStartSec+0.4), H-(t-$finalStartSec)*H/0.4, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "slide_down" -> "overlay=y='if(lt(t,$finalStartSec+0.4), (t-$finalStartSec)*H/0.4 - H, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "slide_left" -> "overlay=x='if(lt(t,$finalStartSec+0.4), W-(t-$finalStartSec)*W/0.4, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "slide_right" -> "overlay=x='if(lt(t,$finalStartSec+0.4), (t-$finalStartSec)*W/0.4 - W, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "bounce_in" -> {
                    // Decay the bounce sin term to 0 at 0.5 seconds to prevent visual snaps/jumping
                    "overlay=y='if(lt(t,$finalStartSec+0.5), (H - (t-$finalStartSec)*H/0.5 + sin((t-$finalStartSec)*15)*30*(1.0-(t-$finalStartSec)/0.5)), 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "mask_reveal" -> {
                    val prepStream = "[mask$i]"
                    // Progressive height reveal crop from bottom to top over 0.4 seconds
                    filterChains.add("[$inputIdx:v]crop=eval=frame:w=iw:h='min(ih,max(1,ih*(t-$finalStartSec)/0.4))':x=0:y=0$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "blink" -> "overlay=enable='between(t,$finalStartSec,$finalEndSec)*lt(mod(t-$finalStartSec,0.6),0.3)':shortest=1"
                "flicker" -> {
                    // Use trunc() instead of non-existent int() to prevent eval parser errors
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)*if(lt(t,$finalStartSec+0.4),mod(trunc((t-$finalStartSec)*20),2),1)':shortest=1"
                }
                "wave" -> "overlay=y='sin((t-$finalStartSec)*6)*15':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "rotate_in" -> {
                    val prepStream = "[rotate$i]"
                    // Rotate smoothly from 360 to 0 degrees, keeping backgrounds transparent with fillcolor=none
                    filterChains.add("[$inputIdx:v]rotate=angle='if(lt(t,$finalStartSec+0.5),(0.5-(t-$finalStartSec))*2*3.14159/0.5,0)':ow=iw:oh=ih:fillcolor=none$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                else -> "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
            }
            
            filterChains.add("$lastStreamName$currentOverlayInput$overlayFilter$nextStream")
            lastStreamName = nextStream
            streamCounter++
        }

        // Speed Adjustment to Video if enabled - applied AFTER text overlays to keep timings sync'd!
        if (project.speedMultiplier != 1.0f) {
            val nextStream = "[vspeed]"
            filterChains.add("$lastStreamName setpts=PTS/${project.speedMultiplier}$nextStream")
            lastStreamName = nextStream
        }

        // Combine filter chains
        val filterComplex = filterChains.joinToString("; ")

        // 5. Assemble Command Arguments
        val args = mutableListOf<String>()
        args.add("-y")
        args.addAll(inputs)

        if (filterComplex.isNotEmpty()) {
            args.add("-filter_complex")
            args.add(filterComplex)
            args.add("-map")
            args.add(lastStreamName)
            args.add("-c:v")
            args.add("libx264")
            args.add("-preset")
            args.add("ultrafast") // Maximum encoding speed with true multi-core threading
            args.add("-crf")
            args.add("18") // Visually lossless quality (lower = higher quality, 18 = near-perfect)
            args.add("-threads")
            args.add("0") // Use ALL CPU cores for parallel frame encoding
        } else {
            // Re-encode video if trimming is enabled to ensure frame-accurate cuts, otherwise stream copy
            if (project.trimEnabled) {
                args.add("-map")
                args.add("0:v")
                args.add("-c:v")
                args.add("libx264")
                args.add("-preset")
                args.add("ultrafast")
                args.add("-crf")
                args.add("18")
                args.add("-threads")
                args.add("0")
            } else {
                args.add("-map")
                args.add("0:v")
                args.add("-c:v")
                args.add("copy") // Lossless stream copy if no video filters exist and not trimming!
            }
        }

        // Map audio stream ONLY if source video actually contains audio
        val hasAudio = FFmpegProcessor.hasAudioTrack(project.videoPath)
        if (hasAudio) {
            args.add("-map")
            args.add("0:a?")

            // Speed / Volume Adjustment to Audio if enabled
            val audioFilters = mutableListOf<String>()
            if (project.speedMultiplier != 1.0f) {
                // Chain multiple atempo filters dynamically to support speed multipliers outside [0.5 - 2.0] range
                var speed = project.speedMultiplier
                while (speed > 2.0f) {
                    audioFilters.add("tempo=2.0")
                    speed /= 2.0f
                }
                while (speed < 0.5f) {
                    audioFilters.add("tempo=0.5")
                    speed /= 0.5f
                }
                if (speed != 1.0f) {
                    audioFilters.add("tempo=$speed")
                }
            }
            if (project.volumeLevel != 1.0f) {
                audioFilters.add("volume=${project.volumeLevel}")
            }

            if (audioFilters.isNotEmpty()) {
                args.add("-filter:a")
                args.add(audioFilters.joinToString(",").replace("tempo=", "atempo="))
                args.add("-c:a")
                args.add("aac")
            } else {
                args.add("-c:a")
                args.add("copy")
            }
        }

        if (filterComplex.isNotEmpty()) {
            args.add("-pix_fmt")
            args.add("yuv420p")
        }
        args.add(outputPath)

        // 6. Execute FFmpeg with progress callback
        val isSuccess = FFmpegProcessor.executeWithProgress(
            commandArgs = args.toTypedArray(),
            sourceVideo = sourceVideoPath,
            totalDurationMs = durationMs,
            onProgress = onProgress
        )

        return isSuccess
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    } finally {
        textOverlayInputs.forEach { (_, path) ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        try { tempVideoFile?.delete() } catch (_: Exception) {}
        try { tempWatermarkFile?.delete() } catch (_: Exception) {}
        try {
            tempAutoSubjectFile?.delete()
            tempAutoSubjectFile?.parentFile?.deleteRecursively()
        } catch (_: Exception) {}
    }
}
}
