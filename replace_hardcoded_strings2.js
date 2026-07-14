const fs = require('fs');

function replaceInFile(filepath, replacements) {
    let content = fs.readFileSync(filepath, 'utf8');
    let original = content;
    
    for (let i = 0; i < replacements.length; i++) {
        let regex = new RegExp(replacements[i].target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
        content = content.replace(regex, replacements[i].replacement);
    }
    
    if (content !== original) {
        fs.writeFileSync(filepath, content, 'utf8');
        console.log("Updated " + filepath);
    } else {
        console.log("No changes in " + filepath);
    }
}

// GlobalProgressDialog.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/GlobalProgressDialog.kt', [
    { target: '"حدث خطأ تقني (Error)"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_199)' },
    { target: '"تفاصيل الخطأ:"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_200)' }
]);

// HistoryScreen.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/screens/HistoryScreen.kt', [
    { target: '"خيارات إضافية"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_201)' }
]);
