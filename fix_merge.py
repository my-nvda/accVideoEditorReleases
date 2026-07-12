import os
import re

base_dir = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens"

with open(os.path.join(base_dir, "MergeVideosScreen.kt"), "r", encoding="utf-8") as f:
    c = f.read()
c = c.replace("(java.io.File, List<Uri>)", "(String, List<Uri>)")
with open(os.path.join(base_dir, "MergeVideosScreen.kt"), "w", encoding="utf-8") as f:
    f.write(c)

print("Fixed MergeVideosScreen stub type")
