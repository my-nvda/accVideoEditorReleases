import os

files = [
    'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/MergeVideosScreen.kt',
    'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/ReverseMediaScreen.kt',
    'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/SmartCutScreen.kt'
]

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = content.replace("import androidx.compose.ui.platform.LocalContext\\nfun ", "fun ")
    content = content.replace("val context = LocalContext.current\\n    val coroutineScope = rememberCoroutineScope()", "val coroutineScope = rememberCoroutineScope()")
    
    if "import androidx.compose.ui.platform.LocalContext" not in content:
        content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext")
    
    if "val context = LocalContext.current" not in content:
        content = content.replace("val coroutineScope = rememberCoroutineScope()", "val context = LocalContext.current\n    val coroutineScope = rememberCoroutineScope()")
        
    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)
