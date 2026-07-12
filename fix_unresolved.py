import os
import re

def fix_ffmpeg():
    file = r"app/src/main/java/com/example/accessiblevideoeditor/media/FFmpegProcessor.kt"
    with open(file, "r", encoding="utf-8") as f:
        content = f.read()
    
    content = content.replace("private suspend fun executeWithProgress", "suspend fun executeWithProgress")
    content = content.replace("suspend fun drawTextOnImage(sourceImage: String, text: String, outputPath: String)", "suspend fun drawTextOnImage(context: android.content.Context, sourceImage: String, text: String, outputPath: String)")
    
    if "suspend fun createSlideshow(" not in content:
        slideshow_method = """
    suspend fun createSlideshow(images: List<String>, audioFile: String?, durationPerImage: Int, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext false
        val listFile = java.io.File(images[0]).parentFile.absolutePath + "/images.txt"
        with(java.io.File(listFile)) {
            writeText(images.joinToString("\\n") { "file '${it}'\\nduration $durationPerImage" })
        }
        val commandArgs = if (audioFile != null) {
            arrayOf("-y", "-f", "concat", "-safe", "0", "-i", listFile, "-i", audioFile, "-c:v", "libx264", "-c:a", "aac", "-pix_fmt", "yuv420p", "-shortest", outputPath)
        } else {
            arrayOf("-y", "-f", "concat", "-safe", "0", "-i", listFile, "-c:v", "libx264", "-pix_fmt", "yuv420p", outputPath)
        }
        executeWithProgress(commandArgs)
    }
"""
        content += slideshow_method
        
    with open(file, "w", encoding="utf-8") as f:
        f.write(content)

def fix_screen(filepath, name):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # Remove val ffmpegProcessor = com.example.accessiblevideoeditor.media.FFmpegProcessor(context) { p -> progress = p }
    content = re.sub(r"val ffmpegProcessor\s*=\s*com\.example\.accessiblevideoeditor\.media\.FFmpegProcessor\(context\).*?\n", "", content)
    # Replace ffmpegProcessor. with com.example.accessiblevideoeditor.media.FFmpegProcessor.
    content = content.replace("ffmpegProcessor.", "com.example.accessiblevideoeditor.media.FFmpegProcessor.")
    
    # Fix SlideshowMakerScreen context
    if name == "SlideshowMakerScreen":
        content = content.replace(
            "val imagePaths = selectedUris.mapNotNull { com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, it) }",
            "val context = androidx.compose.ui.platform.LocalContext.current\\n                        val imagePaths = selectedUris.mapNotNull { com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, it) }"
        )
        content = content.replace("com.example.accessiblevideoeditor.media.FFmpegProcessor.createSlideshow(imagePaths, outputPath)", "com.example.accessiblevideoeditor.media.FFmpegProcessor.createSlideshow(imagePaths, null, 3, outputPath)")

    # Fix drawTextOnImage signature
    if name == "ImageEditorScreen":
        content = content.replace(
            "com.example.accessiblevideoeditor.media.FFmpegProcessor.drawTextOnImage(inputPath, selectedText, outputPath)",
            "com.example.accessiblevideoeditor.media.FFmpegProcessor.drawTextOnImage(context, inputPath, selectedText, outputPath)"
        )

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

fix_ffmpeg()
fix_screen(r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/AudioStudioScreen.kt", "AudioStudioScreen")
fix_screen(r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/BatchProcessScreen.kt", "BatchProcessScreen")
fix_screen(r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/FastConverterScreen.kt", "FastConverterScreen")
fix_screen(r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/ImageEditorScreen.kt", "ImageEditorScreen")
fix_screen(r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/SlideshowMakerScreen.kt", "SlideshowMakerScreen")

