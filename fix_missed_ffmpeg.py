import os
import re

files_to_fix = [
    r'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/WatermarkScreen.kt',
    r'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/ReverseMediaScreen.kt',
    r'app/src/main/java/com/example/accessiblevideoeditor/media/SmartCutProcessor.kt'
]

for file_path in files_to_fix:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if 'WatermarkScreen' in file_path:
        content = re.sub(
            r'val session = com\.arthenica\.ffmpegkit\.FFmpegKit\.execute\(command\)\s+if \(com\.arthenica\.ffmpegkit\.ReturnCode\.isSuccess\(session\.returnCode\)\) \{\s+com\.example\.accessiblevideoeditor\.utils\.FileUtils\.saveToGallery\(context, java\.io\.File\(outputPath\), "video/mp4"\)\s+kotlinx\.coroutines\.withContext\(kotlinx\.coroutines\.Dispatchers\.Main\) \{\s+android\.widget\.Toast\.makeText\(context, "[^"]+", android\.widget\.Toast\.LENGTH_SHORT\)\.show\(\)\s+\}\s+\} else \{\s+kotlinx\.coroutines\.withContext\(kotlinx\.coroutines\.Dispatchers\.Main\) \{\s+android\.widget\.Toast\.makeText\(context, "[^"]+", android\.widget\.Toast\.LENGTH_LONG\)\.show\(\)\s+\}\s+\}',
            '''val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
                                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, " „  «·⁄„·Ì… »‰Ã«Õ", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val logs = session.failStackTrace ?: session.allLogsAsString ?: "Unknown Error"
                                    val detailedLog = "Command:\\n\\n\\nLogs:\\n"
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                                        android.widget.Toast.makeText(context, "ÕœÀ Œÿ√ √À‰«¡ „⁄«·Ã… «·›ÌœÌÊ", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }''',
            content
        )
    elif 'ReverseMediaScreen' in file_path:
        content = re.sub(
            r'com\.arthenica\.ffmpegkit\.FFmpegKit\.execute\(command\)\s+com\.example\.accessiblevideoeditor\.utils\.FileUtils\.saveToGallery\(context, java\.io\.File\(outputPath\), "video/mp4"\)',
            '''val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
                            if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                                val logs = session.failStackTrace ?: session.allLogsAsString ?: "Unknown Error"
                                val detailedLog = "Command:\\n\\n\\nLogs:\\n"
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                                }
                            } else {
                                com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                            }''',
            content
        )
    elif 'SmartCutProcessor' in file_path:
        content = re.sub(
            r'val detectSession = FFmpegKit\.execute\(detectCommand\)',
            '''val detectSession = FFmpegKit.execute(detectCommand)
            if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(detectSession.returnCode)) {
                val logs = detectSession.failStackTrace ?: detectSession.allLogsAsString ?: "Unknown Error"
                val detailedLog = "Command:\\n\\n\\nLogs:\\n"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                }
            }''',
            content
        )
        content = re.sub(
            r'val cutSession = FFmpegKit\.executeWithArguments\(commandArgs\)\s+return ReturnCode\.isSuccess\(cutSession\.returnCode\)',
            '''val cutSession = FFmpegKit.executeWithArguments(commandArgs)
            val isSuccess = com.arthenica.ffmpegkit.ReturnCode.isSuccess(cutSession.returnCode)
            if (!isSuccess) {
                val logs = cutSession.failStackTrace ?: cutSession.allLogsAsString ?: "Unknown Error"
                val cmdStr = commandArgs.joinToString(" ")
                val detailedLog = "Command:\\n\\n\\nLogs:\\n"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                }
            }
            return isSuccess''',
            content
        )
        
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

print("Done replacing.")
