import re

content = open('../app/src/main/res/values/strings.xml', 'r', encoding='utf-8').read()
matches = re.findall(r'<string name="([^"]+)">([^<]*)</string>', content)

out_lines = []
for name, val in matches:
    num_part = ''.join(c for c in name if c.isdigit())
    if num_part:
        val_int = int(num_part)
        if 240 <= val_int <= 270:
            out_lines.append(f"{name}: {val}")

open('trans_strings.txt', 'w', encoding='utf-8').write('\n'.join(out_lines))
print("Done extracting translation strings.")
