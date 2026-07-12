package com.example.accessiblevideoeditor.media

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.LogCallback
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmartCutProcessor {

    suspend fun removeSilence(context: Context, inputPath: String, outputPath: String, thresholdDb: Int = -30, durationSec: Float = 0.5f): Boolean = withContext(Dispatchers.IO) {
        try {
            // Pass 1: Detect silence
            val detectCommand = "-i \"$inputPath\" -af silencedetect=noise=${thresholdDb}dB:d=$durationSec -f null -"
            val detectSession = FFmpegKit.execute(detectCommand)
            val logs = detectSession.allLogsAsString
            
            val silenceStarts = mutableListOf<Double>()
            val silenceEnds = mutableListOf<Double>()
            
            val startRegex = Regex("silence_start: (\\d+\\.?\\d*)")
            val endRegex = Regex("silence_end: (\\d+\\.?\\d*)")
            
            startRegex.findAll(logs).forEach { silenceStarts.add(it.groupValues[1].toDouble()) }
            endRegex.findAll(logs).forEach { silenceEnds.add(it.groupValues[1].toDouble()) }
            
            if (silenceStarts.isEmpty() || silenceEnds.isEmpty()) {
                // No silence found, just copy
                val copyCmd = arrayOf("-y", "-i", inputPath, "-c", "copy", outputPath)
                val session = FFmpegKit.executeWithArguments(copyCmd)
                return@withContext session.returnCode.isValueSuccess
            }
            
            // Generate non-silent segments with padding
            val keepSegments = mutableListOf<Pair<Double, Double>>()
            var lastKeepStart = 0.0
            
            val pad = 0.125 // 0.125s on each side = 0.25s total silence left
            
            for (i in 0 until min(silenceStarts.size, silenceEnds.size)) {
                val sStart = silenceStarts[i] + pad
                val sEnd = silenceEnds[i] - pad
                
                if (sStart >= sEnd) {
                    continue // silence is too small to cut after padding
                }
                
                if (sStart > lastKeepStart) {
                    keepSegments.add(Pair(lastKeepStart, sStart))
                }
                lastKeepStart = sEnd
            }
            
            // Assume video ends at 10 hours if last silence isn't at the very end
            keepSegments.add(Pair(lastKeepStart, 36000.0))
            
            // Pass 2: Build filter_complex
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
                "-c:v", "libx264", "-preset", "fast", "-pix_fmt", "yuv420p", "-c:a", "aac",
                outputPath
            )
            
            val cutSession = FFmpegKit.executeWithArguments(commandArgs)
            
            return@withContext cutSession.returnCode.isValueSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
