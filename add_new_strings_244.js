const fs = require('fs');
const path = require('path');

function appendStrings(filepath, newStrings) {
    let content = fs.readFileSync(filepath, 'utf8');
    
    let insertPos = content.lastIndexOf('</resources>');
    if (insertPos === -1) {
        console.error("Error: </resources> not found in " + filepath);
        return;
    }
        
    let stringsStr = "";
    for (let k in newStrings) {
        let v = newStrings[k].replace(/'/g, "\\'");
        stringsStr += '    <string name="' + k + '">' + v + '</string>\n';
    }
        
    let newContent = content.substring(0, insertPos) + stringsStr + content.substring(insertPos);
    
    fs.writeFileSync(filepath, newContent, 'utf8');
    console.log("Added " + Object.keys(newStrings).length + " strings to " + filepath);
}

const enStrings = {
    "string_184": "New Update",
    "string_185": "New update found...",
    "string_186": "Download and install",
    "string_187": "Later",
    "string_188": "Downloading update",
    "string_189": "Completed: %1$d%%",
    "string_190": "Downloaded...",
    "string_191": "Remaining...",
    "string_192": "Initializing...",
    "string_193": "Cancel download",
    "string_194": "Checking for updates...",
    "string_195": "You are already using the latest version!",
    "string_196": "An error occurred while extracting audio",
    "string_197": "Operation completed successfully",
    "string_198": "An error occurred while processing video",
    "string_199": "Technical error occurred",
    "string_200": "Error details:",
    "string_201": "Additional options"
};

const arStrings = {
    "string_184": "تحديث جديد",
    "string_185": "تم العثور على تحديث جديد...",
    "string_186": "تنزيل وتثبيت",
    "string_187": "لاحقاً",
    "string_188": "جاري تحميل التحديث",
    "string_189": "اكتمل: %1$d%%",
    "string_190": "تم تحميل...",
    "string_191": "المتبقي...",
    "string_192": "جاري التهيئة...",
    "string_193": "إلغاء التحميل",
    "string_194": "جاري التحقق من التحديثات...",
    "string_195": "أنت تستخدم أحدث إصدار بالفعل!",
    "string_196": "حدث خطأ أثناء استخراج الصوت",
    "string_197": "تمت العملية بنجاح",
    "string_198": "حدث خطأ أثناء معالجة الفيديو",
    "string_199": "حدث خطأ تقني (Error)",
    "string_200": "تفاصيل الخطأ:",
    "string_201": "خيارات إضافية"
};

const baseDir = path.join('app', 'src', 'main', 'res');
const enPath = path.join(baseDir, 'values', 'strings.xml');
const arPath = path.join(baseDir, 'values-ar', 'strings.xml');

appendStrings(enPath, enStrings);
appendStrings(arPath, arStrings);
