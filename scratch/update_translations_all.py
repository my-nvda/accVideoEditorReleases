import xml.etree.ElementTree as ET
import os

translations = {
    'es': {
        'string_1': 'Describe esta foto o video en detalle. Concéntrese en las imágenes, las personas, las acciones y los textos escritos, si están disponibles.',
        'string_2': 'Extraiga y transcriba el texto de este archivo de audio con gran precisión. Escriba solo el texto extraído sin adiciones.',
        'string_145': 'Reproducir',
        'string_148': 'Twitter',
        'string_149': 'Cian',
        'string_155': 'Arriba',
        'string_157': 'Abajo',
        'string_182': 'Operación completada con éxito',
        'string_183': 'La operación falló',
        'string_159': 'Ayuda y Acerca de',
        'string_160': 'Cómo usar',
        'string_161': 'Acerca de',
        'string_162': 'Accessible Video Editor es una herramienta integral de edición multimedia diseñada teniendo en cuenta la accesibilidad. Es compatible con 11 idiomas y ofrece funciones profesionales como la fusión de vídeos, personalización de texto, corte inteligente y más.',
        'string_163': 'Versión 1.0.0',
        'string_164': 'Desarrollado por el equipo Accessibility First',
        'string_170': 'Elegir un archivo',
        'string_171': 'Archivo seleccionado',
        'string_172': 'Compartir',
        'string_173': 'Reproducir',
        'string_174': 'Renombrar',
        'string_175': 'Eliminar'
    },
    'fr': {
        'string_1': 'Décrivez cette photo ou cette vidéo en détail. Concentrez-vous sur les éléments visuels, les personnes, les actions et les textes écrits s\'ils sont disponibles.',
        'string_2': 'Extrayez et transcrivez le texte de ce fichier audio avec une grande précision. N\'écrivez que le texte extrait sans ajout.',
        'string_145': 'Lire',
        'string_148': 'Twitter',
        'string_149': 'Cyan',
        'string_155': 'Haut',
        'string_157': 'Bas',
        'string_182': 'Opération terminée avec succès',
        'string_183': 'L\'opération a échoué',
        'string_159': 'Aide et À propos',
        'string_160': 'Comment utiliser',
        'string_161': 'À propos',
        'string_162': 'Accessible Video Editor est un outil complet d\'édition multimédia conçu en tenant compte de l\'accessibilité. Il prend en charge 11 langues et offre des fonctionnalités professionnelles telles que la fusion de vidéos, la personnalisation du texte, le découpage intelligent et plus encore.',
        'string_163': 'Version 1.0.0',
        'string_164': 'Développé par l\'équipe Accessibility First',
        'string_170': 'Choisir un fichier',
        'string_171': 'Fichier sélectionné',
        'string_172': 'Partager',
        'string_173': 'Lire',
        'string_174': 'Renommer',
        'string_175': 'Supprimer'
    },
    'fa': {
        'string_1': 'این عکس یا ویدیو را با جزئیات توصیف کنید. در صورت وجود، روی تصاویر، افراد، کنش‌ها و متون نوشتاری تمرکز کنید.',
        'string_2': 'متن این فایل صوتی را با دقت بالا استخراج و رونویسی کنید. فقط متن استخراج شده را بدون اضافات بنویسید.',
        'string_145': 'پخش',
        'string_148': 'Twitter',
        'string_149': 'فیروزه‌ای',
        'string_155': 'بالا',
        'string_157': 'پایین',
        'string_182': 'عملیات با موفقیت انجام شد',
        'string_183': 'عملیات با خطا مواجه شد',
        'string_159': 'راهنما و درباره',
        'string_160': 'نحوه استفاده',
        'string_161': 'درباره',
        'string_162': 'Accessible Video Editor یک ابزار جامع ویرایش رسانه است که با در نظر گرفتن دسترس‌پذیری طراحی شده است. از ۱۱ زبان پشتیبانی می‌کند و ویژگی‌های حرفه‌ای مانند ادغام ویدیو، سفارشی‌سازی متن، برش هوشمند و موارد دیگر را ارائه می‌دهد.',
        'string_163': 'نسخه 1.0.0',
        'string_164': 'توسعه یافته توسط تیم Accessibility First',
        'string_170': 'انتخاب فایل',
        'string_171': 'فایل انتخاب شد',
        'string_172': 'اشتراک‌گذاری',
        'string_173': 'پخش',
        'string_174': 'تغییر نام',
        'string_175': 'حذف'
    },
    'he': {
        'string_1': 'תאר תמונה או סרטון זה בפירוט. התמקד בחזותיים, באנשים, בפעולות ובטקסטים כתובים אם הם זמינים.',
        'string_2': 'חלץ ותמלל את הטקסט מקובץ שמע זה בדיוק גבוה. כתוב רק את הטקסט שחולץ ללא תוספות.',
        'string_145': 'הפעלה',
        'string_148': 'Twitter',
        'string_149': 'ציאן',
        'string_155': 'למעלה',
        'string_157': 'למטה',
        'string_182': 'הפעולה הושלמה בהצלחה',
        'string_183': 'הפעולה נכשלה',
        'string_159': 'עזרה ואודות',
        'string_160': 'כיצד להשתמש',
        'string_161': 'אודות',
        'string_162': 'Accessible Video Editor הוא כלי עריכת מדיה מקיף שתוכנן תוך התחשבות בנגישות. הוא תומך ב-11 שפות ומציע תכונות מקצועיות כגון מיזוג סרטונים, התאמה אישית של טקסט, חיתוך חכם ועוד.',
        'string_163': 'גרסה 1.0.0',
        'string_164': 'פותח על ידי צוות Accessibility First',
        'string_170': 'בחר קובץ',
        'string_171': 'הקובץ נבחר',
        'string_172': 'שתף',
        'string_173': 'הפעלה',
        'string_174': 'שנה שם',
        'string_175': 'מחק'
    },
    'ja': {
        'string_1': 'この写真またはビデオを詳しく説明してください。 ビジュアル、人物、行動、および書かれたテキスト（ある場合）に焦点を当てます。',
        'string_2': 'このオーディオファイルからテキストを高精度で抽出し、書き起こします。 追加せずに抽出したテキストのみを記述します。',
        'string_145': '再生',
        'string_148': 'Twitter',
        'string_149': 'シアン',
        'string_155': '上',
        'string_157': '下',
        'string_182': '操作が正常に完了しました',
        'string_183': '操作に失敗しました',
        'string_159': 'ヘルプと概要',
        'string_160': '使い方',
        'string_161': '概要',
        'string_162': 'Accessible Video Editorは、アクセシビリティを考慮して設計された包括的なメディア編集ツールです。11言語をサポートし、ビデオの結合、テキストのカスタマイズ、スマートカットなどのプロフェッショナルな機能を提供します。',
        'string_163': 'バージョン 1.0.0',
        'string_164': 'Accessibility Firstチームが開発',
        'string_170': 'ファイルを選択',
        'string_171': 'ファイルが選択されました',
        'string_172': '共有',
        'string_173': '再生',
        'string_174': '名前変更',
        'string_175': '削除'
    },
    'ru': {
        'string_1': 'Опишите подробно это фото или видео. Сосредоточьтесь на визуальных эффектах, людях, действиях и письменных текстах, если они доступны.',
        'string_2': 'Извлеките и расшифруйте текст из этого аудиофайла с высокой точностью. Напишите только извлеченный текст без дополнений.',
        'string_145': 'Воспроизвести',
        'string_148': 'Twitter',
        'string_149': 'Голубой',
        'string_155': 'Сверху',
        'string_157': 'Снизу',
        'string_182': 'Операция выполнена успешно',
        'string_183': 'Операция не удалась',
        'string_159': 'Помощь и О приложении',
        'string_160': 'Как пользоваться',
        'string_161': 'О приложении',
        'string_162': 'Accessible Video Editor — это комплексный инструмент для редактирования мультимедиа, разработанный с учётом доступности. Он поддерживает 11 языков и предлагает профессиональные функции, такие как объединение видео, настройка текста, умная обрезка и многое другое.',
        'string_163': 'Версия 1.0.0',
        'string_164': 'Разработано командой Accessibility First',
        'string_170': 'Выбрать файл',
        'string_171': 'Файл выбран',
        'string_172': 'Поделиться',
        'string_173': 'Воспроизвести',
        'string_174': 'Переименовать',
        'string_175': 'Удалить'
    },
    'tr': {
        'string_1': 'Bu fotoğrafı veya videoyu ayrıntılı olarak açıklayın. Varsa görsellere, kişilere, eylemlere ve yazılı metinlere odaklanın.',
        'string_2': 'Bu ses dosyasındaki metni yüksek doğrulukla çıkarın ve yazıya dökün. Yalnızca çıkarılan metni eklemeler yapmadan yazın.',
        'string_145': 'Oynat',
        'string_148': 'Twitter',
        'string_149': 'Camgöbeği',
        'string_155': 'Üst',
        'string_157': 'Alt',
        'string_182': 'İşlem başarıyla tamamlandı',
        'string_183': 'İşlem başarısız oldu',
        'string_159': 'Yardım ve Hakkında',
        'string_160': 'Nasıl Kullanılır',
        'string_161': 'Hakkında',
        'string_162': 'Accessible Video Editor, erişilebilirlik göz önünde bulundurularak tasarlanmış kapsamlı bir medya düzenleme aracıdır. 11 dili destekler ve video birleştirme, metin özelleştirme, akıllı kesim ve daha fazlası gibi profesyonel özellikler sunar.',
        'string_163': 'Sürüm 1.0.0',
        'string_164': 'Accessibility First ekibi tarafından geliştirilmiştir',
        'string_170': 'Dosya seç',
        'string_171': 'Dosya seçildi',
        'string_172': 'Paylaş',
        'string_173': 'Oynat',
        'string_174': 'Yeniden adlandır',
        'string_175': 'Sil'
    },
    'ur': {
        'string_1': 'اس تصویر یا ویڈیو کو تفصیل سے بیان کریں۔ اگر دستیاب ہو تو بصری، لوگوں، اعمال اور تحریری متن پر توجہ دیں۔',
        'string_2': 'اس آڈیو فائل سے متن کو اعلی درستگی کے ساتھ نکالیں اور نقل کریں۔ بغیر اضافے کے صرف نکالا گیا متن لکھیں۔',
        'string_145': 'چلائیں',
        'string_148': 'Twitter',
        'string_149': 'فیروزی',
        'string_155': 'اوپر',
        'string_157': 'نیچے',
        'string_182': 'عمل کامیابی سے مکمل ہو گیا',
        'string_183': 'عمل ناکام ہو گیا',
        'string_159': 'مدد اور تعارف',
        'string_160': 'استعمال کا طریقہ',
        'string_161': 'تعارف',
        'string_162': 'Accessible Video Editor ایک جامع میڈیا ایڈیٹنگ ٹول ہے جو رسائی کو مدنظر رکھتے ہوئے ڈیزائن کیا گیا ہے۔ یہ 11 زبانوں کو سپورٹ کرتا ہے اور ویڈیو ضم کرنا، ٹیکسٹ کی تخصیص، سمارٹ کٹنگ اور مزید جیسی پیشہ ورانہ خصوصیات پیش کرتا ہے۔',
        'string_163': 'ورژن 1.0.0',
        'string_164': 'Accessibility First ٹیم نے تیار کیا',
        'string_170': 'فائل منتخب کریں',
        'string_171': 'فائل منتخب ہو گئی',
        'string_172': 'شیئر کریں',
        'string_173': 'چلائیں',
        'string_174': 'نام تبدیل کریں',
        'string_175': 'حذف کریں'
    },
    'zh': {
        'string_1': '详细描述这张照片或视频。 关注视觉效果、人物、动作和书面文本（如果可用）。',
        'string_2': '高精度地提取和转录此音频文件中的文本。 仅写入提取的文本，无需添加。',
        'string_145': '播放',
        'string_148': 'Twitter',
        'string_149': '青色',
        'string_155': '顶部',
        'string_157': '底部',
        'string_182': '操作成功完成',
        'string_183': '操作失败',
        'string_159': '帮助与关于',
        'string_160': '使用方法',
        'string_161': '关于',
        'string_162': 'Accessible Video Editor 是一款综合性媒体编辑工具，在设计时充分考虑了无障碍功能。它支持11种语言，提供视频合并、文字自定义、智能剪切等专业功能。',
        'string_163': '版本 1.0.0',
        'string_164': '由 Accessibility First 团队开发',
        'string_170': '选择文件',
        'string_171': '文件已选择',
        'string_172': '分享',
        'string_173': '播放',
        'string_174': '重命名',
        'string_175': '删除'
    }
}

base_dir = r"D:\.gemini\antigravity\scratch\AccessibleVideoEditor\app\src\main\res"

for lang, trans in translations.items():
    file_path = os.path.join(base_dir, f"values-{lang}", "strings.xml")
    if not os.path.exists(file_path):
        print(f"Not found: {file_path}")
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # We will just parse the XML and replace or add elements
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    for key, value in trans.items():
        # Find element by name
        elem = root.find(f".//string[@name='{key}']")
        if elem is not None:
            elem.text = value
        else:
            # Create a new element
            new_elem = ET.Element("string", name=key)
            new_elem.text = value
            root.append(new_elem)
            
    # Write back
    tree.write(file_path, encoding="utf-8", xml_declaration=True)
    print(f"Updated: {file_path}")
