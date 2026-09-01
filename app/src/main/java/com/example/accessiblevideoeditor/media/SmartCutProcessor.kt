package com.example.accessiblevideoeditor.media

import android.content.Context
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SmartCutProcessor {

    data class SilenceReport(
        val totalDuration: Double,
        val silenceSegments: List<Pair<Double, Double>>,
        val keepSegments: List<Pair<Double, Double>>,
        val totalSilenceDuration: Double,
        val timeSaved: Double
    )

    suspend fun detectSilenceReport(
        context: Context,
        inputPath: String,
        thresholdDb: Int = -30,
        durationSec: Float = 0.5f
    ): SilenceReport? = withContext(Dispatchers.IO) {
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

            if (duration <= 0.0) return@withContext null

            // Detect silence using silencedetect filter
            val detectCommandArgs = arrayOf("-i", inputPath, "-af", "silencedetect=noise=${thresholdDb}dB:d=$durationSec", "-f", "null", "-")
            val logsBuilder = StringBuilder()
            val detectSuccess = FFmpegProcessor.executeWithProgress(
                detectCommandArgs,
                sourceVideo = inputPath,
                progressOffset = 0f,
                progressScale = 1.0f,
                logCollector = { line -> logsBuilder.append(line).append("\n") }
            )

            if (!detectSuccess) return@withContext null
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

            // Calculate silence duration
            var totalSilence = 0.0
            silenceSegments.forEach {
                totalSilence += (it.second - it.first)
            }

            // Generate keep segments
            val keepSegments = mutableListOf<Pair<Double, Double>>()
            var lastKeepStart = 0.0
            val pad = 0.125 // padding on each side

            for (seg in silenceSegments) {
                val sStart = seg.first + pad
                val sEnd = seg.second - pad

                if (sStart >= sEnd) {
                    continue
                }

                if (sStart > lastKeepStart) {
                    keepSegments.add(Pair(lastKeepStart, sStart))
                }
                lastKeepStart = sEnd
            }

            if (lastKeepStart < duration) {
                keepSegments.add(Pair(lastKeepStart, duration))
            }

            if (keepSegments.isEmpty() && silenceSegments.isNotEmpty()) {
                // If everything is silent but we need at least something, keep the whole video
                keepSegments.add(Pair(0.0, duration))
            }

            return@withContext SilenceReport(
                totalDuration = duration,
                silenceSegments = silenceSegments,
                keepSegments = keepSegments,
                totalSilenceDuration = totalSilence,
                timeSaved = totalSilence
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    fun hasAudioTrack(path: String): Boolean {
        return try {
            val info = FFprobeKit.getMediaInformation(path)
            info?.mediaInformation?.streams?.any { it.type == "audio" } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeSilence(
        context: Context,
        inputPath: String,
        outputPath: String,
        thresholdDb: Int = -30,
        durationSec: Float = 0.5f,
        fastCut: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val report = detectSilenceReport(context, inputPath, thresholdDb, durationSec) ?: return@withContext false
            val keepSegments = report.keepSegments

            if (keepSegments.isEmpty() || report.silenceSegments.isEmpty()) {
                // No silence to cut or all silent, just copy input to output
                val copyCmd = arrayOf("-y", "-i", inputPath, "-c", "copy", outputPath)
                return@withContext FFmpegProcessor.executeWithProgress(copyCmd, inputPath)
            }

            val hasAudio = hasAudioTrack(inputPath)
            val filterBuilder = StringBuilder()
            var concatStr = ""

            keepSegments.forEachIndexed { index, pair ->
                val start = pair.first
                val end = pair.second
                filterBuilder.append("[0:v]trim=start=$start:end=$end,setpts=PTS-STARTPTS[v$index];")
                if (hasAudio) {
                    filterBuilder.append("[0:a]atrim=start=$start:end=$end,asetpts=PTS-STARTPTS[a$index];")
                    concatStr += "[v$index][a$index]"
                } else {
                    concatStr += "[v$index]"
                }
            }

            if (hasAudio) {
                filterBuilder.append("${concatStr}concat=n=${keepSegments.size}:v=1:a=1[outv][outa]")
                val commandArgs = arrayOf(
                    "-y", "-i", inputPath,
                    "-filter_complex", filterBuilder.toString(),
                    "-map", "[outv]", "-map", "[outa]",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                    "-c:a", "aac", "-b:a", "128k",
                    outputPath
                )
                return@withContext FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
            } else {
                filterBuilder.append("${concatStr}concat=n=${keepSegments.size}:v=1:a=0[outv]")
                val commandArgs = arrayOf(
                    "-y", "-i", inputPath,
                    "-filter_complex", filterBuilder.toString(),
                    "-map", "[outv]",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                    outputPath
                )
                return@withContext FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
