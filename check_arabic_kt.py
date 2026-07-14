import re, os

arabic_pattern = re.compile(r'[\u0600-\u06FF]+')
found = False

for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            for i, line in enumerate(lines):
                if arabic_pattern.search(line):
                    print(f'{path}:{i+1}: {line.strip()}')
                    found = True

if not found:
    print('No Arabic text found in Kotlin files')
