import os

def add_strings_to_xml(file_path, new_strings_list):
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return
    
    content = open(file_path, 'r', encoding='utf-8').read()
    
    # Check if they are already added
    if "string_translation_local" in content:
        print(f"Strings already present in {file_path}")
        return
    
    # We want to insert just before </resources>
    closing_tag = "</resources>"
    idx = content.rfind(closing_tag)
    if idx == -1:
        print(f"Could not find </resources> tag in {file_path}")
        return
        
    inserted_str = "\n" + "\n".join(new_strings_list) + "\n"
    new_content = content[:idx] + inserted_str + content[idx:]
    
    open(file_path, 'w', encoding='utf-8').write(new_content)
    print(f"Successfully added strings to {file_path}")

eng_strings = [
    '    <string name="string_translation_local">Local Translation</string>',
    '    <string name="string_translation_cloud">Cloud Translation</string>',
    '    <string name="string_translation_suggestion">Local Suggestion: %1$s</string>',
    '    <string name="btn_apply_suggestion">Apply</string>',
    '    <string name="action_sync_cloud_from_local">Sync from Local</string>',
    '    <string name="msg_sync_suggestions_success">Synced %1$d missing translations from Local tab</string>',
    '    <string name="msg_no_suggestions_to_sync">No missing translations to sync from Local tab</string>'
]

ar_strings = [
    '    <string name="string_translation_local">الترجمة المحلية</string>',
    '    <string name="string_translation_cloud">الترجمة السحابية</string>',
    '    <string name="string_translation_suggestion">الاقتراح المحلي: %1$s</string>',
    '    <string name="btn_apply_suggestion">تطبيق</string>',
    '    <string name="action_sync_cloud_from_local">مزامنة من المحلي</string>',
    '    <string name="msg_sync_suggestions_success">تمت مزامنة %1$d من التراجم المفقودة من التبويب المحلي</string>',
    '    <string name="msg_no_suggestions_to_sync">لا توجد تراجم مفقودة لمزامنتها من التبويب المحلي</string>'
]

add_strings_to_xml('../app/src/main/res/values/strings.xml', eng_strings)
add_strings_to_xml('../app/src/main/res/values-ar/strings.xml', ar_strings)
