import xml.etree.ElementTree as ET
try:
    tree = ET.parse('app/src/main/res/values-ar/strings.xml')
    for elem in tree.getroot():
        if elem.get('name') in ['string_81', 'string_96', 'string_133']:
            print(elem.get('name'), elem.text)
except Exception as e:
    print('Error:', e)
