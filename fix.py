
import re

def remove_dups(filepath):
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    
    seen = set()
    new_content = []
    
    for line in content.split('\n'):
        match = re.search(r'<string name=\"([^\"]+)\">', line)
        if match:
            name = match.group(1)
            if name in seen:
                continue
            seen.add(name)
        new_content.append(line)
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write('\n'.join(new_content))

remove_dups('app/src/main/res/values/strings.xml')
remove_dups('app/src/main/res/values-ar/strings.xml')
