import re

filepath = "app/src/main/java/com/example/accessiblevideoeditor/ui/MainNavigation.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Undo explicit types
content = content.replace("onApplyText = { textOptions: Any, uri: android.net.Uri? ->", "onApplyText = { textOptions, uri ->")
content = content.replace("onApplyTrim = { startStr: String, durationStr: String, uri: android.net.Uri? ->", "onApplyTrim = { startStr, durationStr, uri ->")
content = content.replace("onRemoveAudio = { videoUri: android.net.Uri? ->", "onRemoveAudio = { videoUri ->")
content = content.replace("onReplaceAudio = { videoUri: android.net.Uri?, audioUri: android.net.Uri? ->", "onReplaceAudio = { videoUri, audioUri ->")
content = content.replace("onMixAudio = { videoUri: android.net.Uri?, audioUri: android.net.Uri? ->", "onMixAudio = { videoUri, audioUri ->")
content = content.replace("onApplyText = { textOptions: Any, start: String, end: String, uri: android.net.Uri? ->", "onApplyText = { textOptions, start, end, uri ->")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Undid MainNavigation types")
