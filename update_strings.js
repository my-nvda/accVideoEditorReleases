const fs = require('fs');
const enUpdates = {
    "string_79": "Slideshow Maker",
    "string_80": "Slideshow Maker",
    "string_83": "Canceling...",
    "string_110": "2. Processing",
    "string_111": "Audio Processing",
    "string_114": "Output Format",
    "string_116": "Process Log",
    "string_130": "Success Sound",
    "string_137": "Start Reversing",
    "string_138": "Reverse Audio",
    "string_142": "Operation"
};

const enAdditions = {
    "string_184": "Shadow Color",
    "string_185": "Shadow Radius",
    "string_186": "Shadow Offset X",
    "string_187": "Shadow Offset Y",
    "string_188": "Alignment",
    "string_189": "Font Family",
    "string_190": "Left",
    "string_191": "Right",
    "string_192": "Default",
    "string_193": "Serif",
    "string_194": "Sans Serif",
    "string_195": "Monospace",
    "string_196": "Gemini API Settings",
    "string_197": "Gemini API Key",
    "string_198": "Select AI Model",
    "string_199": "Text Watermark",
    "string_200": "Image Watermark"
};

const arAdditions = {
    "string_184": "لون الظل",
    "string_185": "نصف قطر الظل",
    "string_186": "الإزاحة الأفقية للظل",
    "string_187": "الإزاحة العمودية للظل",
    "string_188": "المحاذاة",
    "string_189": "نوع الخط",
    "string_190": "يسار",
    "string_191": "يمين",
    "string_192": "الافتراضي",
    "string_193": "Serif",
    "string_194": "Sans Serif",
    "string_195": "Monospace",
    "string_196": "إعدادات الذكاء الاصطناعي Gemini",
    "string_197": "مفتاح Gemini API",
    "string_198": "اختر نموذج الذكاء الاصطناعي",
    "string_199": "علامة مائية نصية",
    "string_200": "علامة مائية كصورة"
};

function updateXml(filePath, updates, additions) {
    let content = fs.readFileSync(filePath, 'utf8');

    if (updates) {
        for (const [k, v] of Object.entries(updates)) {
            const regex = new RegExp(`<string name="${k}">.*?</string>`, 'g');
            content = content.replace(regex, `<string name="${k}">${v}</string>`);
        }
    }

    if (additions) {
        let newElements = "";
        for (const [k, v] of Object.entries(additions)) {
            if (!content.includes(`name="${k}"`)) {
                newElements += `    <string name="${k}">${v}</string>\n`;
            }
        }
        if (newElements) {
            content = content.replace("</resources>", `${newElements}</resources>`);
        }
    }

    fs.writeFileSync(filePath, content, 'utf8');
}

updateXml("app/src/main/res/values/strings.xml", enUpdates, enAdditions);
updateXml("app/src/main/res/values-ar/strings.xml", null, arAdditions);

console.log("Done");
