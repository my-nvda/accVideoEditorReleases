import os, json, sys
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator

sys.stdout.reconfigure(line_buffering=True)

langs = ['ar', 'he', 'fr', 'fa', 'ur', 'tr', 'es', 'ru', 'zh-CN', 'ja']
lang_codes = {
    'zh-CN': 'zh-rCN'
}

# 1. Parse values/strings.xml
en_strings = {}
tree = ET.parse('app/src/main/res/values/strings.xml')
for elem in tree.getroot():
    en_strings[elem.get('name')] = elem.text

# 2. Parse strings_db.json to get existing translations
db_translations = {} # key -> {lang: text}
with open(r'C:\Users\AHMED\.gemini\antigravity\brain\89629709-b27b-4a75-af6e-9d34936b309e\scratch\strings_db.json', 'r', encoding='utf-8') as f:
    strings_db = json.load(f)

for original, data in strings_db.items():
    key = data['key']
    translations = data.get('translations', {})
    if key not in db_translations:
        db_translations[key] = translations
    else:
        db_translations[key].update(translations)

from concurrent.futures import ThreadPoolExecutor

# 3. Generate translations for missing keys or missing langs
def translate_task(key, lang, en_text, mapped_lang):
    try:
        translated = GoogleTranslator(source='en', target=mapped_lang).translate(en_text)
        return key, lang, translated
    except Exception as e:
        print(f"Failed to translate {key} to {lang}: {e}")
        return key, lang, en_text

futures = []
with ThreadPoolExecutor(max_workers=10) as executor:
    for key, en_text in en_strings.items():
        if key not in db_translations:
            db_translations[key] = {'en': en_text}
        
        if 'en' not in db_translations[key] or not db_translations[key]['en']:
            db_translations[key]['en'] = en_text
            
        if key == 'string_133': db_translations[key]['ar'] = "الضبط"
        if key == 'string_81': db_translations[key]['ar'] = "لغة التطبيق"
        
        for lang in langs:
            mapped_lang = 'zh-CN' if lang == 'zh-rCN' else ('iw' if lang == 'he' else lang)
            if lang not in db_translations[key] or not db_translations[key][lang]:
                futures.append(executor.submit(translate_task, key, lang, en_text, mapped_lang))

    for future in futures:
        key, lang, translated = future.result()
        db_translations[key][lang] = translated
        print(f"Translated {key} to {lang}")

# 4. Write back strings_db.json? Not strictly necessary, but good for backup.
# 5. Write out all values-*/strings.xml
for lang in langs:
    folder_lang = lang_codes.get(lang, lang)
    folder_path = f'app/src/main/res/values-{folder_lang}'
    os.makedirs(folder_path, exist_ok=True)
    xml_path = os.path.join(folder_path, 'strings.xml')
    
    root = ET.Element('resources')
    for key, en_text in en_strings.items():
        text = db_translations[key].get(lang, en_text)
        if text is None: text = ""
        elem = ET.Element('string', {'name': key})
        # replace problematic chars
        text = text.replace("'", "\\'").replace('"', '\\"')
        elem.text = text
        root.append(elem)
        
    # write to file
    tree = ET.ElementTree(root)
    tree.write(xml_path, encoding='utf-8', xml_declaration=True)

print("Successfully synchronized all strings.xml files!")
