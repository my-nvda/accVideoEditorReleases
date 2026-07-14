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
    "string_209": "New Update",
    "string_210": "New update found...",
    "string_211": "Download and install",
    "string_212": "Later",
    "string_213": "Downloading update",
    "string_214": "Completed: %1$d%%",
    "string_215": "Downloaded...",
    "string_216": "Remaining...",
    "string_217": "Initializing...",
    "string_218": "Cancel download",
    "string_219": "Checking for updates...",
    "string_220": "You are already using the latest version!",
    "string_221": "An error occurred while extracting audio",
    "string_222": "Operation completed successfully",
    "string_223": "An error occurred while processing video",
    "string_224": "Technical error occurred",
    "string_225": "Error details:",
    "string_226": "Additional options"
};

const arStrings = {
    "string_209": "تحديث جديد",
    "string_210": "تم العثور على تحديث جديد...",
    "string_211": "تنزيل وتثبيت",
    "string_212": "لاحقاً",
    "string_213": "جاري تحميل التحديث",
    "string_214": "اكتمل: %1$d%%",
    "string_215": "تم تحميل...",
    "string_216": "المتبقي...",
    "string_217": "جاري التهيئة...",
    "string_218": "إلغاء التحميل",
    "string_219": "جاري التحقق من التحديثات...",
    "string_220": "أنت تستخدم أحدث إصدار بالفعل!",
    "string_221": "حدث خطأ أثناء استخراج الصوت",
    "string_222": "تمت العملية بنجاح",
    "string_223": "حدث خطأ أثناء معالجة الفيديو",
    "string_224": "حدث خطأ تقني (Error)",
    "string_225": "تفاصيل الخطأ:",
    "string_226": "خيارات إضافية"
};

const baseDir = path.join('app', 'src', 'main', 'res');
const enPath = path.join(baseDir, 'values', 'strings.xml');
const arPath = path.join(baseDir, 'values-ar', 'strings.xml');

appendStrings(enPath, enStrings);
appendStrings(arPath, arStrings);
