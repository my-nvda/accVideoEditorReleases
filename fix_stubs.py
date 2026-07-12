import os

base_dir = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens"

with open(os.path.join(base_dir, "SimpleProcessScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri

@Composable
fun SimpleProcessScreen(
    titleRes: Int = 0,
    isProcessing: Boolean = false,
    onBack: () -> Unit = {},
    onProcess: (Uri, String) -> Unit = { _, _ -> }
) {
    Box { Text("Recovering SimpleProcessScreen...") }
}
""")

with open(os.path.join(base_dir, "MergeVideosScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri

@Composable
fun MergeVideosScreen(
    isProcessing: Boolean = false,
    onBack: () -> Unit = {},
    onProcess: (java.io.File, List<Uri>) -> Unit = { _, _ -> }
) {
    Box { Text("Recovering MergeVideosScreen...") }
}
""")

with open(os.path.join(base_dir, "AudioEditorScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri

@Composable
fun AudioEditorScreen(
    isProcessing: Boolean = false,
    onRemoveAudio: (Uri) -> Unit = { _ -> },
    onReplaceAudio: (Uri, Uri) -> Unit = { _, _ -> },
    onMixAudio: (Uri, Uri) -> Unit = { _, _ -> }
) {
    Box { Text("Recovering AudioEditorScreen...") }
}
""")

with open(os.path.join(base_dir, "ImageEditorScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri
import com.example.accessiblevideoeditor.media.TextRenderer

@Composable
fun ImageEditorScreen(
    progress: Int = 0,
    isProcessing: Boolean = false,
    onApplyText: (TextRenderer.TextOptions, Uri?) -> Unit = { _, _ -> }
) {
    Box { Text("Recovering ImageEditorScreen...") }
}
""")

with open(os.path.join(base_dir, "VideoTrimmerScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri

@Composable
fun VideoTrimmerScreen(
    progress: Int = 0,
    isProcessing: Boolean = false,
    onApplyTrim: (String, String, Uri?) -> Unit = { _, _, _ -> }
) {
    Box { Text("Recovering VideoTrimmerScreen...") }
}
""")

with open(os.path.join(base_dir, "VideoEditorScreen.kt"), "w", encoding="utf-8") as f:
    f.write("""package com.example.accessiblevideoeditor.ui.screens
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import android.net.Uri
import com.example.accessiblevideoeditor.media.TextRenderer

@Composable
fun VideoEditorScreen(
    progress: Int = 0,
    isProcessing: Boolean = false,
    onApplyText: (TextRenderer.TextOptions, String, String, Uri?) -> Unit = { _, _, _, _ -> }
) {
    Box { Text("Recovering VideoEditorScreen...") }
}
""")

print("Done fixing stubs")
