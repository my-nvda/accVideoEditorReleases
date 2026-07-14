import xml.etree.ElementTree as ET

en_path = 'app/src/main/res/values/strings.xml'
ar_path = 'app/src/main/res/values-ar/strings.xml'

def append_strings(path, strings_dict):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    new_strings = ''
    for k, v in strings_dict.items():
        new_strings += f'    <string name="{k}">{v}</string>\n'
    
    content = content.replace('</resources>', new_strings + '</resources>')
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

en_strings = {
    'string_201': 'New update available!',
    'string_202': 'App Update',
    'string_203': 'Downloading update %s...'
}

ar_strings = {
    'string_201': 'تحديث جديد متوفر!',
    'string_202': 'تحديث التطبيق',
    'string_203': 'جاري تنزيل التحديث %s...'
}

append_strings(en_path, en_strings)
append_strings(ar_path, ar_strings)
print('Done')
