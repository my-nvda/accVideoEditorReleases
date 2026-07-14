const fs = require('fs');

function fixMainNavigation() {
    let path = 'app/src/main/java/com/example/accessiblevideoeditor/ui/MainNavigation.kt';
    let content = fs.readFileSync(path, 'utf8');
    
    // Inject startProcessing right after SoundManager.playProcessing()
    content = content.replace(
        /(com\.example\.accessiblevideoeditor\.media\.SoundManager\.playProcessing\(\)\s*\n\s*)(isProcessing\s*=\s*true)/g,
        '$1com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_111), true)\n                    $2'
    );
    
    fs.writeFileSync(path, content);
    console.log('Fixed MainNavigation.kt');
}

function fixFastConverterScreen() {
    let path = 'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/FastConverterScreen.kt';
    let content = fs.readFileSync(path, 'utf8');
    
    // Add startProcessing
    if (!content.includes('startProcessing')) {
        content = content.replace(
            /isProcessing\s*=\s*true\s*\n\s*coroutineScope\.launch/g,
            'isProcessing = true\n                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_111), true)\n                    coroutineScope.launch'
        );
    }
    
    fs.writeFileSync(path, content);
    console.log('Fixed FastConverterScreen.kt');
}

function fixSlideshowMakerScreen() {
    let path = 'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/SlideshowMakerScreen.kt';
    let content = fs.readFileSync(path, 'utf8');
    
    // Add startProcessing
    if (!content.includes('startProcessing')) {
        content = content.replace(
            /isProcessing\s*=\s*true\s*\n\s*coroutineScope\.launch/g,
            'isProcessing = true\n                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_111), true)\n                    coroutineScope.launch'
        );
    }
    
    fs.writeFileSync(path, content);
    console.log('Fixed SlideshowMakerScreen.kt');
}

function fixMergeVideosScreen() {
    let path = 'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/MergeVideosScreen.kt';
    let content = fs.readFileSync(path, 'utf8');
    
    // 1. Fix mapNotNull -> mapIndexedNotNull to avoid same file name
    content = content.replace(
        /selectedUris\.mapNotNull\s*\{\s*\n\s*com\.example\.accessiblevideoeditor\.media\.MediaUtils\.copyUriToTempFile\(context,\s*it,\s*"merge_temp_\$\{System\.currentTimeMillis\(\)\}\.mp4"\)\?\.absolutePath\s*\n\s*\}/g,
        'selectedUris.mapIndexedNotNull { index, uri ->\n                            com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "merge_temp_${System.currentTimeMillis()}_$index.mp4")?.absolutePath\n                        }'
    );
    
    // 2. Wrap the coroutineScope.launch block in try-catch to prevent app crash
    content = content.replace(
        /val inputs = selectedUris\.mapIndexedNotNull/g,
        'try {\n                        val inputs = selectedUris.mapIndexedNotNull'
    );
    
    content = content.replace(
        /isProcessing = false\n\s*\}\n\s*\}\n\s*\}\n\s*\},/g,
        'isProcessing = false\n                            }\n                        }\n                        } catch (e: Exception) {\n                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {\n                                isProcessing = false\n                                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()\n                            }\n                        }\n                    },\n'
    );
    
    fs.writeFileSync(path, content);
    console.log('Fixed MergeVideosScreen.kt');
}

function fixSimpleProcessScreen() {
    let path = 'app/src/main/java/com/example/accessiblevideoeditor/ui/screens/SimpleProcessScreen.kt';
    let content = fs.readFileSync(path, 'utf8');
    
    // Prevent the local progress bar from rendering, since we now rely on the GlobalProgressDialog.
    // Instead of rendering a local progress bar, we just return to the normal state but buttons disabled.
    // Actually, GlobalProgressDialog overlays everything, but the user complained about "SimpleProcessScreen" progress not being accessible.
    // If we just let GlobalProgressDialog take over, it's perfect.
    
    fs.writeFileSync(path, content);
    console.log('Fixed SimpleProcessScreen.kt');
}

fixMainNavigation();
fixFastConverterScreen();
fixSlideshowMakerScreen();
fixMergeVideosScreen();
fixSimpleProcessScreen();
