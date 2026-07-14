package com.example.accessiblevideoeditor.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.media.TextRenderer
import com.example.accessiblevideoeditor.utils.FileUtils
import java.io.File

@Composable
fun CreateBlankImageScreen(onBack: () -> Unit) {
    var textOptions by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_270), style = MaterialTheme.typography.titleLarge)

        TextCustomizationPanel(
            onOptionsChanged = { textOptions = it }
        )

        val successMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182)
        val errorMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183)

        Button(
            onClick = {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    var success = false
                    try {
                        val width = 1080
                        val height = 1080
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.BLACK)

                        val resultBitmap = TextRenderer.drawTextOnImage(bitmap, textOptions)
                        val outputPath = context.cacheDir.absolutePath + "/created_image_${System.currentTimeMillis()}.jpg"
                        val out = java.io.FileOutputStream(outputPath)
                        resultBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                        out.flush()
                        out.close()

                        val savedUri = FileUtils.saveToGallery(context, File(outputPath), "image/jpeg")
                        if (savedUri != null) success = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = textOptions.text.isNotBlank()
        ) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_127))
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_207))
        }
    }
}
