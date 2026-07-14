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

// MainNavigation.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/MainNavigation.kt', [
    { target: '"تحديث جديد"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_184)' },
    { target: '"تم العثور على تحديث جديد..."', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_185)' },
    { target: '"تنزيل وتثبيت"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_186)' },
    { target: '"لاحقاً"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_187)' },
    { target: '"جاري تحميل التحديث"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_188)' },
    { target: '"اكتمل: ${percent}%"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_189).replace("%1$d", percent.toString())' },
    { target: '"اكتمل: $percent%"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_189).replace("%1$d", percent.toString())' },
    { target: '"تم تحميل..."', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_190)' },
    { target: '"المتبقي..."', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_191)' },
    { target: '"جاري التهيئة..."', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_192)' },
    { target: '"إلغاء التحميل"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_193)' },
    { target: '"جاري التحقق من التحديثات..."', replacement: 'context.getString(com.example.accessiblevideoeditor.R.string.string_194)' },
    { target: '"أنت تستخدم أحدث إصدار بالفعل!"', replacement: 'context.getString(com.example.accessiblevideoeditor.R.string.string_195)' },
    { target: '"حدث خطأ أثناء استخراج الصوت"', replacement: 'context.getString(com.example.accessiblevideoeditor.R.string.string_196)' }
]);

// ReverseMediaScreen.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/screens/ReverseMediaScreen.kt', [
    { target: '"تمت العملية بنجاح"', replacement: 'context.getString(com.example.accessiblevideoeditor.R.string.string_197)' },
    { target: '"حدث خطأ أثناء معالجة الفيديو"', replacement: 'context.getString(com.example.accessiblevideoeditor.R.string.string_198)' }
]);

// GlobalProgressDialog.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/components/GlobalProgressDialog.kt', [
    { target: '"حدث خطأ تقني (Error)"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_199)' },
    { target: '"تفاصيل الخطأ:"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_200)' }
]);

// HistoryScreen.kt
replaceInFile('app/src/main/java/com/example/accessiblevideoeditor/ui/screens/HistoryScreen.kt', [
    { target: '"خيارات إضافية"', replacement: 'com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.R.string.string_201)' }
]);
