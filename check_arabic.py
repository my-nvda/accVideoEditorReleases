import re

with open('app/src/main/res/values/strings.xml', 'r', encoding='utf-8') as f:
    content = f.read()

arabic_pattern = re.compile(r'[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]+')
matches = []

for i, line in enumerate(content.split('\n')):
    if arabic_pattern.search(line):
        matches.append(f"Line {i+1}: {line.strip()}")

if matches:
    print('Found Arabic text in English strings.xml:')
    for m in matches:
        print(m)
else:
    print('No Arabic text found in values/strings.xml')
