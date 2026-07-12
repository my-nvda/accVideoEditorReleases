import re

filepath = r"app/src/main/java/com/example/accessiblevideoeditor/ui/screens/HistoryScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("import androidx.compose.material.icons.Icons\n", "")
content = content.replace("import androidx.compose.material.icons.filled.PlayArrow\n", "")
content = content.replace("Icon(imageVector = Icons.Default.PlayArrow, contentDescription = \" ‘€Ì·\")", "Text(\" ‘€Ì·\")")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)

