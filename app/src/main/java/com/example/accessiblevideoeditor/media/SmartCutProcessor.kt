package com.example.accessiblevideoeditor.media

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmartCutProcessor {

    suspend fun removeSilence(context: Context, inputPath: String, outputPath: String, thresholdDb: Int = -30, durationSec: Float = 0.5f): Boolean = withContext(Dispatchers.IO) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(inputPath)
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                (durStr?.toDoubleOrNull() ?: 0.0) / 1000.0
            } catch (e: Exception) {
                e.printStackTrace()
                0.0
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
            
            if (duration <= 0.0) return@withContext false

            val mediaInfo = FFprobeKit.getMediaInformation(inputPath)
            val info = mediaInfo.mediaInformation
            
            // Check if there is an audio stream
            val streams = info?.streams ?: emptyList()
            val hasAudio = streams.any { it.type == "audio" }
            
            if (!hasAudio) {
                // No audio, just copy
                val copyCmd = arrayOf("-y", "-i", inputPath, "-c", "copy", outputPath)
                return@withContext FFmpegProcessor.executeWithProgress(copyCmd, inputPath)
            }

            // Pass 1: Detect silence (0% to 40% progress)
            val detectCommandArgs = arrayOf("-i", inputPath, "-af", "silencedetect=noise=${thresholdDb}dB:d=$durationSec", "-f", "null", "-")
            val logsBuilder = StringBuilder()
            val pass1Success = FFmpegProcessor.executeWithProgress(
                detectCommandArgs,
                sourceVideo = inputPath,
                progressOffset = 0f,
                progressScale = 0.4f,
                logCollector = { line -> logsBuilder.append(line).append("\n") }
            )

            if (!pass1Success) {
                val logs = logsBuilder.toString()
                val detailedLog = "Command:\n${detectCommandArgs.joinToString(" ")}\n\nLogs:\n$logs"
                withContext(Dispatchers.Main) {
                    com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                }
                return@withContext false
            }
            val logs = logsBuilder.toString()
            
            val silenceSegments = mutableListOf<Pair<Double, Double>>()
            var currentStart: Double? = null
            
            logs.split("\n").forEach { line ->
                if (line.contains("silence_start:")) {
                    val match = Regex("silence_start: ([\\d.eE+-]+)").find(line)
                    match?.let { currentStart = it.groupValues[1].toDoubleOrNull() }
                } else if (line.contains("silence_end:")) {
                    val match = Regex("silence_end: ([\\d.eE+-]+)").find(line)
                    match?.let {
                        val endVal = it.groupValues[1].toDoubleOrNull()
                        if (endVal != null) {
                            val startVal = currentStart ?: 0.0
                            silenceSegments.add(Pair(startVal, endVal))
                            currentStart = null
                        }
                    }
                }
            }
            if (currentStart != null) {
                silenceSegments.add(Pair(currentStart!!, duration))
            }
            
            if (silenceSegments.isEmpty()) {
                // No silence found, just copy
                val copyCmd = arrayOf("-y", "-i", inputPath, "-c", "copy", outputPath)
                return@withContext FFmpegProcessor.executeWithProgress(copyCmd, inputPath, progressOffset = 40f, progressScale = 0.6f)
            }
            
            // Generate non-silent segments with padding
            val keepSegments = mutableListOf<Pair<Double, Double>>()
            var lastKeepStart = 0.0
            
            val pad = 0.125 // 0.125s on each side = 0.25s total silence left
            
            for (seg in silenceSegments) {
                val sStart = seg.first + pad
                val sEnd = seg.second - pad
                
                if (sStart >= sEnd) {
                    continue // silence is too small to cut after padding
                }
                
                if (sStart > lastKeepStart) {
                    keepSegments.add(Pair(lastKeepStart, sStart))
                }
                lastKeepStart = sEnd
            }
            
            // Use actual video duration
            if (lastKeepStart < duration) {
                keepSegments.add(Pair(lastKeepStart, duration))
            }
            
            if (keepSegments.isEmpty()) {
                // If everything is silence but we have to keep something, or it's empty, copy as is
                val copyCmd = arrayOf("-y", "-i", inputPath, "-c", "copy", outputPath)
                return@withContext FFmpegProcessor.executeWithProgress(copyCmd, inputPath, progressOffset = 40f, progressScale = 0.6f)
            }
            
            // Pass 2: Build filter_complex (40% to 100% progress)
            val filterBuilder = StringBuilder()
            var concatStr = ""
            
            keepSegments.forEachIndexed { index, pair ->
                val start = pair.first
                val end = pair.second
                filterBuilder.append("[0:v]trim=start=$start:end=$end,setpts=PTS-STARTPTS[v$index];")
                filterBuilder.append("[0:a]atrim=start=$start:end=$end,asetpts=PTS-STARTPTS[a$index];")
                concatStr += "[v$index][a$index]"
            }
            filterBuilder.append("${concatStr}concat=n=${keepSegments.size}:v=1:a=1[outv][outa]")
            
            val commandArgs = arrayOf(
                "-y", "-i", inputPath, 
                "-filter_complex", filterBuilder.toString(),
                "-map", "[outv]", "-map", "[outa]",
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18", "-c:a", "aac",
                outputPath
            )
            
            return@withContext FFmpegProcessor.executeWithProgress(commandArgs, inputPath, progressOffset = 40f, progressScale = 0.6f)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
