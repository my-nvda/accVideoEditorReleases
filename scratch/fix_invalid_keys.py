import os
import glob

files = glob.glob('app/src/main/res/values*/strings.xml')

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = content.replace('name="2_choose_the_watermark_im_24"', 'name="string_24"')
    content = content.replace('name="1_choose_media_77"', 'name="string_77"')
    content = content.replace('name="1_select_the_video_89"', 'name="string_89"')
    content = content.replace('name="2_procedures_110"', 'name="string_110"')
    
    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)

with open('scratch/strings_db.json', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('"2_choose_the_watermark_im_24"', '"string_24"')
content = content.replace('"1_choose_media_77"', '"string_77"')
content = content.replace('"1_select_the_video_89"', '"string_89"')
content = content.replace('"2_procedures_110"', '"string_110"')

with open('scratch/strings_db.json', 'w', encoding='utf-8') as f:
    f.write(content)
