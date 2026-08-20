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

        // B. Background Removal / Chroma Key (if enabled)
        if (project.backgroundRemovalEnabled) {
            val keyColor = when (project.backgroundRemovalType) {
                "green_screen" -> "0x00FF00"
                "blue_screen" -> "0x0000FF"
                else -> "0x00FF00" // auto/green default
            }
            val nextStream = "[vchroma]"
            filterChains.add("$lastStreamName chromakey=$keyColor:0.15:0.2$nextStream")
            lastStreamName = nextStream
        }

        // C. Color Filters & Adjustments (Brightness, Contrast, Saturation, Presets)
        val eqFilters = mutableListOf<String>()
        if (project.brightness != 0.0f) eqFilters.add("brightness=${project.brightness}")
        if (project.contrast != 1.0f) eqFilters.add("contrast=${project.contrast}")
        if (project.saturation != 1.0f) eqFilters.add("saturation=${project.saturation}")

        // Preset LUT filters
        when (project.colorFilterPreset) {
            "warm_cinematic" -> eqFilters.add("gamma_r=1.2:gamma_g=1.0:gamma_b=0.8")
            "cool_noir" -> {
                eqFilters.add("saturation=0.2")
                eqFilters.add("contrast=1.3")
            }
            "vivid_hdr" -> {
                eqFilters.add("contrast=1.2")
                eqFilters.add("saturation=1.4")
            }
            "vintage_sepia" -> eqFilters.add("colorbalance=rs=.3:gs=.1:bs=-.2")
        }

        if (eqFilters.isNotEmpty()) {
            val nextStream = "[vcol]"
            val filterString = eqFilters.joinToString(":")
            filterChains.add("$lastStreamName eq=$filterString$nextStream")
            lastStreamName = nextStream
        }

        // D. Motion Keyframing & Reframe Presets (zoompan)
        when (project.keyframePreset) {
            "zoom_in_center" -> {
                val nextStream = "[vzoom]"
                filterChains.add("$lastStreamName zoompan=z='min(zoom+0.0015,1.3)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=125:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
            "pan_left_to_right" -> {
                val nextStream = "[vpan]"
                filterChains.add("$lastStreamName zoompan=z='1.2':x='if(lte(on,-1),10,x+1)':y='ih/2-(ih/zoom/2)':d=125:s=${width}x${height}$nextStream")
                lastStreamName = nextStream
            }
        }

        // E. Geometric Masking Presets
        when (project.maskPreset) {
            "top_bottom_cinematic" -> {
                val nextStream = "[vmask]"
                val cropH = (height * 0.8f).toInt()
                filterChains.add("$lastStreamName crop=w=$width:h=$cropH:x=0:y=(in_h-$cropH)/2,pad=w=$width:h=$height:x=0:y=(oh-ih)/2:color=black$nextStream")
                lastStreamName = nextStream
            }
            "circle_center" -> {
                val nextStream = "[vmask]"
                filterChains.add("$lastStreamName vignette=angle=0.5$nextStream")
                lastStreamName = nextStream
            }
        }

        // F. Speed Adjustment to Video if enabled
        if (project.speedMultiplier != 1.0f) {
            val nextStream = "[vspeed]"
            filterChains.add("$lastStreamName setpts=PTS/${project.speedMultiplier}$nextStream")
            lastStreamName = nextStream
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
            
            val finalStartSec = maxOf(0.0, startSec)
            val finalEndSec = maxOf(0.1, endSec)

            val nextStream = "[v$streamCounter]"
            
            var currentOverlayInput = "[$inputIdx:v]"
            val overlayFilter = when (overlay.animationType) {
                "fade_in" -> "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "zoom_in" -> {
                    val prepStream = "[zoomv$i]"
                    filterChains.add("[$inputIdx:v]scale=eval=frame:w='iw*min(1,max(0,(t-$finalStartSec)/0.4))':h='ih*min(1,max(0,(t-$finalStartSec)/0.4))'$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=x='(W-w)/2':y='(H-h)/2':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "typewriter" -> {
                    val prepStream = "[typev$i]"
                    filterChains.add("[$inputIdx:v]crop=w='iw*min(1,max(0,(t-$finalStartSec)/1.5))':h=ih:x=0:y=0,pad=w=iw:h=ih:x=0:y=0:color=black@0$prepStream")
                    currentOverlayInput = prepStream
                    "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                }
                "slide_up" -> "overlay=y='if(lt(t,$finalStartSec+0.4), H-(t-$finalStartSec)*H/0.4, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "slide_down" -> "overlay=y='if(lt(t,$finalStartSec+0.4), (t-$finalStartSec)*H/0.4 - H, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "slide_left" -> "overlay=x='if(lt(t,$finalStartSec+0.4), W-(t-$finalStartSec)*W/0.4, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "bounce_in" -> "overlay=y='if(lt(t,$finalStartSec+0.5), (H - (t-$finalStartSec)*H/0.5 + sin((t-$finalStartSec)*15)*30), 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                "mask_reveal" -> "overlay=y='if(lt(t,$finalStartSec+0.4), H*0.3-(t-$finalStartSec)*H*0.3/0.4, 0)':enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
                else -> "overlay=enable='between(t,$finalStartSec,$finalEndSec)':shortest=1"
            }
            
            filterChains.add("$lastStreamName$currentOverlayInput$overlayFilter$nextStream")
            lastStreamName = nextStream
            streamCounter++
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
            args.add("-map")
            args.add("0:v")
            args.add("-c:v")
            args.add("copy") // Lossless stream copy if no video filters exist!
        }

        // Always map audio stream explicitly to avoid stream selection failures when video mapping is present
        args.add("-map")
        args.add("0:a?")

        // Speed / Volume Adjustment to Audio if enabled
        val audioFilters = mutableListOf<String>()
        if (project.speedMultiplier != 1.0f) {
            audioFilters.add("atempo=${project.speedMultiplier}")
        }
        if (project.volumeLevel != 1.0f) {
            audioFilters.add("volume=${project.volumeLevel}")
        }

        if (audioFilters.isNotEmpty()) {
            args.add("-filter:a")
            args.add(audioFilters.joinToString(","))
            args.add("-c:a")
            args.add("aac")
        } else {
            args.add("-c:a")
            args.add("copy") // Direct lossless audio stream copy
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
