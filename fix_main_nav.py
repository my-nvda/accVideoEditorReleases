import re

filepath = "app/src/main/java/com/example/accessiblevideoeditor/ui/MainNavigation.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix ImageEditorScreen
content = content.replace("onApplyText = { textOptions, uri ->", "onApplyText = { textOptions: Any, uri: android.net.Uri? ->")

# Fix VideoTrimmerScreen
content = content.replace("onApplyTrim = { startStr, durationStr, uri ->", "onApplyTrim = { startStr: String, durationStr: String, uri: android.net.Uri? ->")

# Fix AudioEditorScreen
content = content.replace("onRemoveAudio = { videoUri ->", "onRemoveAudio = { videoUri: android.net.Uri? ->")
content = content.replace("onReplaceAudio = { videoUri, audioUri ->", "onReplaceAudio = { videoUri: android.net.Uri?, audioUri: android.net.Uri? ->")
content = content.replace("onMixAudio = { videoUri, audioUri ->", "onMixAudio = { videoUri: android.net.Uri?, audioUri: android.net.Uri? ->")

# Fix VideoEditorScreen
content = content.replace("onApplyText = { textOptions, start, end, uri ->", "onApplyText = { textOptions: Any, start: String, end: String, uri: android.net.Uri? ->")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed MainNavigation types")
