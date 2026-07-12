package com.example.accessiblevideoeditor.media

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import com.example.accessiblevideoeditor.ui.SettingsManager
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class SpeechToTextProcessor {

    @Volatile
    private var isRecognizing = false

    suspend fun recognizeWavFileOnline(wavFilePath: String, onlineModel: OnlineAudioModel, onProgress: (Int) -> Unit): String = withContext(Dispatchers.IO) {
        val file = File(wavFilePath)
        if (!file.exists()) return@withContext ""

        isRecognizing = true
        onProgress(10) // start
        try {
            when (onlineModel.serviceType) {
                OnlineAudioModel.ServiceType.WIT_AI -> recognizeWithWitAi(file, onProgress)
                OnlineAudioModel.ServiceType.OPENAI -> recognizeWithOpenAi(file, onProgress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR: Connection failed ${e.message}"
        } finally {
            isRecognizing = false
            onProgress(100)
        }
    }

    private suspend fun recognizeWithWitAi(file: File, onProgress: (Int) -> Unit): String {
        val token = SettingsManager.witAiToken.trim()
        if (token.isBlank()) return "ERROR: Wit.ai token is missing"

        val url = URL("https://api.wit.ai/dictation?v=20230215")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "audio/wav")
        conn.doOutput = true
        conn.setChunkedStreamingMode(4096)
        
        val outputStream = conn.outputStream
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(4096)
        var bytesRead: Int
        var totalRead: Long = 0
        val fileLength = file.length()
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1 && coroutineContext.isActive) {
            outputStream.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            val progress = 10 + ((totalRead.toFloat() / fileLength) * 80).toInt()
            onProgress(progress)
        }
        
        inputStream.close()
        outputStream.flush()
        outputStream.close()
        
        if (conn.responseCode != 200) {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.errorStream ?: conn.inputStream))
            val errResponse = java.lang.StringBuilder()
            var errLine: String?
            while (reader.readLine().also { errLine = it } != null) {
                errResponse.append(errLine)
            }
            reader.close()
            return "ERROR: Wit.ai HTTP ${conn.responseCode} - $errResponse"
        }
        
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        
        return extractTextFromJson(response.toString())
    }

    private suspend fun recognizeWithOpenAi(file: File, onProgress: (Int) -> Unit): String {
        val key = SettingsManager.openAiKey.trim()
        if (key.isBlank()) return "ERROR: OpenAI key is missing"

        val boundary = "Boundary-" + System.currentTimeMillis()
        val url = URL("https://api.openai.com/v1/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.doOutput = true
        
        val outputStream = conn.outputStream
        
        outputStream.write(("--$boundary\r\n").toByteArray())
        outputStream.write(("Content-Disposition: form-data; name=\"model\"\r\n\r\n").toByteArray())
        outputStream.write(("whisper-1\r\n").toByteArray())
        
        outputStream.write(("--$boundary\r\n").toByteArray())
        outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n").toByteArray())
        outputStream.write(("Content-Type: audio/wav\r\n\r\n").toByteArray())
        
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(4096)
        var bytesRead: Int
        var totalRead: Long = 0
        val fileLength = file.length()
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1 && coroutineContext.isActive) {
            outputStream.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            val progress = 10 + ((totalRead.toFloat() / fileLength) * 80).toInt()
            onProgress(progress)
        }
        inputStream.close()
        
        outputStream.write(("\r\n--$boundary--\r\n").toByteArray())
        outputStream.flush()
        outputStream.close()
        
        if (conn.responseCode != 200) {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.errorStream ?: conn.inputStream))
            val errResponse = java.lang.StringBuilder()
            var errLine: String?
            while (reader.readLine().also { errLine = it } != null) {
                errResponse.append(errLine)
            }
            reader.close()
            return "ERROR: OpenAI HTTP ${conn.responseCode} - $errResponse"
        }
        
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        
        return extractTextFromJson(response.toString())
    }

    private fun extractTextFromJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            json.optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }
    
    fun release() {
        // Nothing to release natively
    }
}
