import re

filepath = "app/src/main/java/com/example/accessiblevideoeditor/ui/screens/AiAnalysisScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Replace the inputContent block with a pre-extraction block
old_content = """val inputContent = content {
                            if (selectedImage != null) {
                                val bitmap = withContext(Dispatchers.IO) {
                                    val inputStream = context.contentResolver.openInputStream(selectedImage!!)
                                    android.graphics.BitmapFactory.decodeStream(inputStream)
                                }
                                image(bitmap)
                            } else if (selectedVideo != null) {
                                val frames = extractVideoFrames(context, selectedVideo!!)
                                frames.forEach { image(it) }
                            }
                            val promptText = if (userQuestion.isNotBlank()) userQuestion else "’› Â–Â «·’Ê—… √Ê «·›ÌœÌÊ »«· ›’Ì· »«··€… «·⁄—»Ì…. —ﬂ“ ⁄·Ï «·⁄‰«’— «·„—∆Ì… Ê«·√‘Œ«’ Ê«·√›⁄«· Ê«·‰’Ê’ «·„ﬂ Ê»… ≈‰ ÊÃœ ."
                            text(promptText)
                        }"""

new_content = """
                        val bitmaps = if (selectedImage != null) {
                            withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(selectedImage!!)
                                listOf(android.graphics.BitmapFactory.decodeStream(inputStream))
                            }
                        } else if (selectedVideo != null) {
                            extractVideoFrames(context, selectedVideo!!)
                        } else {
                            emptyList()
                        }
                        
                        val inputContent = content {
                            bitmaps.forEach { image(it) }
                            val promptText = if (userQuestion.isNotBlank()) userQuestion else "’› Â–Â «·’Ê—… √Ê «·›ÌœÌÊ »«· ›’Ì· »«··€… «·⁄—»Ì…. —ﬂ“ ⁄·Ï «·⁄‰«’— «·„—∆Ì… Ê«·√‘Œ«’ Ê«·√›⁄«· Ê«·‰’Ê’ «·„ﬂ Ê»… ≈‰ ÊÃœ ."
                            text(promptText)
                        }"""

content = content.replace(old_content, new_content)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed AiAnalysisScreen")
