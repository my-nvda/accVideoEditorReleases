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
import androidx.compose.ui.semantics.liveRegion
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
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing
    val progress = (com.example.accessiblevideoeditor.ui.ProcessingManager.progress * 100).toInt()
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
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_72), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedImageUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_108) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_134))
        }

        TextCustomizationPanel(
            onOptionsChanged = { textOptions = it }
        )

        if (false) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_28, progress),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
        } else {
            val successMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_182) // "Saved successfully"
            val errorMessage = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_183) // "An error occurred while saving"
            
            Button(
                onClick = {
                    selectedImageUri?.let { uri ->
                        /* isProcessing = true */
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            var success = false
                            try {
                                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                    android.graphics.ImageDecoder.decodeBitmap(source)
                                } else {
                                    @Suppress("DEPRECATION")
                                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                }
                                
                                val resultBitmap = TextRenderer.drawTextOnImage(bitmap, textOptions)
                                val outputPath = context.cacheDir.absolutePath + "/edited_image_${System.currentTimeMillis()}.jpg"
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
                                /* isProcessing = false */
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
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_127))
            }
        }
    }
}

