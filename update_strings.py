import xml.etree.ElementTree as ET
import os

# Updates to English
en_updates = {
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
}

en_additions = {
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
}

ar_additions = {
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
}

def update_xml(file_path, updates, additions):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Apply updates
    if updates:
        for k, v in updates.items():
            import re
            content = re.sub(f'<string name="{k}">.*?</string>', f'<string name="{k}">{v}</string>', content)

    # Append additions before </resources>
    if additions:
        new_elements = ""
        for k, v in additions.items():
            if f'name="{k}"' not in content:
                new_elements += f'    <string name="{k}">{v}</string>\n'
        
        if new_elements:
            content = content.replace("</resources>", f"{new_elements}</resources>")
            
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

update_xml("app/src/main/res/values/strings.xml", en_updates, en_additions)
update_xml("app/src/main/res/values-ar/strings.xml", None, ar_additions)
print("Done")
