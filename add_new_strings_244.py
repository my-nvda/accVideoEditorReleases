import os
import xml.etree.ElementTree as ET

def append_strings(filepath, new_strings):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Insert before the last </resources>
    insert_pos = content.rfind('</resources>')
    if insert_pos == -1:
        print(f"Error: </resources> not found in {filepath}")
        return
        
    strings_str = ""
    for k, v in new_strings.items():
        # Escape special characters
        v = v.replace("'", "\\'")
        strings_str += f'    <string name="{k}">{v}</string>\n'
        
    new_content = content[:insert_pos] + strings_str + content[insert_pos:]
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Added {len(new_strings)} strings to {filepath}")

en_strings = {
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
}

ar_strings = {
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
}

base_dir = r"app\src\main\res"
en_path = os.path.join(base_dir, "values", "strings.xml")
ar_path = os.path.join(base_dir, "values-ar", "strings.xml")

append_strings(en_path, en_strings)
append_strings(ar_path, ar_strings)
