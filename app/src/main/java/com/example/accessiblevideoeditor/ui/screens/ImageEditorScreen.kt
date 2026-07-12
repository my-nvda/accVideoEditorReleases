package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.utils.FileUtils
import com.example.accessiblevideoeditor.media.TextRenderer
import java.io.File

@Composable
fun ImageEditorScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var textOptions by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_72), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedImageUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_108) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_134))
        }

        TextCustomizationPanel(
            onOptionsChanged = { textOptions = it }
        )

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_28, progress))
        } else {
            val successMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_176) // "Saved successfully"
            val errorMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_177) // "An error occurred while saving"
            
            Button(
                onClick = {
                    selectedImageUri?.let { uri ->
                        isProcessing = true
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "temp_img_${System.currentTimeMillis()}.jpg")
                            val outputPath = context.cacheDir.absolutePath + "/edited_image_${System.currentTimeMillis()}.jpg"
                            var success = false
                            if (tempInput != null) {
                                success = com.example.accessiblevideoeditor.media.FFmpegProcessor.drawTextOnImage(context, tempInput.absolutePath, textOptions, outputPath)
                                if (success) {
                                    val savedUri = FileUtils.saveToGallery(context, File(outputPath), "image/jpeg")
                                    if(savedUri == null) success = false
                                }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isProcessing = false
                                if (success) {
                                    Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageUri != null && textOptions.text.isNotBlank()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_127))
            }
        }
    }
}
