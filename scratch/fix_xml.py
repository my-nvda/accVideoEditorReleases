import os, glob
import xml.etree.ElementTree as ET

res_dirs = glob.glob('app/src/main/res/values*')
for res_dir in res_dirs:
    strings_path = os.path.join(res_dir, 'strings.xml')
    if os.path.exists(strings_path):
        try:
            tree = ET.parse(strings_path)
            changed = False
            for elem in tree.getroot():
                if elem.text:
                    if elem.text.startswith('?') or elem.text.startswith('@'):
                        elem.text = '\\' + elem.text
                        changed = True
                    # Also check for unescaped apostrophes if any. ET doesn't escape apostrophes by default in text.
                    # Actually, Android requires \' for apostrophes.
                    if "'" in elem.text and "\\'" not in elem.text:
                        elem.text = elem.text.replace("'", "\\'")
                        changed = True
            
            if changed:
                tree.write(strings_path, encoding='utf-8', xml_declaration=True)
                print(f'Fixed {strings_path}')
        except Exception as e:
            print(f'Error parsing {strings_path}: {e}')
