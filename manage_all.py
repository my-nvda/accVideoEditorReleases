import os
import json
import shutil
import subprocess
import time
import wx
import winsound

# Default Paths & Configuration File
RELEASES_REPO = r"D:\.gemini\antigravity\scratch\accVideoEditorReleases"
SOURCE_REPO = r"D:\.gemini\antigravity\scratch\AccessibleVideoEditor"
REPOS_CONFIG_FILE = os.path.join(RELEASES_REPO, "repos_config.json")
SCRATCH_DIR = r"D:\.gemini\antigravity\scratch"

def play_success_sound():
    try:
        winsound.MessageBeep(winsound.MB_ICONASTERISK)
    except Exception:
        pass

def play_error_sound():
    try:
        winsound.MessageBeep(winsound.MB_ICONHAND)
    except Exception:
        pass

class FileEditDialog(wx.Dialog):
    """A dialog to edit text file contents cleanly with NVDA support."""
    def __init__(self, parent, file_name, file_path):
        super().__init__(parent, title=f"تعديل ملف: {file_name}", size=(650, 450))
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.file_path = file_path
        
        sizer = wx.BoxSizer(wx.VERTICAL)
        sizer.Add(wx.StaticText(self, label=f"تعديل محتوى الملف: {file_path}"), 0, wx.ALL, 10)
        
        self.text_editor = wx.TextCtrl(
            self, 
            style=wx.TE_MULTILINE | wx.HSCROLL | wx.TE_DONTWRAP, 
            name="محرر النصوص"
        )
        sizer.Add(self.text_editor, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 10)
        
        self.load_file_content()
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ التغييرات", name="حفظ تعديلات الملف")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save)
        
        self.cancel_btn = wx.Button(self, label="إلغاء", name="إلغاء التعديل")
        self.cancel_btn.Bind(wx.EVT_BUTTON, self.on_cancel)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT, 10)
        btn_sizer.Add(self.cancel_btn, 1, wx.EXPAND | wx.RIGHT, 10)
        
        sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 15)
        self.SetSizer(sizer)
        self.Centre()

    def load_file_content(self):
        try:
            with open(self.file_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.text_editor.SetValue(content)
        except Exception as e:
            wx.MessageBox(f"فشل قراءة الملف:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            self.EndModal(wx.ID_CANCEL)

    def on_save(self, event):
        try:
            content = self.text_editor.GetValue()
            with open(self.file_path, "w", encoding="utf-8") as f:
                f.write(content)
            wx.MessageBox("تم حفظ الملف بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)
            self.EndModal(wx.ID_OK)
        except Exception as e:
            wx.MessageBox(f"فشل حفظ الملف:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_cancel(self, event):
        self.EndModal(wx.ID_CANCEL)


class AddRepoDialog(wx.Dialog):
    """A dialog to input a new GitHub repository link and clone it."""
    def __init__(self, parent):
        super().__init__(parent, title="إضافة مستودع جديد", size=(500, 250))
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        
        sizer = wx.BoxSizer(wx.VERTICAL)
        
        # Header Info
        sizer.Add(wx.StaticText(self, label="قم بإدخال تفاصيل مستودع GitHub ليتم استنساخه وإضافته للأداة:"), 0, wx.ALL, 10)
        
        grid = wx.FlexGridSizer(rows=2, cols=2, vgap=10, hgap=10)
        grid.AddGrowableCol(1, 1)
        
        # Repo Name
        grid.Add(wx.StaticText(self, label="اسم المستودع:"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.name_input = wx.TextCtrl(self, placeholder="مثال: مستودع الفيديوهات الفلاتر", name="اسم المستودع الجديد")
        grid.Add(self.name_input, 1, wx.EXPAND)
        
        # Repo URL
        grid.Add(wx.StaticText(self, label="رابط مستودع Git (HTTPS):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.url_input = wx.TextCtrl(self, placeholder="https://github.com/...", name="رابط مستودع GitHub")
        grid.Add(self.url_input, 1, wx.EXPAND)
        
        sizer.Add(grid, 0, wx.EXPAND | wx.ALL, 10)
        
        # Buttons
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.add_btn = wx.Button(self, label="إضافة واستنساخ", name="تأكيد الإضافة واستنساخ المستودع")
        self.add_btn.Bind(wx.EVT_BUTTON, self.on_add)
        
        self.cancel_btn = wx.Button(self, label="إلغاء", name="إلغاء الإضافة")
        self.cancel_btn.Bind(wx.EVT_BUTTON, self.on_cancel)
        
        btn_sizer.Add(self.add_btn, 1, wx.EXPAND | wx.LEFT, 10)
        btn_sizer.Add(self.cancel_btn, 1, wx.EXPAND | wx.RIGHT, 10)
        
        sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 15)
        self.SetSizer(sizer)
        self.Centre()
        
        self.cloned_path = None
        self.repo_name = None
        self.repo_url = None

    def on_add(self, event):
        name = self.name_input.GetValue().strip()
        url = self.url_input.GetValue().strip()
        
        if not name or not url:
            play_error_sound()
            wx.MessageBox("الرجاء تعبئة اسم المستودع ورابط الـ GitHub أولاً!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        # Format a folder name from the repo name
        safe_folder_name = "".join([c if c.isalnum() else "_" for c in name])
        target_path = os.path.join(SCRATCH_DIR, safe_folder_name)
        
        if os.path.exists(target_path):
            play_error_sound()
            wx.MessageBox("يوجد بالفعل مستودع بنفس هذا الاسم محلياً! يرجى اختيار اسم آخر.", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        wx.BeginBusyCursor()
        try:
            # git clone <url> <target_path>
            res = subprocess.run(["git", "clone", url, target_path], capture_output=True, text=True)
            wx.EndBusyCursor()
            
            if res.returncode == 0:
                self.cloned_path = target_path
                self.repo_name = name
                self.repo_url = url
                play_success_sound()
                wx.MessageBox("تم استنساخ المستودع بنجاح وإضافته لقائمة العمل!", "نجاح العملية", wx.OK | wx.ICON_INFORMATION)
                self.EndModal(wx.ID_OK)
            else:
                play_error_sound()
                wx.MessageBox(f"فشل استنساخ المستودع من GitHub:\n{res.stderr}", "خطأ في الاستنساخ", wx.OK | wx.ICON_ERROR)
        except Exception as e:
            if wx.IsBusy():
                wx.EndBusyCursor()
            play_error_sound()
            wx.MessageBox(f"حدث خطأ غير متوقع أثناء استنساخ المستودع:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_cancel(self, event):
        self.EndModal(wx.ID_CANCEL)


class EditRepoDialog(wx.Dialog):
    """A dialog to edit an existing repository's name, path, and GitHub URL."""
    def __init__(self, parent, current_name, current_path, current_url=""):
        super().__init__(parent, title="تعديل بيانات المستودع", size=(500, 300))
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        
        sizer = wx.BoxSizer(wx.VERTICAL)
        sizer.Add(wx.StaticText(self, label="قم بتعديل الاسم، مسار المجلد، أو رابط الـ GitHub للمستودع:"), 0, wx.ALL, 10)
        
        grid = wx.FlexGridSizer(rows=3, cols=2, vgap=10, hgap=10)
        grid.AddGrowableCol(1, 1)
        
        grid.Add(wx.StaticText(self, label="اسم المستودع:"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.name_input = wx.TextCtrl(self, value=current_name, name="اسم المستودع")
        grid.Add(self.name_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="مسار المجلد المحلي:"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.path_input = wx.TextCtrl(self, value=current_path, name="مسار المجلد المحلي")
        grid.Add(self.path_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="رابط Git (GitHub URL):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.url_input = wx.TextCtrl(self, value=current_url, name="رابط مستودع GitHub")
        grid.Add(self.url_input, 1, wx.EXPAND)
        
        sizer.Add(grid, 0, wx.EXPAND | wx.ALL, 10)
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ التعديلات", name="حفظ تعديلات المستودع")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save)
        
        self.cancel_btn = wx.Button(self, label="إلغاء", name="إلغاء")
        self.cancel_btn.Bind(wx.EVT_BUTTON, self.on_cancel)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT, 10)
        btn_sizer.Add(self.cancel_btn, 1, wx.EXPAND | wx.RIGHT, 10)
        
        sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 15)
        self.SetSizer(sizer)
        self.Centre()
        
        self.updated_name = None
        self.updated_path = None
        self.updated_url = None

    def on_save(self, event):
        name = self.name_input.GetValue().strip()
        path = self.path_input.GetValue().strip()
        url = self.url_input.GetValue().strip()
        
        if not name or not path or not url:
            wx.MessageBox("الرجاء إدخال الاسم، المسار، والرابط بشكل صحيح!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        if not os.path.exists(path):
            confirm = wx.MessageBox("المسار المحلي المحدد غير موجود حالياً. هل تود المتابعة وحفظه على أي حال؟", "تنبيه", wx.YES_NO | wx.ICON_WARNING)
            if confirm == wx.NO:
                return
                
        self.updated_name = name
        self.updated_path = path
        self.updated_url = url
        self.EndModal(wx.ID_OK)

    def on_cancel(self, event):
        self.EndModal(wx.ID_CANCEL)


class FeatureSelectionDialog(wx.Dialog):
    """A dialog showing all features as checkboxes, letting the user check/uncheck them for a specific ID."""
    def __init__(self, parent, device_name, checked_features):
        super().__init__(parent, title=f"الميزات المتاحة للجهاز: {device_name}", size=(450, 500))
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        
        self.feature_keys = [
            ("btnVideoEditor", "محرر الفيديو"),
            ("btnImageEditor", "محرر الصور"),
            ("btnWatermark", "إضافة علامة مائية"),
            ("btnCreateBlankImage", "إنشاء صورة فارغة"),
            ("btnVideoTrimmer", "قص الفيديو"),
            ("btnSmartCut", "القص الذكي الصامت"),
            ("btnAudioEditor", "محرر الصوت"),
            ("btnAudioStudio", "استوديو الصوت"),
            ("btnAiAnalysis", "تحليل الذكاء الاصطناعي"),
            ("btnStt", "تحويل الصوت إلى نص (STT)"),
            ("btnOcr", "استخراج النص من الصور (OCR)"),
            ("btnFastConverter", "التحويل السريع"),
            ("btnBoostVolume", "تضخيم الصوت"),
            ("btnExtractAudio", "استخراج الصوت من الفيديو"),
            ("btnCompressVideo", "ضغط الفيديو"),
            ("btnMergeVideos", "دمج الفيديوهات"),
            ("btnReverseMedia", "عكس الفيديو/الصوت"),
            ("btnSlideshowMaker", "صانع عرض الصور (Slideshow)"),
            ("btnTickerText", "شريط النصوص المتحرك"),
            ("btnBatchProcess", "المعالجة الجماعية (Batch)"),
            ("btnSpeedControl", "التحكم في السرعة"),
            ("btnNoiseReduction", "تقليل التشويش"),
            ("btnBackgroundMusic", "موسيقى خلفية"),
            ("btnAudioNormalization", "تعديل مستوى الصوت (Normalization)"),
            ("btnAiSceneInspector", "مستكشف المشاهد بالذكاء الاصطناعي"),
            ("btnAiVoiceDubbing", "الدبلجة الصوتية الذكية"),
            ("btnAudioStemSeparator", "فصل مسارات الصوت (أغنية/موسيقى)"),
            ("btnAutoShortsCreator", "صانع مقاطع Shorts تلقائياً"),
            ("btnCinematicLutShaders", "فلاتر سينمائية"),
            ("btnAiSceneAudioDescription", "وصف المشاهد صوتياً بالذكاء الاصطناعي"),
            ("btnSubtitlesOcrSrt", "استخراج ترجمة الفيديو (SRT)")
        ]
        
        sizer = wx.BoxSizer(wx.VERTICAL)
        sizer.Add(wx.StaticText(self, label="اختر الميزات المتاحة لهذا الجهاز:"), 0, wx.ALL, 10)
        
        # Scrolled window for checkboxes
        scroll_win = wx.ScrolledWindow(self, style=wx.VSCROLL)
        scroll_win.SetScrollRate(0, 20)
        scroll_sizer = wx.BoxSizer(wx.VERTICAL)
        
        self.checkboxes = {}
        for key, name in self.feature_keys:
            cb = wx.CheckBox(scroll_win, label=name, name=key)
            if key in checked_features:
                cb.SetValue(True)
            scroll_sizer.Add(cb, 0, wx.ALL, 5)
            self.checkboxes[key] = cb
            
        scroll_win.SetSizer(scroll_sizer)
        sizer.Add(scroll_win, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 10)
        
        # Action buttons
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.ok_btn = wx.Button(self, label="حفظ وموافق", name="حفظ الميزات المحددة")
        self.ok_btn.Bind(wx.EVT_BUTTON, self.on_ok)
        self.cancel_btn = wx.Button(self, label="إلغاء", name="إلغاء")
        self.cancel_btn.Bind(wx.EVT_BUTTON, self.on_cancel)
        
        btn_sizer.Add(self.ok_btn, 1, wx.EXPAND | wx.LEFT, 10)
        btn_sizer.Add(self.cancel_btn, 1, wx.EXPAND | wx.RIGHT, 10)
        
        sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 15)
        self.SetSizer(sizer)
        self.Centre()
        
        self.selected_features = []

    def on_ok(self, event):
        self.selected_features = [key for key, cb in self.checkboxes.items() if cb.GetValue()]
        self.EndModal(wx.ID_OK)

    def on_cancel(self, event):
        self.EndModal(wx.ID_CANCEL)


class DynamicSidebarPanel(wx.Panel):
    """A global sidebar that completely separates Local (on device) elements from GitHub (remote on origin) elements."""
    def __init__(self, parent, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        
        self.active_repo_provider = active_repo_provider
        self.mode = "releases_files"
        
        sizer = wx.BoxSizer(wx.VERTICAL)
        
        # 1. SECTION: Local Elements (on the device)
        self.title_top = wx.StaticText(self, label="الملفات المحلية (على جهازي):")
        font_bold = wx.Font(9, wx.FONTFAMILY_DEFAULT, wx.FONTSTYLE_NORMAL, wx.FONTWEIGHT_BOLD)
        self.title_top.SetFont(font_bold)
        sizer.Add(self.title_top, 0, wx.ALL | wx.EXPAND, 3)
        
        self.list_box_top = wx.ListBox(self, size=(240, 180), name="قائمة العناصر المحلية على جهازي")
        sizer.Add(self.list_box_top, 1, wx.EXPAND | wx.BOTTOM, 5)
        
        # Top Actions
        self.top_actions_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.edit_btn = wx.Button(self, label="تعديل", name="تعديل الملف المحدد")
        self.edit_btn.Bind(wx.EVT_BUTTON, self.on_edit_click)
        
        self.delete_local_btn = wx.Button(self, label="حذف محلياً", name="حذف العنصر المختار محلياً من الجهاز")
        self.delete_local_btn.Bind(wx.EVT_BUTTON, self.on_delete_local_click)
        
        self.top_actions_sizer.Add(self.edit_btn, 1, wx.EXPAND | wx.LEFT, 2)
        self.top_actions_sizer.Add(self.delete_local_btn, 1, wx.EXPAND | wx.RIGHT, 2)
        sizer.Add(self.top_actions_sizer, 0, wx.EXPAND | wx.BOTTOM, 10)
        
        # 2. SECTION: GitHub Elements (Remote on GitHub)
        self.title_bottom = wx.StaticText(self, label="الملفات على GitHub (السحاب):")
        self.title_bottom.SetFont(font_bold)
        sizer.Add(self.title_bottom, 0, wx.ALL | wx.EXPAND, 3)
        
        self.list_box_bottom = wx.ListBox(self, size=(240, 140), name="قائمة العناصر على GitHub")
        sizer.Add(self.list_box_bottom, 1, wx.EXPAND | wx.BOTTOM, 5)
        
        # Bottom Actions
        self.bottom_actions_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.delete_remote_btn = wx.Button(self, label="حذف من GitHub", name="حذف العنصر المختار من سيرفر GitHub")
        self.delete_remote_btn.Bind(wx.EVT_BUTTON, self.on_delete_remote_click)
        self.bottom_actions_sizer.Add(self.delete_remote_btn, 1, wx.EXPAND)
        sizer.Add(self.bottom_actions_sizer, 0, wx.EXPAND | wx.BOTTOM, 10)
        
        # 3. Global Control Buttons (Sync & Refresh)
        sync_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.pull_btn = wx.Button(self, label="جلب من GitHub", name="جلب ومزامنة التحديثات من GitHub")
        self.pull_btn.Bind(wx.EVT_BUTTON, self.on_pull_click)
        
        self.refresh_btn = wx.Button(self, label="تحديث القائمة", name="تحديث القوائم الحالية")
        self.refresh_btn.Bind(wx.EVT_BUTTON, self.on_refresh_click)
        
        sync_sizer.Add(self.pull_btn, 1, wx.EXPAND | wx.LEFT, 2)
        sync_sizer.Add(self.refresh_btn, 1, wx.EXPAND | wx.RIGHT, 2)
        sizer.Add(sync_sizer, 0, wx.EXPAND)
        
        self.SetSizer(sizer)

    def get_active_repo_path(self):
        return self.active_repo_provider.get_active_repo_path()

    def get_active_branch(self):
        repo_path = self.get_active_repo_path()
        try:
            res = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            if res.returncode == 0 and res.stdout.strip():
                return res.stdout.strip()
        except Exception:
            pass
        return "main"

    def set_mode(self, mode):
        self.mode = mode
        if mode == "releases_files":
            self.title_top.SetLabel("الملفات المحلية (على جهازي):")
            self.title_bottom.SetLabel("الملفات على GitHub (السحاب):")
            
            self.edit_btn.Show()
            self.delete_local_btn.SetLabel("حذف محلياً")
            self.delete_local_btn.SetName("حذف الملف المحدد محلياً")
            
            self.delete_remote_btn.Show()
            self.delete_remote_btn.SetLabel("حذف من GitHub")
            self.delete_remote_btn.SetName("حذف الملف المحدد نهائياً من مستودع GitHub")
            
            self.pull_btn.SetLabel("جلب من GitHub")
            self.pull_btn.SetName("جلب ومزامنة ملفات مستودع الإصدارات من GitHub")
            
        elif mode == "source_files":
            self.title_top.SetLabel("ملفات الكود المحلية (على جهازي):")
            self.title_bottom.SetLabel("ملفات الكود على GitHub (السحاب):")
            
            self.edit_btn.Show()
            self.delete_local_btn.SetLabel("حذف محلياً")
            self.delete_local_btn.SetName("حذف الملف المحدد محلياً")
            
            self.delete_remote_btn.Show()
            self.delete_remote_btn.SetLabel("حذف من GitHub")
            self.delete_remote_btn.SetName("حذف الملف البرمجي المحدد نهائياً من مستودع GitHub")
            
            self.pull_btn.SetLabel("جلب من GitHub")
            self.pull_btn.SetName("جلب ومزامنة ملفات الكود المصدري من GitHub")
            
        elif mode == "tags":
            self.title_top.SetLabel("الـ Tags المحلية (على جهازي):")
            self.title_bottom.SetLabel("الـ Tags المرفوعة (على GitHub):")
            
            self.edit_btn.Hide()
            self.delete_local_btn.SetLabel("حذف محلياً")
            self.delete_local_btn.SetName("حذف وسم الإصدار من جهازي")
            
            self.delete_remote_btn.Show()
            self.delete_remote_btn.SetLabel("حذف من GitHub")
            self.delete_remote_btn.SetName("حذف وسم الإصدار نهائياً من GitHub")
            
            self.pull_btn.SetLabel("مزامنة الـ Tags")
            self.pull_btn.SetName("جلب وتحديث التاجات من GitHub")
            
        self.Layout()
        self.refresh_data()

    def refresh_data(self):
        self.list_box_top.Clear()
        self.list_box_bottom.Clear()
        
        dir_path = self.get_active_repo_path()
        if not dir_path or not os.path.exists(dir_path):
            self.list_box_top.Append("لا يوجد مستودع نشط أو المجلد غير موجود")
            self.list_box_bottom.Append("لا يوجد مستودع نشط")
            return
            
        # Files Mode
        if self.mode in ("releases_files", "source_files"):
            # Populate TOP (Local Files)
            exclude = {".git", ".gradle", ".idea", "build", "app/build", ".kotlin", "local.properties"}
            try:
                items = sorted(os.listdir(dir_path))
                for item in items:
                    if item in exclude:
                        continue
                    path = os.path.join(dir_path, item)
                    if os.path.isdir(path):
                        self.list_box_top.Append(f"[مجلد] {item}")
                    else:
                        self.list_box_top.Append(item)
            except Exception as e:
                self.list_box_top.Append(f"خطأ: {str(e)}")
                
            # Populate BOTTOM (GitHub Remote Files tracked in origin/<branch>)
            branch = self.get_active_branch()
            try:
                res = subprocess.run(["git", "ls-tree", f"origin/{branch}"], cwd=dir_path, capture_output=True, text=True)
                if res.returncode == 0:
                    lines = [line.strip() for line in res.stdout.split("\n") if line.strip()]
                    if lines:
                        for line in lines:
                            parts_tab = line.split("\t", 1)
                            if len(parts_tab) == 2:
                                metadata, name = parts_tab
                                meta_parts = metadata.split()
                                if len(meta_parts) >= 2:
                                    item_type = meta_parts[1]
                                    if name in exclude:
                                        continue
                                    if item_type == "tree":
                                        self.list_box_bottom.Append(f"[مجلد] {name}")
                                    else:
                                        self.list_box_bottom.Append(name)
                    else:
                        self.list_box_bottom.Append(f"المستودع فارغ على GitHub (الفرع {branch})")
                else:
                    self.list_box_bottom.Append("خطأ في قراءة ملفات GitHub (اضغط جلب للمزامنة)")
            except Exception as e:
                self.list_box_bottom.Append(f"خطأ: {str(e)}")
                
        # Tags Mode
        elif self.mode == "tags":
            # Populate TOP (Local Tags)
            try:
                res = subprocess.run(["git", "tag"], cwd=dir_path, capture_output=True, text=True)
                if res.returncode == 0:
                    tags = [line.strip() for line in res.stdout.split("\n") if line.strip()]
                    if tags:
                        for tag in sorted(tags, reverse=True):
                            self.list_box_top.Append(tag)
                    else:
                        self.list_box_top.Append("لا توجد وسوم محلية")
                else:
                    self.list_box_top.Append("خطأ في قراءة التاجات المحلية")
            except Exception as e:
                self.list_box_top.Append(f"خطأ: {str(e)}")
                
            # Populate BOTTOM (GitHub Remote Tags)
            try:
                res = subprocess.run(["git", "ls-remote", "--tags", "origin"], cwd=dir_path, capture_output=True, text=True)
                if res.returncode == 0:
                    remote_tags = set()
                    for line in res.stdout.split("\n"):
                        if "refs/tags/" in line:
                            tag = line.split("refs/tags/")[1].strip()
                            if tag.endswith("^{}"):
                                tag = tag[:-3]
                            remote_tags.add(tag)
                    if remote_tags:
                        for tag in sorted(list(remote_tags), reverse=True):
                            self.list_box_bottom.Append(tag)
                    else:
                        self.list_box_bottom.Append("لا توجد وسوم على GitHub")
                else:
                    self.list_box_bottom.Append("خطأ في جلب التاجات من السحاب")
            except Exception as e:
                self.list_box_bottom.Append(f"خطأ: {str(e)}")

    def on_refresh_click(self, event):
        self.refresh_data()

    def on_pull_click(self, event):
        dir_path = self.get_active_repo_path()
        if not dir_path or not os.path.exists(dir_path):
            return
            
        wx.BeginBusyCursor()
        try:
            if self.mode in ("releases_files", "source_files"):
                res = subprocess.run(["git", "pull"], cwd=dir_path, capture_output=True, text=True)
                if res.returncode == 0:
                    wx.EndBusyCursor()
                    wx.MessageBox("تم مزامنة وجلب أحدث الملفات للمستودع النشط بنجاح من GitHub!", "تمت المزامنة", wx.OK | wx.ICON_INFORMATION)
                else:
                    wx.EndBusyCursor()
                    wx.MessageBox(f"فشل المزامنة مع GitHub:\n{res.stderr}", "خطأ", wx.OK | wx.ICON_ERROR)
                    
            elif self.mode == "tags":
                res = subprocess.run(["git", "fetch", "--tags"], cwd=dir_path, capture_output=True, text=True)
                if res.returncode == 0:
                    wx.EndBusyCursor()
                    wx.MessageBox("تم جلب وتحديث جميع وسوم الإصدارات (Tags) للمستودع النشط من GitHub!", "تمت المزامنة", wx.OK | wx.ICON_INFORMATION)
                else:
                    wx.EndBusyCursor()
                    wx.MessageBox(f"فشل جلب التاجات من GitHub:\n{res.stderr}", "خطأ", wx.OK | wx.ICON_ERROR)
            
            self.refresh_data()
        except Exception as e:
            if wx.IsBusy():
                wx.EndBusyCursor()
            wx.MessageBox(f"حدث خطأ غير متوقع أثناء المزامنة:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_edit_click(self, event):
        if self.mode == "tags":
            return
            
        selection = self.list_box_top.GetSelection()
        if selection == wx.NOT_FOUND:
            wx.MessageBox("الرجاء اختيار ملف من القائمة المحلية أولاً لتعديله!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        selected_text = self.list_box_top.GetString(selection)
        if selected_text.startswith("[مجلد] "):
            wx.MessageBox("لا يمكن تعديل المجلدات كملفات نصية!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        dir_path = self.get_active_repo_path()
        full_path = os.path.join(dir_path, selected_text)
        
        text_extensions = {
            ".json", ".xml", ".txt", ".md", ".kt", ".java", ".gradle", 
            ".properties", ".py", ".bat", ".sh", ".gitignore"
        }
        ext = os.path.splitext(selected_text)[1].lower()
        if ext not in text_extensions:
            wx.MessageBox("هذا الملف قد يكون ملفاً ثنائياً (مثل APK أو صور) ولا يمكن تعديله كصيغة نصية!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        with FileEditDialog(self, selected_text, full_path) as dlg:
            dlg.ShowModal()

    def on_delete_local_click(self, event):
        selection = self.list_box_top.GetSelection()
        if selection == wx.NOT_FOUND:
            wx.MessageBox("الرجاء اختيار عنصر من القائمة المحلية لحذفه من الجهاز!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        selected_text = self.list_box_top.GetString(selection)
        dir_path = self.get_active_repo_path()
        
        if self.mode == "tags":
            if selected_text in ("لا توجد وسوم محلية", "خطأ في قراءة التاجات المحلية"):
                return
                
            confirm = wx.MessageBox(
                f"هل أنت متأكد من حذف الوسم ({selected_text}) محلياً من جهازك فقط؟ (لن يؤثر ذلك على موقع GitHub)",
                "تأكيد حذف محلي",
                wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
            )
            if confirm == wx.YES:
                try:
                    subprocess.run(["git", "tag", "-d", selected_text], cwd=dir_path, check=True, capture_output=True)
                    wx.MessageBox("تم حذف التاج محلياً من جهازك بنجاح!", "تم الحذف", wx.OK | wx.ICON_INFORMATION)
                    self.refresh_data()
                except Exception as e:
                    wx.MessageBox(f"فشل الحذف المحلي:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
                    
        else: # Files Mode
            is_dir = selected_text.startswith("[مجلد] ")
            name = selected_text[7:] if is_dir else selected_text
            full_path = os.path.join(dir_path, name)
            
            type_str = "المجلد" if is_dir else "الملف"
            confirm = wx.MessageBox(
                f"هل أنت متأكد من حذف {type_str} ({name}) نهائياً من جهازك؟", 
                "تأكيد حذف ملف", 
                wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
            )
            
            if confirm == wx.YES:
                try:
                    if is_dir:
                        shutil.rmtree(full_path)
                    else:
                        os.remove(full_path)
                    wx.MessageBox(f"تم حذف {type_str} بنجاح من جهازك!", "تم الحذف", wx.OK | wx.ICON_INFORMATION)
                    self.refresh_data()
                except Exception as e:
                    wx.MessageBox(f"فشل الحذف:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_delete_remote_click(self, event):
        dir_path = self.get_active_repo_path()
        selection = self.list_box_bottom.GetSelection()
        if selection == wx.NOT_FOUND:
            wx.MessageBox("الرجاء اختيار عنصر من قائمة GitHub لحذفه من السحاب!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        selected_text = self.list_box_bottom.GetString(selection)
        
        # Tags deletion
        if self.mode == "tags":
            if selected_text in ("لا توجد وسوم على GitHub", "خطأ في جلب التاجات من السحاب"):
                return
                
            confirm = wx.MessageBox(
                f"تحذير هام جداً:\nهل أنت متأكد تماماً من رغبتك في حذف وسم الإصدار ({selected_text}) نهائياً من مستودع GitHub على السحاب؟",
                "تحذير: تأكيد الحذف من GitHub",
                wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
            )
            
            if confirm == wx.YES:
                wx.BeginBusyCursor()
                try:
                    res = subprocess.run(["git", "push", "origin", "--delete", selected_text], cwd=dir_path, capture_output=True, text=True)
                    wx.EndBusyCursor()
                    if res.returncode == 0:
                        wx.MessageBox(f"تم حذف الوسم ({selected_text}) نهائياً من GitHub بنجاح!", "نجاح الحذف", wx.OK | wx.ICON_INFORMATION)
                        self.refresh_data()
                    else:
                        wx.MessageBox(f"فشل حذف الوسم من GitHub:\n{res.stderr}", "خطأ", wx.OK | wx.ICON_ERROR)
                except Exception as e:
                    if wx.IsBusy():
                        wx.EndBusyCursor()
                    wx.MessageBox(f"حدث خطأ غير متوقع:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
                    
        # Files deletion from GitHub
        else:
            if selected_text in ("المستودع فارغ على GitHub", "خطأ في قراءة ملفات GitHub (تأكد من عمل جلب أولاً)"):
                return
                
            is_dir = selected_text.startswith("[مجلد] ")
            name = selected_text[7:] if is_dir else selected_text
            
            type_str = "المجلد" if is_dir else "الملف"
            confirm = wx.MessageBox(
                f"تحذير هام جداً:\nهل أنت متأكد من رغبتك في حذف {type_str} ({name}) نهائياً من مستودع GitHub على السحاب؟\nسيقوم البرنامج بحذفه برمجياً ورفع التغيير فوراً.",
                "تحذير: تأكيد حذف ملف من GitHub",
                wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
            )
            
            if confirm == wx.YES:
                wx.BeginBusyCursor()
                try:
                    # git rm -r <file>
                    res_rm = subprocess.run(["git", "rm", "-r", name], cwd=dir_path, capture_output=True, text=True)
                    if res_rm.returncode != 0:
                        wx.EndBusyCursor()
                        wx.MessageBox(f"فشل حذف الملف محلياً عبر Git:\n{res_rm.stderr}", "خطأ", wx.OK | wx.ICON_ERROR)
                        return
                        
                    # git commit
                    subprocess.run(["git", "commit", "-m", f"Delete {name} via Release Manager GUI"], cwd=dir_path, capture_output=True)
                    
                    # git push
                    branch = self.get_active_branch()
                    res_push = subprocess.run(["git", "push", "origin", branch], cwd=dir_path, capture_output=True, text=True)
                    wx.EndBusyCursor()
                    
                    if res_push.returncode == 0:
                        wx.MessageBox(f"تم حذف {type_str} ({name}) ورفعه وإزالته من GitHub بنجاح!", "نجاح الحذف من السحاب", wx.OK | wx.ICON_INFORMATION)
                        self.refresh_data()
                    else:
                        wx.MessageBox(f"فشل رفع عملية الحذف لـ GitHub:\n{res_push.stderr}", "خطأ في الرفع", wx.OK | wx.ICON_ERROR)
                except Exception as e:
                    if wx.IsBusy():
                        wx.EndBusyCursor()
                    wx.MessageBox(f"حدث خطأ غير متوقع:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


ALL_FEATURES = [
    ("btnVideoEditor", "محرر الفيديو"),
    ("btnImageEditor", "محرر الصور"),
    ("btnWatermark", "إضافة علامة مائية"),
    ("btnCreateBlankImage", "إنشاء صورة فارغة"),
    ("btnVideoTrimmer", "قص الفيديو"),
    ("btnSmartCut", "القص الذكي الصامت"),
    ("btnAudioEditor", "محرر الصوت"),
    ("btnAudioStudio", "استوديو الصوت"),
    ("btnAiAnalysis", "تحليل الذكاء الاصطناعي"),
    ("btnStt", "تحويل الصوت إلى نص (STT)"),
    ("btnOcr", "استخراج النص من الصور (OCR)"),
    ("btnFastConverter", "التحويل السريع"),
    ("btnBoostVolume", "تضخيم الصوت"),
    ("btnExtractAudio", "استخراج الصوت من الفيديو"),
    ("btnCompressVideo", "ضغط الفيديو"),
    ("btnMergeVideos", "دمج الفيديوهات"),
    ("btnReverseMedia", "عكس الفيديو/الصوت"),
    ("btnSlideshowMaker", "صانع عرض الصور (Slideshow)"),
    ("btnTickerText", "شريط النصوص المتحرك"),
    ("btnBatchProcess", "المعالجة الجماعية (Batch)"),
    ("btnSpeedControl", "التحكم في السرعة"),
    ("btnNoiseReduction", "تقليل التشويش"),
    ("btnBackgroundMusic", "موسيقى خلفية"),
    ("btnAudioNormalization", "تعديل مستوى الصوت (Normalization)"),
    ("btnAiSceneInspector", "مستكشف المشاهد بالذكاء الاصطناعي"),
    ("btnAiVoiceDubbing", "الدبلجة الصوتية الذكية"),
    ("btnAudioStemSeparator", "فصل مسارات الصوت (أغنية/موسيقى)"),
    ("btnAutoShortsCreator", "صانع مقاطع Shorts تلقائياً"),
    ("btnCinematicLutShaders", "فلاتر سينمائية"),
    ("btnAiSceneAudioDescription", "وصف المشاهد صوتياً بالذكاء الاصطناعي"),
    ("btnSubtitlesOcrSrt", "استخراج ترجمة الفيديو (SRT)")
]

class CloudConfigTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        self.rows = []
        
        form_sizer = wx.BoxSizer(wx.VERTICAL)
        
        # GitHub Telemetry Config fields at the top
        github_box = wx.StaticBox(self, label="إعدادات رفع إحصائيات وتقارير الانهيارات")
        github_sizer = wx.StaticBoxSizer(github_box, wx.HORIZONTAL)
        
        token_label = wx.StaticText(self, label="رمز وصول GitHub (Token):")
        self.github_token_input = wx.TextCtrl(self, style=wx.TE_PASSWORD, name="رمز وصول GitHub", size=(180, -1))
        
        repo_label = wx.StaticText(self, label=" مستودع الرفع (owner/repo):")
        self.github_repo_input = wx.TextCtrl(self, name="مستودع الرفع للتقارير", size=(150, -1))
        
        github_sizer.Add(token_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        github_sizer.Add(self.github_token_input, 1, wx.EXPAND | wx.RIGHT, 15)
        github_sizer.Add(repo_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        github_sizer.Add(self.github_repo_input, 1, wx.EXPAND)
        
        form_sizer.Add(github_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.scroll_win = wx.ScrolledWindow(self)
        self.scroll_win.SetScrollRate(0, 20)
        self.scroll_sizer = wx.BoxSizer(wx.VERTICAL)
        self.scroll_win.SetSizer(self.scroll_sizer)
        
        form_sizer.Add(self.scroll_win, 1, wx.EXPAND | wx.ALL, 5)
        
        self.add_btn = wx.Button(self, label="إضافة جهاز جديد +", name="إضافة جهاز جديد")
        self.add_btn.Bind(wx.EVT_BUTTON, self.on_add_device)
        form_sizer.Add(self.add_btn, 0, wx.EXPAND | wx.ALL, 10)
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ التغييرات محلياً", name="حفظ الإعدادات محلياً")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save_local)
        
        self.upload_btn = wx.Button(self, label="حفظ ورفع التغييرات إلى السحابة", name="رفع الإعدادات للسحابة")
        self.upload_btn.Bind(wx.EVT_BUTTON, self.on_upload_cloud)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        btn_sizer.Add(self.upload_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        form_sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(form_sizer)
        self.load_config()

    def get_config_file_path(self):
        repo_path = self.active_repo_provider.get_active_repo_path()
        if repo_path:
            path = os.path.join(repo_path, "cloud_config.json")
            if os.path.exists(path):
                return path
        for r in getattr(self.active_repo_provider, "registered_repos", []):
            alt_path = os.path.join(r["path"], "cloud_config.json")
            if os.path.exists(alt_path):
                return alt_path
        return os.path.join(repo_path, "cloud_config.json") if repo_path else ""


    def clear_rows(self):
        self.scroll_sizer.Clear(True)
        self.rows = []
        self.scroll_win.Layout()

    def load_config(self):
        self.clear_rows()
        config_path = self.get_config_file_path()
        
        try:
            if not os.path.exists(config_path):
                self.add_row()
                return

            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)

            raw_token = data.get("github_token", "")
            decoded_token = raw_token
            if raw_token and not raw_token.startswith("ghp_"):
                import base64
                try:
                    reversed_str = base64.b64decode(raw_token.encode("utf-8")).decode("utf-8")
                    decoded_token = reversed_str[::-1]
                except Exception:
                    pass
            self.github_token_input.SetValue(decoded_token)
            self.github_repo_input.SetValue(data.get("github_repo", ""))

            whitelist = data.get("whitelistedDevices", {})
            device_names = data.get("deviceNames", {})
            all_device_ids = set(device_names.keys())
            
            for key, ids in whitelist.items():
                for uid in ids:
                    all_device_ids.add(uid)

            for uid in sorted(list(all_device_ids)):
                name = device_names.get(uid, "")
                checked = []
                for key, ids in whitelist.items():
                    if uid in ids:
                        checked.append(key)
                self.add_row(name, uid, checked)

            if not all_device_ids:
                self.add_row()

        except Exception as e:
            wx.MessageBox(f"فشل تحميل إعدادات السحابة:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            self.add_row()

    def add_row(self, name_val="", id_val="", checked_features=None, focus=False):
        if checked_features is None:
            # Select all features by default for new rows
            checked_features = [k for k, n in ALL_FEATURES]
            
        idx = len(self.rows) + 1
        row_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        name_label = f"اسم صاحب الجهاز رقم {idx}"
        name_txt = wx.StaticText(self.scroll_win, label=name_label + ":")
        name_input = wx.TextCtrl(self.scroll_win, value=name_val, name=name_label, size=(110, -1))
        
        id_label = f"معرّف الجهاز رقم {idx}"
        id_txt = wx.StaticText(self.scroll_win, label=id_label + ":")
        id_input = wx.TextCtrl(self.scroll_win, value=id_val, name=id_label, size=(160, -1))
        
        btn_features = wx.Button(self.scroll_win, label=f"تحديد الميزات ({len(checked_features)})", name=f"تحديد ميزات الجهاز رقم {idx}")
        
        btn_label = f"إزالة الجهاز رقم {idx}"
        delete_btn = wx.Button(self.scroll_win, label="إزالة", name=btn_label)
        
        row_sizer.Add(name_txt, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        row_sizer.Add(name_input, 1, wx.EXPAND | wx.RIGHT, 10)
        row_sizer.Add(id_txt, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        row_sizer.Add(id_input, 1, wx.EXPAND | wx.RIGHT, 10)
        row_sizer.Add(btn_features, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        row_sizer.Add(delete_btn, 0, wx.ALIGN_CENTER_VERTICAL)
        
        self.scroll_sizer.Add(row_sizer, 0, wx.EXPAND | wx.ALL, 6)
        
        row_data = {
            "sizer": row_sizer, "name_txt": name_txt, "name_input": name_input,
            "id_txt": id_txt, "id_input": id_input, "btn_features": btn_features,
            "delete_btn": delete_btn, "checked_features": checked_features
        }
        self.rows.append(row_data)
        
        btn_features.Bind(wx.EVT_BUTTON, lambda e: self.on_select_features(row_data))
        delete_btn.Bind(wx.EVT_BUTTON, lambda e: self.on_delete_row(row_data))
        self.scroll_win.Layout()
        self.Layout()
        
        if focus and not name_val:
            wx.CallAfter(name_input.SetFocus)

    def on_select_features(self, row_data):
        name = row_data["name_input"].GetValue().strip() or "جهاز غير مسمى"
        with FeatureSelectionDialog(self, name, row_data["checked_features"]) as dlg:
            if dlg.ShowModal() == wx.ID_OK:
                row_data["checked_features"] = dlg.selected_features
                row_data["btn_features"].SetLabel(f"تحديد الميزات ({len(dlg.selected_features)})")

    def on_delete_row(self, row_data):
        if row_data not in self.rows:
            return
        wx.CallAfter(self.really_delete_row, row_data)

    def really_delete_row(self, row_data):
        if row_data not in self.rows:
            return
        controls = ["name_txt", "name_input", "id_txt", "id_input", "btn_features", "delete_btn"]
        for ctrl_name in controls:
            if ctrl_name in row_data and row_data[ctrl_name]:
                try:
                    row_data[ctrl_name].Destroy()
                except Exception:
                    pass
        self.rows.remove(row_data)
        self.scroll_win.Layout()
        self.refresh_row_indices()

    def refresh_row_indices(self):
        for i, row in enumerate(self.rows):
            idx = i + 1
            row["name_txt"].SetLabel(f"اسم صاحب الجهاز رقم {idx}:")
            row["name_input"].SetName(f"اسم صاحب الجهاز رقم {idx}")
            row["id_txt"].SetLabel(f"معرّف الجهاز رقم {idx}:")
            row["id_input"].SetName(f"معرّف الجهاز رقم {idx}")
            row["btn_features"].SetName(f"تحديد ميزات الجهاز رقم {idx}")
            row["btn_features"].SetLabel(f"تحديد الميزات ({len(row['checked_features'])})")
            row["delete_btn"].SetName(f"إزالة الجهاز رقم {idx}")
        self.scroll_win.Layout()

    def on_add_device(self, event):
        self.add_row(focus=True)

    def get_data(self):
        devices = []
        id_to_name_map = {}
        for row in self.rows:
            name = row["name_input"].GetValue().strip()
            uid = row["id_input"].GetValue().strip()
            if name and uid:
                devices.append({
                    "id": uid,
                    "name": name,
                    "features": row["checked_features"]
                })
                id_to_name_map[uid] = name
        return devices, id_to_name_map

    def save_local_action(self):
        config_path = self.get_config_file_path()
        try:
            devices, id_to_name_map = self.get_data()
            if not os.path.exists(config_path):
                data = {"whitelistedDevices": {}}
            else:
                with open(config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            
            # Rebuild whitelistedDevices
            whitelist = {}
            for key, name in ALL_FEATURES:
                whitelist[key] = []
                
            for dev in devices:
                uid = dev["id"]
                for feat in dev["features"]:
                    if feat in whitelist:
                        whitelist[feat].append(uid)
            
            data["whitelistedDevices"] = whitelist
            data["deviceNames"] = id_to_name_map
            
            token_val = self.github_token_input.GetValue().strip()
            token_encoded = token_val
            if token_val.startswith("ghp_"):
                import base64
                try:
                    reversed_token = token_val[::-1]
                    token_encoded = base64.b64encode(reversed_token.encode("utf-8")).decode("utf-8")
                except Exception:
                    pass
            repo_val = self.github_repo_input.GetValue().strip()
            data["github_token"] = token_encoded
            data["github_repo"] = repo_val
            data["telemetryConfig"] = {
                "token": token_encoded,
                "repo": repo_val
            }

            with open(config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            self.sidebar.refresh_data()
            return True
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ المحلي:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            return False

    def on_save_local(self, event):
        if self.save_local_action():
            wx.MessageBox("تم حفظ الإعدادات السحابية محلياً بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)

    def on_upload_cloud(self, event):
        if not self.save_local_action():
            return
        
        config_path = self.get_config_file_path()
        if not config_path:
            return
        repo_path = os.path.dirname(config_path)
        try:
            subprocess.run(["git", "add", "cloud_config.json"], cwd=repo_path, check=True, capture_output=True)
            subprocess.run(["git", "commit", "-m", "Update cloud config via wxPython GUI"], cwd=repo_path, capture_output=True)
            
            # git push origin <branch>
            res_branch = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            branch = res_branch.stdout.strip() if res_branch.returncode == 0 else "main"
            
            subprocess.run(["git", "push", "origin", branch], cwd=repo_path, check=True, capture_output=True)
            self.sidebar.refresh_data()
            wx.MessageBox("تم الرفع والتطبيق على السحابة بنجاح!", "نجاح الرفع", wx.OK | wx.ICON_INFORMATION)
        except subprocess.CalledProcessError as e:
            err = e.stderr.decode("utf-8", errors="ignore")
            if "nothing to commit" in err or "clean" in err:
                wx.MessageBox("لا توجد تغييرات لرفعها، السحابة متطابقة بالفعل.", "تنبيه", wx.OK | wx.ICON_INFORMATION)
            else:
                wx.MessageBox(f"خطأ في الاتصال بالرفع:\n{err}", "فشل الرفع", wx.OK | wx.ICON_ERROR)


class AppUpdateTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        
        form_sizer = wx.BoxSizer(wx.VERTICAL)
        
        grid = wx.FlexGridSizer(rows=4, cols=2, vgap=10, hgap=10)
        grid.AddGrowableCol(1, 1)
        
        grid.Add(wx.StaticText(self, label="رقم الإصدار (Version Code):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.vc_input = wx.TextCtrl(self, name="رقم الإصدار")
        grid.Add(self.vc_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="اسم الإصدار (Version Name):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.vn_input = wx.TextCtrl(self, name="اسم الإصدار")
        grid.Add(self.vn_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="رابط تحميل ملف APK:"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.url_input = wx.TextCtrl(self, name="رابط تحميل ملف APK")
        grid.Add(self.url_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="تحديث ملف APK المحلي (اختياري):"), 0, wx.ALIGN_CENTER_VERTICAL)
        file_picker_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.apk_path_input = wx.TextCtrl(self, style=wx.TE_READONLY, name="مسار ملف APK المختار")
        self.pick_apk_btn = wx.Button(self, label="اختر ملف APK...", name="اختيار ملف APK")
        self.pick_apk_btn.Bind(wx.EVT_BUTTON, self.on_pick_apk)
        file_picker_sizer.Add(self.apk_path_input, 1, wx.EXPAND | wx.LEFT, 5)
        file_picker_sizer.Add(self.pick_apk_btn, 0)
        grid.Add(file_picker_sizer, 1, wx.EXPAND)
        
        form_sizer.Add(grid, 0, wx.EXPAND | wx.ALL, 10)
        
        form_sizer.Add(wx.StaticText(self, label="ملاحظات الإصدار الجديد (Release Notes):"), 0, wx.LEFT | wx.RIGHT | wx.TOP, 10)
        self.notes_input = wx.TextCtrl(self, style=wx.TE_MULTILINE, size=(-1, 160), name="ملاحظات الإصدار الجديد")
        form_sizer.Add(self.notes_input, 1, wx.EXPAND | wx.ALL, 10)
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ محلياً", name="حفظ التحديث محلياً")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save_local)
        
        self.upload_btn = wx.Button(self, label="حفظ ورفع التحديث إلى السحابة", name="رفع التحديث للسحابة")
        self.upload_btn.Bind(wx.EVT_BUTTON, self.on_upload_cloud)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        btn_sizer.Add(self.upload_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        form_sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(form_sizer)
        self.load_update_info()

    def get_update_file_path(self):
        repo_path = self.active_repo_provider.get_active_repo_path()
        if repo_path:
            path = os.path.join(repo_path, "update.json")
            if os.path.exists(path):
                return path
        for r in getattr(self.active_repo_provider, "registered_repos", []):
            alt_path = os.path.join(r["path"], "update.json")
            if os.path.exists(alt_path):
                return alt_path
        return os.path.join(repo_path, "update.json") if repo_path else ""


    def load_update_info(self):
        self.vc_input.SetValue("")
        self.vn_input.SetValue("")
        self.url_input.SetValue("")
        self.notes_input.SetValue("")
        
        update_path = self.get_update_file_path()
        
        try:
            if not os.path.exists(update_path):
                return
            with open(update_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            
            self.vc_input.SetValue(str(data.get("versionCode", "")))
            self.vn_input.SetValue(data.get("versionName", ""))
            self.url_input.SetValue(data.get("downloadUrl", ""))
            
            notes = data.get("releaseNotes", "")
            notes = notes.replace("\\n", "\n")
            self.notes_input.SetValue(notes)
        except Exception as e:
            wx.MessageBox(f"تنبيه: لا يوجد ملف تحديث صالح في المستودع النشط حالياً:\n{str(e)}", "تنبيه", wx.OK | wx.ICON_WARNING)

    def on_pick_apk(self, event):
        with wx.FileDialog(self, "اختر ملف APK المجمّع للتحديث", wildcard="APK files (*.apk)|*.apk", style=wx.FD_OPEN | wx.FD_FILE_MUST_EXIST) as fd:
            if fd.ShowModal() == wx.ID_OK:
                self.apk_path_input.SetValue(fd.GetPath())

    def save_local_action(self):
        update_path = self.get_update_file_path()
        repo_path = self.active_repo_provider.get_active_repo_path()
        
        try:
            vc = int(self.vc_input.GetValue().strip())
            vn = self.vn_input.GetValue().strip()
            url = self.url_input.GetValue().strip()
            notes = self.notes_input.GetValue()
            
            escaped_notes = notes.replace("\n", "\\n")
            
            data = {
                "versionCode": vc,
                "versionName": vn,
                "downloadUrl": url,
                "releaseNotes": escaped_notes
            }
            
            with open(update_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            chosen_apk = self.apk_path_input.GetValue()
            if chosen_apk and os.path.exists(chosen_apk):
                dest_apk = os.path.join(repo_path, "app-release.apk")
                shutil.copy2(chosen_apk, dest_apk)
                self.apk_path_input.SetValue("")
                
            self.sidebar.refresh_data()
            return True
        except ValueError:
            wx.MessageBox("رقم الإصدار (Version Code) يجب أن يكون عدداً صحيحاً فقط!", "خطأ مدخلات", wx.OK | wx.ICON_WARNING)
            return False
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            return False

    def on_save_local(self, event):
        if self.save_local_action():
            wx.MessageBox("تم حفظ معلومات التحديث وملف APK محلياً!", "نجاح", wx.OK | wx.ICON_INFORMATION)

    def on_upload_cloud(self, event):
        if not self.save_local_action():
            return
        
        update_path = self.get_update_file_path()
        if not update_path:
            return
        repo_path = os.path.dirname(update_path)
        try:
            subprocess.run(["git", "add", "update.json"], cwd=repo_path, check=True, capture_output=True)
            if os.path.exists(os.path.join(repo_path, "app-release.apk")):
                subprocess.run(["git", "add", "app-release.apk"], cwd=repo_path, capture_output=True)
                
            subprocess.run(["git", "commit", "-m", f"Publish update configuration and APK for version {self.vn_input.GetValue()}"], cwd=repo_path, capture_output=True)
            
            res_branch = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            branch = res_branch.stdout.strip() if res_branch.returncode == 0 else "main"
            
            subprocess.run(["git", "push", "origin", branch], cwd=repo_path, check=True, capture_output=True)
            self.sidebar.refresh_data()
            wx.MessageBox("تم رفع التحديث والـ APK وتطبيقهما على السحاب بنجاح!", "نجاح الرفع", wx.OK | wx.ICON_INFORMATION)
        except subprocess.CalledProcessError as e:
            err = e.stderr.decode("utf-8", errors="ignore")
            if "nothing to commit" in err or "clean" in err:
                wx.MessageBox("لا توجد تغييرات جديدة لرفعها.", "تنبيه", wx.OK | wx.ICON_INFORMATION)
            else:
                wx.MessageBox(f"فشل الرفع إلى GitHub:\n{err}", "خطأ في الرفع", wx.OK | wx.ICON_ERROR)


class GitTagsTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        
        form_sizer = wx.BoxSizer(wx.VERTICAL)
        
        form_sizer.Add(wx.StaticText(self, label="إنشاء ورفع وسم إصدار جديد (Git Tag) للمستودع النشط:"), 0, wx.ALL, 10)
        
        grid = wx.FlexGridSizer(rows=2, cols=2, vgap=10, hgap=10)
        grid.AddGrowableCol(1, 1)
        
        grid.Add(wx.StaticText(self, label="اسم الوسم (مثال v2.9.5):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.tag_input = wx.TextCtrl(self, name="اسم وسم الإصدار الجديد")
        grid.Add(self.tag_input, 1, wx.EXPAND)
        
        grid.Add(wx.StaticText(self, label="وصف الإصدار (أو رسالة التاج):"), 0, wx.ALIGN_CENTER_VERTICAL)
        self.tag_msg_input = wx.TextCtrl(self, name="وصف وسم الإصدار الجديد")
        grid.Add(self.tag_msg_input, 1, wx.EXPAND)
        
        form_sizer.Add(grid, 0, wx.EXPAND | wx.LEFT | wx.RIGHT | wx.BOTTOM, 10)
        
        self.tag_btn = wx.Button(self, label="إنشاء ورفع الـ Tag للسحابة", name="إنشاء ورفع التاج")
        self.tag_btn.Bind(wx.EVT_BUTTON, self.on_create_tag)
        form_sizer.Add(self.tag_btn, 0, wx.EXPAND | wx.ALL, 10)
        
        form_sizer.Add(wx.StaticText(self, label="سجل مخرجات العملية:"), 0, wx.LEFT | wx.RIGHT | wx.TOP, 10)
        self.log_output = wx.TextCtrl(self, style=wx.TE_MULTILINE | wx.TE_READONLY, size=(-1, 160), name="سجل مخرجات عملية التاج")
        form_sizer.Add(self.log_output, 1, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(form_sizer)

    def log(self, msg):
        self.log_output.AppendText(msg + "\n")

    def on_create_tag(self, event):
        repo_path = self.active_repo_provider.get_active_repo_path()
        tag = self.tag_input.GetValue().strip()
        msg = self.tag_msg_input.GetValue().strip()
        
        if not tag:
            wx.MessageBox("يجب إدخال اسم الوسم (Tag Name) أولاً!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        if not msg:
            msg = f"Release {tag}"
            
        self.log_output.Clear()
        self.log(f"جاري إنشاء وسم إصدار محلي {tag}...")
        
        try:
            res_tag = subprocess.run(["git", "tag", "-a", tag, "-m", msg], cwd=repo_path, capture_output=True, text=True)
            if res_tag.returncode != 0:
                self.log(f"خطأ في إنشاء التاج محلياً:\n{res_tag.stderr}")
                wx.MessageBox("فشل إنشاء التاج محلياً. تأكد أنه غير موجود مسبقاً.", "خطأ", wx.OK | wx.ICON_ERROR)
                return
                
            self.log("تم إنشاء التاج محلياً بنجاح.")
            self.log("جاري الرفع إلى GitHub...")
            
            res_push = subprocess.run(["git", "push", "origin", tag], cwd=repo_path, capture_output=True, text=True)
            self.log(res_push.stdout)
            self.log(res_push.stderr)
            
            if res_push.returncode == 0:
                self.log("تم رفع التاج الجديد بنجاح إلى السحابة!")
                self.sidebar.refresh_data()
                wx.MessageBox("تم إنشاء ورفع التاج (Tag) بنجاح إلى GitHub!", "نجاح العملية", wx.OK | wx.ICON_INFORMATION)
                self.tag_input.SetValue("")
                self.tag_msg_input.SetValue("")
            else:
                wx.MessageBox("فشل رفع التاج إلى السحابة. تحقق من اتصال الإنترنت.", "فشل الرفع", wx.OK | wx.ICON_ERROR)
                
        except Exception as e:
            self.log(f"حدث خطأ غير متوقع: {str(e)}")
            wx.MessageBox(f"خطأ: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


class SourceCodeTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        
        form_sizer = wx.BoxSizer(wx.VERTICAL)
        
        form_sizer.Add(wx.StaticText(self, label="رفع وحفظ التعديلات في الكود المصدري للمستودع النشط:"), 0, wx.ALL, 10)
        
        form_sizer.Add(wx.StaticText(self, label="اكتب رسالة التحديث (Commit Message) تصف التعديلات:"), 0, wx.LEFT | wx.RIGHT | wx.TOP, 10)
        self.commit_input = wx.TextCtrl(self, name="رسالة تحديث الكود المصدري")
        self.commit_input.SetHint("مثال: إصلاح أخطاء الترجمة في الشاشات المختلفة")
        form_sizer.Add(self.commit_input, 0, wx.EXPAND | wx.ALL, 10)
        
        self.push_btn = wx.Button(self, label="رفع وحفظ التعديلات إلى GitHub", name="رفع التعديلات")
        self.push_btn.Bind(wx.EVT_BUTTON, self.on_push_source)
        form_sizer.Add(self.push_btn, 0, wx.EXPAND | wx.ALL, 10)
        
        form_sizer.Add(wx.StaticText(self, label="سجل عمليات الرفع:"), 0, wx.LEFT | wx.RIGHT | wx.TOP, 10)
        self.log_output = wx.TextCtrl(self, style=wx.TE_MULTILINE | wx.TE_READONLY, size=(-1, 180), name="سجل مخرجات رفع الكود")
        form_sizer.Add(self.log_output, 1, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(form_sizer)

    def log(self, msg):
        self.log_output.AppendText(msg + "\n")

    def on_push_source(self, event):
        repo_path = self.active_repo_provider.get_active_repo_path()
        msg = self.commit_input.GetValue().strip()
        if not msg:
            wx.MessageBox("يجب كتابة رسالة التحديث (Commit Message) قبل الرفع للحفاظ على تنظيم المستودع!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        self.log_output.Clear()
        self.log("جاري رصد الملفات المعدلة (git add)...")
        
        try:
            subprocess.run(["git", "add", "."], cwd=repo_path, check=True, capture_output=True)
            self.log("تمت إضافة التعديلات بنجاح.")
            
            self.log("جاري توثيق التحديث (git commit)...")
            res_commit = subprocess.run(["git", "commit", "-m", msg], cwd=repo_path, capture_output=True, text=True)
            self.log(res_commit.stdout)
            self.log(res_commit.stderr)
            
            self.log("جاري الرفع إلى السيرفر (git push)...")
            res_branch = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            branch = res_branch.stdout.strip() if res_branch.returncode == 0 else "main"
            
            res_push = subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True, text=True)
            self.log(res_push.stdout)
            self.log(res_push.stderr)
            
            if res_push.returncode == 0:
                self.log(f"تم رفع التعديلات للمستودع بنجاح إلى GitHub (الفرع {branch})!")
                self.sidebar.refresh_data()
                wx.MessageBox("تم رفع التعديلات إلى GitHub بنجاح!", "نجاح العملية", wx.OK | wx.ICON_INFORMATION)
                self.commit_input.SetValue("")
            else:
                if "nothing to commit" in res_commit.stdout or "clean" in res_commit.stdout:
                    wx.MessageBox("لا توجد أي تغييرات جديدة في المستودع لرفعها.", "تنبيه", wx.OK | wx.ICON_INFORMATION)
                else:
                    wx.MessageBox("فشل الرفع. يرجى مراجعة سجل المخرجات.", "خطأ في الرفع", wx.OK | wx.ICON_ERROR)
                    
        except Exception as e:
            self.log(f"حدث خطأ غير متوقع: {str(e)}")
            wx.MessageBox(f"خطأ: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


class AnnouncementsTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        self.rows = []
        
        form_sizer = wx.BoxSizer(wx.VERTICAL)
        
        self.scroll_win = wx.ScrolledWindow(self)
        self.scroll_win.SetScrollRate(0, 20)
        self.scroll_sizer = wx.BoxSizer(wx.VERTICAL)
        self.scroll_win.SetSizer(self.scroll_sizer)
        
        form_sizer.Add(self.scroll_win, 1, wx.EXPAND | wx.ALL, 5)
        
        self.add_btn = wx.Button(self, label="إضافة إعلان جديد +", name="إضافة إعلان جديد")
        self.add_btn.Bind(wx.EVT_BUTTON, self.on_add_announcement)
        form_sizer.Add(self.add_btn, 0, wx.EXPAND | wx.ALL, 10)
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ التغييرات محلياً", name="حفظ الإعلانات محلياً")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save_local)
        
        self.upload_btn = wx.Button(self, label="حفظ ورفع التغييرات إلى السحابة", name="رفع الإعلانات للسحابة")
        self.upload_btn.Bind(wx.EVT_BUTTON, self.on_upload_cloud)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        btn_sizer.Add(self.upload_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        form_sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(form_sizer)
        self.load_announcements()

    def get_config_file_path(self):
        repo_path = self.active_repo_provider.get_active_repo_path()
        if repo_path:
            path = os.path.join(repo_path, "cloud_config.json")
            if os.path.exists(path):
                return path
        for r in getattr(self.active_repo_provider, "registered_repos", []):
            alt_path = os.path.join(r["path"], "cloud_config.json")
            if os.path.exists(alt_path):
                return alt_path
        return os.path.join(repo_path, "cloud_config.json") if repo_path else ""


    def clear_rows(self):
        self.scroll_sizer.Clear(True)
        self.rows = []
        self.scroll_win.Layout()

    def get_device_groups(self):
        config_path = self.get_config_file_path()
        if not config_path or not os.path.exists(config_path):
            return ["all"]
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            groups = list(data.get("deviceGroups", {}).keys())
            return ["all"] + groups
        except Exception:
            return ["all"]

    def load_announcements(self):
        import datetime
        self.clear_rows()
        config_path = self.get_config_file_path()
        if not config_path or not os.path.exists(config_path):
            self.add_row()
            return
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)

            ann_list = data.get("announcements", [])
            for ann in ann_list:
                sched_time = ann.get("scheduleTime", 0)
                sched_str = ""
                if sched_time > 0:
                    try:
                        sched_str = datetime.datetime.fromtimestamp(sched_time / 1000.0).strftime("%Y-%m-%d %H:%M")
                    except Exception:
                        pass
                self.add_row(
                    title_val=ann.get("title", ""),
                    msg_val=ann.get("message", ""),
                    id_val=ann.get("id", ""),
                    target_devices=ann.get("targetDevices", None),
                    target_group=ann.get("targetGroup", "all"),
                    schedule_time_str=sched_str
                )
            
            if not ann_list:
                self.add_row()
        except Exception as e:
            wx.MessageBox(f"فشل تحميل الإعلانات:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            self.add_row()

    def get_all_device_ids(self):
        """Get list of (device_id, device_name) from cloud config"""
        config_path = self.get_config_file_path()
        if not config_path or not os.path.exists(config_path):
            return []
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            device_names = data.get("deviceNames", {})
            return [(uid, name) for uid, name in sorted(device_names.items(), key=lambda x: (x[1] or ""))]
        except Exception:
            return []

    def add_row(self, title_val="", msg_val="", id_val="", target_devices=None, target_group="all", schedule_time_str="", focus=False):
        idx = len(self.rows) + 1
        
        box = wx.StaticBox(self.scroll_win, label=f"الإعلان #{idx}")
        box_sizer = wx.StaticBoxSizer(box, wx.VERTICAL)
        
        hdr_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        title_label = f"عنوان الإعلان رقم {idx}"
        title_txt = wx.StaticText(box, label=title_label + ":")
        title_input = wx.TextCtrl(box, value=title_val, name=title_label, size=(200, -1))
        
        id_label = f"معرّف الإعلان رقم {idx}"
        id_txt = wx.StaticText(box, label=" ID:")
        id_input = wx.TextCtrl(box, value=id_val if id_val else f"ann_{int(time.time()*1000)}", name=id_label, size=(120, -1))
        id_txt.Hide()
        id_input.Hide()
        
        hdr_sizer.Add(title_txt, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        hdr_sizer.Add(title_input, 1, wx.EXPAND | wx.RIGHT, 15)
        
        msg_label = f"محتوى الإعلان رقم {idx}"
        msg_txt = wx.StaticText(box, label="محتوى الرسالة:")
        msg_input = wx.TextCtrl(box, value=msg_val, name=msg_label, style=wx.TE_MULTILINE, size=(-1, 50))
        
        # Device targeting section
        target_label = wx.StaticText(box, label="استهداف الأجهزة:")
        
        radio_all = wx.RadioButton(box, label="جميع الأجهزة", name=f"جميع الأجهزة للإعلان {idx}", style=wx.RB_GROUP)
        radio_selected = wx.RadioButton(box, label="أجهزة محددة", name=f"أجهزة محددة للإعلان {idx}")
        
        radio_sizer = wx.BoxSizer(wx.HORIZONTAL)
        radio_sizer.Add(target_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 10)
        radio_sizer.Add(radio_all, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 15)
        radio_sizer.Add(radio_selected, 0, wx.ALIGN_CENTER_VERTICAL)
        
        # Device checkboxes panel
        devices_panel = wx.Panel(box)
        devices_sizer = wx.WrapSizer(wx.HORIZONTAL)
        devices_panel.SetSizer(devices_sizer)
        
        all_devices = self.get_all_device_ids()
        device_checkboxes = []
        for dev_id, dev_name in all_devices:
            display = dev_name if dev_name else dev_id[:8]
            cb = wx.CheckBox(devices_panel, label=display, name=f"جهاز {display} للإعلان {idx}")
            cb.device_id = dev_id
            if target_devices is not None and dev_id in target_devices:
                cb.SetValue(True)
            devices_sizer.Add(cb, 0, wx.ALL, 3)
            device_checkboxes.append(cb)
        
        # Set initial state
        if target_devices is None or len(target_devices) == 0:
            radio_all.SetValue(True)
            devices_panel.Hide()
        else:
            radio_selected.SetValue(True)
            devices_panel.Show()
        
        def on_radio_change(evt):
            if radio_selected.GetValue():
                devices_panel.Show()
            else:
                devices_panel.Hide()
            self.scroll_win.Layout()
            self.Layout()
        
        radio_all.Bind(wx.EVT_RADIOBUTTON, on_radio_change)
        radio_selected.Bind(wx.EVT_RADIOBUTTON, on_radio_change)

        # Target Group selection
        group_label = wx.StaticText(box, label="استهداف المجموعة:")
        group_choices = self.get_device_groups()
        group_choice = wx.Choice(box, choices=group_choices, name=f"مجموعة الإعلان {idx}")
        if target_group in group_choices:
            group_choice.SetSelection(group_choices.index(target_group))
        else:
            group_choice.SetSelection(0)

        # Scheduling date/time
        schedule_label = wx.StaticText(box, label="وقت الجدولة (YYYY-MM-DD HH:MM):")
        schedule_input = wx.TextCtrl(box, value=schedule_time_str, name=f"وقت جدولة الإعلان {idx}", size=(150, -1))
        schedule_hint = wx.StaticText(box, label="(اتركه فارغاً للعرض الفوري)")

        extra_sizer = wx.BoxSizer(wx.HORIZONTAL)
        extra_sizer.Add(group_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        extra_sizer.Add(group_choice, 0, wx.EXPAND | wx.RIGHT, 15)
        extra_sizer.Add(schedule_label, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        extra_sizer.Add(schedule_input, 0, wx.EXPAND | wx.RIGHT, 5)
        extra_sizer.Add(schedule_hint, 0, wx.ALIGN_CENTER_VERTICAL)
        
        delete_btn = wx.Button(box, label="إزالة الإعلان", name=f"إزالة الإعلان رقم {idx}")
        
        box_sizer.Add(hdr_sizer, 0, wx.EXPAND | wx.BOTTOM, 5)
        box_sizer.Add(msg_txt, 0, wx.BOTTOM, 2)
        box_sizer.Add(msg_input, 0, wx.EXPAND | wx.BOTTOM, 5)
        box_sizer.Add(radio_sizer, 0, wx.EXPAND | wx.BOTTOM, 5)
        box_sizer.Add(devices_panel, 0, wx.EXPAND | wx.BOTTOM, 5)
        box_sizer.Add(extra_sizer, 0, wx.EXPAND | wx.BOTTOM, 5)
        box_sizer.Add(delete_btn, 0, wx.ALIGN_LEFT)
        
        self.scroll_sizer.Add(box_sizer, 0, wx.EXPAND | wx.ALL, 8)
        
        row_data = {
            "box_sizer": box_sizer,
            "title_txt": title_txt, "title_input": title_input,
            "id_txt": id_txt, "id_input": id_input,
            "msg_txt": msg_txt, "msg_input": msg_input,
            "delete_btn": delete_btn,
            "target_label": target_label,
            "radio_all": radio_all, "radio_selected": radio_selected,
            "devices_panel": devices_panel,
            "device_checkboxes": device_checkboxes,
            "group_choice": group_choice,
            "schedule_input": schedule_input,
            "radio_sizer_items": [target_label, radio_all, radio_selected]
        }
        self.rows.append(row_data)
        
        delete_btn.Bind(wx.EVT_BUTTON, lambda e: self.on_delete_row(row_data))
        self.scroll_win.Layout()
        self.Layout()
        
        if focus and not title_val:
            wx.CallAfter(title_input.SetFocus)

    def on_add_announcement(self, event):
        self.add_row(focus=True)

    def on_delete_row(self, row_data):
        if row_data not in self.rows:
            return
        wx.CallAfter(self.really_delete_row, row_data)

    def really_delete_row(self, row_data):
        if row_data not in self.rows:
            return
        
        box_sizer = row_data["box_sizer"]
        try:
            self.scroll_sizer.Detach(box_sizer)
        except Exception:
            pass
            
        box = box_sizer.GetStaticBox()
        if box:
            try:
                box.Destroy()
            except Exception:
                pass
                
        self.rows.remove(row_data)
        self.scroll_win.Layout()
        self.refresh_row_indices()

    def refresh_row_indices(self):
        for i, row in enumerate(self.rows):
            idx = i + 1
            row["box_sizer"].GetStaticBox().SetLabel(f"الإعلان #{idx}")
            row["title_txt"].SetLabel(f"عنوان الإعلان رقم {idx}:")
            row["title_input"].SetName(f"عنوان الإعلان رقم {idx}")
            row["id_input"].SetName(f"معرّف الإعلان رقم {idx}")
            row["msg_input"].SetName(f"محتوى الإعلان رقم {idx}")
            row["delete_btn"].SetName(f"إزالة الإعلان رقم {idx}")
        self.scroll_win.Layout()

    def get_data(self):
        import datetime
        anns = []
        for row in self.rows:
            t = row["title_input"].GetValue().strip()
            m = row["msg_input"].GetValue().strip()
            i = row["id_input"].GetValue().strip()
            if t and m:
                ann_obj = {
                    "id": i if i else f"ann_{int(time.time()*1000)}",
                    "title": t,
                    "message": m
                }
                
                # Target Group
                g_idx = row["group_choice"].GetSelection()
                if g_idx != wx.NOT_FOUND:
                    g_val = row["group_choice"].GetString(g_idx)
                    ann_obj["targetGroup"] = g_val
                else:
                    ann_obj["targetGroup"] = "all"
                
                # Schedule Time
                s_val = row["schedule_input"].GetValue().strip()
                sched_ts = 0
                if s_val:
                    try:
                        dt = datetime.datetime.strptime(s_val, "%Y-%m-%d %H:%M")
                        sched_ts = int(dt.timestamp() * 1000)
                    except Exception:
                        wx.MessageBox(f"تنسيق وقت الجدولة غير صالح للإعلان \"{t}\". يرجى استخدام التنسيق YYYY-MM-DD HH:MM. سيتم عرض الإعلان فوراً.", "تنبيه", wx.OK | wx.ICON_WARNING)
                ann_obj["scheduleTime"] = sched_ts

                if row["radio_selected"].GetValue():
                    selected_ids = []
                    for cb in row.get("device_checkboxes", []):
                        if cb.GetValue():
                            selected_ids.append(cb.device_id)
                    if selected_ids:
                        ann_obj["targetDevices"] = selected_ids
                anns.append(ann_obj)
        return anns

    def save_local_action(self):
        config_path = self.get_config_file_path()
        if not config_path:
            return False
        try:
            anns = self.get_data()
            if not os.path.exists(config_path):
                data = {"whitelistedDevices": {}}
            else:
                with open(config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            
            data["announcements"] = anns
            with open(config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            self.sidebar.refresh_data()
            return True
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ المحلي:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            return False

    def on_save_local(self, event):
        if self.save_local_action():
            wx.MessageBox("تم حفظ الإعلانات محلياً بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)

    def on_upload_cloud(self, event):
        if not self.save_local_action():
            return
        
        config_path = self.get_config_file_path()
        if not config_path:
            return
        repo_path = os.path.dirname(config_path)
        try:
            subprocess.run(["git", "add", "cloud_config.json"], cwd=repo_path, check=True, capture_output=True)
            subprocess.run(["git", "commit", "-m", "Update announcements via wxPython GUI"], cwd=repo_path, capture_output=True)
            
            res_branch = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            branch = res_branch.stdout.strip() if res_branch.returncode == 0 else "main"
            
            subprocess.run(["git", "push", "origin", branch], cwd=repo_path, check=True, capture_output=True)
            self.sidebar.refresh_data()
            wx.MessageBox("تم الرفع والتطبيق على السحابة بنجاح!", "نجاح الرفع", wx.OK | wx.ICON_INFORMATION)
        except subprocess.CalledProcessError as e:
            err = e.stderr.decode("utf-8", errors="ignore")
            if "nothing to commit" in err or "clean" in err:
                wx.MessageBox("لا توجد تغييرات لرفعها، السحابة متطابقة بالفعل.", "تنبيه", wx.OK | wx.ICON_INFORMATION)
            else:
                wx.MessageBox(f"خطأ في الاتصال بالرفع:\n{err}", "فشل الرفع", wx.OK | wx.ICON_ERROR)


class DeviceGroupsTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        self.groups_data = {}
        
        main_sizer = wx.BoxSizer(wx.VERTICAL)
        
        add_sizer = wx.BoxSizer(wx.HORIZONTAL)
        lbl = wx.StaticText(self, label="اسم المجموعة الجديدة:")
        self.group_name_input = wx.TextCtrl(self, size=(200, -1))
        add_btn = wx.Button(self, label="إضافة مجموعة +")
        add_btn.Bind(wx.EVT_BUTTON, self.on_add_group)
        
        add_sizer.Add(lbl, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        add_sizer.Add(self.group_name_input, 0, wx.EXPAND | wx.RIGHT, 10)
        add_sizer.Add(add_btn, 0)
        main_sizer.Add(add_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.scroll_win = wx.ScrolledWindow(self)
        self.scroll_win.SetScrollRate(0, 20)
        self.scroll_sizer = wx.BoxSizer(wx.VERTICAL)
        self.scroll_win.SetSizer(self.scroll_sizer)
        main_sizer.Add(self.scroll_win, 1, wx.EXPAND | wx.ALL, 5)
        
        btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ المجموعات محلياً")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save_local)
        self.upload_btn = wx.Button(self, label="حفظ ورفع المجموعات للسحابة")
        self.upload_btn.Bind(wx.EVT_BUTTON, self.on_upload_cloud)
        
        btn_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        btn_sizer.Add(self.upload_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 5)
        main_sizer.Add(btn_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        self.SetSizer(main_sizer)
        self.load_groups()

    def get_config_file_path(self):
        repo_path = self.active_repo_provider.get_active_repo_path()
        if repo_path:
            path = os.path.join(repo_path, "cloud_config.json")
            if os.path.exists(path):
                return path
        return ""

    def load_groups(self):
        self.scroll_sizer.Clear(True)
        self.groups_data = {}
        config_path = self.get_config_file_path()
        if not config_path or not os.path.exists(config_path):
            return
        
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.groups_data = data.get("deviceGroups", {})
            device_names = data.get("deviceNames", {})
            
            for group_name, members in sorted(self.groups_data.items()):
                self.add_group_section(group_name, members, device_names)
        except Exception as e:
            wx.MessageBox(f"فشل تحميل مجموعات الأجهزة: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def add_group_section(self, group_name, members, device_names):
        box = wx.StaticBox(self.scroll_win, label=f"مجموعة: {group_name}")
        box_sizer = wx.StaticBoxSizer(box, wx.VERTICAL)
        
        cb_sizer = wx.WrapSizer(wx.HORIZONTAL)
        checkboxes = []
        for dev_id, dev_name in sorted(device_names.items(), key=lambda x: (x[1] or "")):
            display = dev_name if dev_name else dev_id[:8]
            cb = wx.CheckBox(box, label=display)
            cb.device_id = dev_id
            if dev_id in members:
                cb.SetValue(True)
            cb_sizer.Add(cb, 0, wx.ALL, 5)
            checkboxes.append(cb)
            
        box_sizer.Add(cb_sizer, 0, wx.EXPAND | wx.BOTTOM, 10)
        
        del_btn = wx.Button(box, label="حذف المجموعة")
        del_btn.Bind(wx.EVT_BUTTON, lambda e: self.on_delete_group(group_name))
        box_sizer.Add(del_btn, 0, wx.ALIGN_LEFT)
        
        box.checkboxes = checkboxes
        box.group_name = group_name
        
        self.scroll_sizer.Add(box_sizer, 0, wx.EXPAND | wx.ALL, 8)
        self.scroll_win.Layout()
        self.Layout()

    def on_add_group(self, event):
        name = self.group_name_input.GetValue().strip()
        if not name:
            wx.MessageBox("الرجاء إدخال اسم المجموعة!", "خطأ", wx.OK | wx.ICON_ERROR)
            return
        
        config_path = self.get_config_file_path()
        if not config_path or not os.path.exists(config_path):
            wx.MessageBox("ملف cloud_config.json غير موجود!", "خطأ", wx.OK | wx.ICON_ERROR)
            return
            
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            device_names = data.get("deviceNames", {})
            
            if name in self.groups_data:
                wx.MessageBox("هذه المجموعة موجودة بالفعل!", "تنبيه", wx.OK | wx.ICON_WARNING)
                return
                
            self.groups_data[name] = []
            self.add_group_section(name, [], device_names)
            self.group_name_input.SetValue("")
        except Exception as e:
            wx.MessageBox(str(e), "خطأ", wx.OK | wx.ICON_ERROR)

    def on_delete_group(self, group_name):
        if wx.MessageBox(f"هل أنت متأكد من حذف مجموعة \"{group_name}\"؟", "تأكيد الحذف", wx.YES_NO | wx.ICON_QUESTION) == wx.YES:
            self.save_local_action(exclude_group=group_name)
            self.load_groups()

    def get_data(self, exclude_group=None):
        updated_groups = {}
        for child in self.scroll_sizer.GetChildren():
            sizer = child.GetSizer()
            if isinstance(sizer, wx.StaticBoxSizer):
                box = sizer.GetStaticBox()
                if hasattr(box, "group_name") and hasattr(box, "checkboxes"):
                    g_name = box.group_name
                    if g_name == exclude_group:
                        continue
                    members = []
                    for cb in box.checkboxes:
                        if cb.GetValue():
                            members.append(cb.device_id)
                    updated_groups[g_name] = members
                    
        for g_name, members in self.groups_data.items():
            if g_name not in updated_groups and g_name != exclude_group:
                updated_groups[g_name] = members
        return updated_groups

    def save_local_action(self, exclude_group=None):
        config_path = self.get_config_file_path()
        if not config_path:
            return False
        try:
            updated_groups = self.get_data(exclude_group)
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            data["deviceGroups"] = updated_groups
            with open(config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            self.sidebar.refresh_data()
            return True
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ المحلي للمجموعات:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            return False

    def on_save_local(self, event):
        if self.save_local_action():
            wx.MessageBox("تم حفظ المجموعات محلياً بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)
            self.load_groups()

    def on_upload_cloud(self, event):
        if not self.save_local_action():
            return
        config_path = self.get_config_file_path()
        if not config_path:
            return
        repo_path = os.path.dirname(config_path)
        try:
            subprocess.run(["git", "add", "cloud_config.json"], cwd=repo_path, check=True, capture_output=True)
            subprocess.run(["git", "commit", "-m", "Update device groups via wxPython GUI"], cwd=repo_path, capture_output=True)
            
            res = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
            branch = res.stdout.strip() if res.returncode == 0 else "main"
                
            res = subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True, text=True)
            if res.returncode == 0:
                wx.MessageBox("تم حفظ ورفع المجموعات إلى السحابة بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)
                self.load_groups()
            else:
                wx.MessageBox(f"فشل الرفع للسحابة:\n{res.stderr}", "خطأ في الرفع", wx.OK | wx.ICON_ERROR)
        except Exception as e:
            wx.MessageBox(f"حدث خطأ أثناء الرفع: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


class AnalyticsTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        self.rows = []
        self.all_devices_data = []
        
        main_sizer = wx.BoxSizer(wx.VERTICAL)
        
        search_sizer = wx.BoxSizer(wx.HORIZONTAL)
        search_lbl = wx.StaticText(self, label="ابحث بالاسم أو بالمعرّف (ID):")
        self.search_input = wx.TextCtrl(self, name="حقل البحث عن مستخدم")
        self.search_input.Bind(wx.EVT_TEXT, self.on_search)
        
        self.refresh_btn = wx.Button(self, label="تحديث البيانات من السحابة 🔄", name="تحديث البيانات")
        self.refresh_btn.Bind(wx.EVT_BUTTON, self.on_refresh_cloud)
        
        self.export_csv_btn = wx.Button(self, label="تصدير الإحصائيات (CSV) 📥", name="تصدير CSV")
        self.export_csv_btn.Bind(wx.EVT_BUTTON, self.on_export_csv)
        
        self.auto_sync_cb = wx.CheckBox(self, label="تحديث تلقائي صامت كل 5 دقائق ⏰", name="تحديث تلقائي")
        self.auto_sync_cb.Bind(wx.EVT_CHECKBOX, self.on_toggle_auto_sync)
        
        search_sizer.Add(search_lbl, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        search_sizer.Add(self.search_input, 1, wx.EXPAND | wx.RIGHT, 10)
        search_sizer.Add(self.refresh_btn, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 10)
        search_sizer.Add(self.export_csv_btn, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 10)
        search_sizer.Add(self.auto_sync_cb, 0, wx.ALIGN_CENTER_VERTICAL)
        main_sizer.Add(search_sizer, 0, wx.EXPAND | wx.ALL, 10)
        
        # Top tools chart box
        self.chart_box = wx.StaticBox(self, label="تحليلات استخدام الأدوات (الأكثر طلباً 📊)")
        self.chart_sizer = wx.StaticBoxSizer(self.chart_box, wx.VERTICAL)
        
        self.chart_grid = wx.FlexGridSizer(rows=5, cols=3, vgap=8, hgap=12)
        self.chart_grid.AddGrowableCol(1, 1)
        self.chart_sizer.Add(self.chart_grid, 1, wx.EXPAND | wx.ALL, 8)
        main_sizer.Add(self.chart_sizer, 0, wx.EXPAND | wx.ALL, 10)

        self.scroll_win = wx.ScrolledWindow(self)
        self.scroll_win.SetScrollRate(0, 20)
        self.scroll_sizer = wx.BoxSizer(wx.VERTICAL)
        self.scroll_win.SetSizer(self.scroll_sizer)
        main_sizer.Add(self.scroll_win, 1, wx.EXPAND | wx.ALL, 5)
        
        self.sync_timer = wx.Timer(self)
        self.Bind(wx.EVT_TIMER, self.on_timer_sync, self.sync_timer)
        
        self.SetSizer(main_sizer)
        self.load_analytics()

    def get_repo_path(self):
        return self.active_repo_provider.get_active_repo_path()

    def clear_rows(self):
        self.scroll_sizer.Clear(True)
        self.rows = []
        self.scroll_win.Layout()

    def load_analytics(self):
        self.clear_rows()
        repo_path = self.get_repo_path()
        if not repo_path or not os.path.exists(repo_path):
            return
            
        config_path = os.path.join(repo_path, "cloud_config.json")
        if not os.path.exists(config_path):
            for r in getattr(self.active_repo_provider, "registered_repos", []):
                alt_path = os.path.join(r["path"], "cloud_config.json")
                if os.path.exists(alt_path):
                    config_path = alt_path
                    break
            
        stats_dir = os.path.join(repo_path, "device_stats")
        if not os.path.exists(stats_dir):
            for r in getattr(self.active_repo_provider, "registered_repos", []):
                alt_dir = os.path.join(r["path"], "device_stats")
                if os.path.exists(alt_dir):
                    stats_dir = alt_dir
                    break
        
        try:
            whitelist = {}
            device_names = {}
            if os.path.exists(config_path):
                with open(config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                whitelist = data.get("whitelistedDevices", {})
                device_names = data.get("deviceNames", {})
            
            stats_map = {}
            if os.path.exists(stats_dir):
                for f_name in os.listdir(stats_dir):
                    if f_name.endswith(".json"):
                        f_path = os.path.join(stats_dir, f_name)
                        try:
                            with open(f_path, "r", encoding="utf-8") as f:
                                s_data = json.load(f)
                            uid = s_data.get("deviceId")
                            if uid:
                                stats_map[uid] = s_data
                        except Exception:
                            pass
            
            all_device_ids = set(device_names.keys())
            for key, uids in whitelist.items():
                for u in uids:
                    all_device_ids.add(u)
                    
            combined_devices = []
            for uid in sorted(list(all_device_ids)):
                checked_features = []
                for f_key, uids in whitelist.items():
                    if uid in uids:
                        checked_features.append(f_key)
                
                combined_devices.append({
                    "id": uid,
                    "name": device_names.get(uid, ""),
                    "whitelisted": True,
                    "features": checked_features,
                    "stats": stats_map.get(uid)
                })
                
            for uid, s_data in stats_map.items():
                if not any(d["id"] == uid for d in combined_devices):
                    combined_devices.append({
                        "id": uid,
                        "name": s_data.get("deviceName", "جهاز غير مسجل"),
                        "whitelisted": False,
                        "features": [],
                        "stats": s_data
                    })
                    
            self.all_devices_data = combined_devices
            self.update_charts(stats_map)
            self.render_analytics()
        except Exception as e:
            wx.MessageBox(f"خطأ أثناء قراءة الإحصائيات: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def render_analytics(self):
        self.clear_rows()
        query = self.search_input.GetValue().strip().lower()
        
        for dev in self.all_devices_data:
            assigned_name = dev.get("name") or ""
            real_name = ""
            if dev["stats"]:
                real_name = dev["stats"].get("deviceName") or ""
                
            if dev["whitelisted"]:
                dev_name = f"{assigned_name} ({real_name})" if real_name else assigned_name
            else:
                dev_name = f"{real_name} (جهاز غير مسجل)" if real_name else "جهاز غير مسجل"
                
            dev_id = str(dev.get("id") or "")
            if query and query not in dev_name.lower() and query not in dev_id.lower() and query not in assigned_name.lower() and query not in real_name.lower():
                continue
                
            idx = len(self.rows) + 1
            
            # Active Status Indicator
            status_indicator = "🔴"
            status_desc = "غير متصل/خامل"
            status_str = "مفعّل في القائمة" if dev["whitelisted"] else "غير مصرح له حالياً"
            active_time = "غير معروف"
            usage_str = "لا توجد بيانات استخدام بعد"
            
            if dev["stats"]:
                last_act = dev["stats"].get("lastActive", 0)
                if last_act:
                    active_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(last_act / 1000.0))
                    diff_hours = (time.time() - (last_act / 1000.0)) / 3600.0
                    if diff_hours <= 24:
                        status_indicator = "🟢"
                        status_desc = "نشط حالياً"
                    elif diff_hours <= 168:
                        status_indicator = "🟡"
                        status_desc = "خامل (خلال أسبوع)"
                    else:
                        status_indicator = "🔴"
                        status_desc = "غير متصل (منذ أكثر من أسبوع)"
            
            box = wx.StaticBox(self.scroll_win, label=f"[{status_indicator}] جهاز: {dev_name}")
            box_sizer = wx.StaticBoxSizer(box, wx.VERTICAL)
            
            name_txt = wx.StaticText(box, label=f"اسم صاحب الجهاز: {dev_name}")
            id_txt = wx.StaticText(box, label=f"معرّف الجهاز (ID): {dev_id}")
            
            if dev["stats"]:
                usage_map = dev["stats"].get("featuresUsage", {})
                if usage_map:
                    usages = []
                    for f_key, count in usage_map.items():
                        label = next((f[1] for f in ALL_FEATURES if f[0] == f_key), f_key)
                        usages.append(f"{label} ({count})")
                    usage_str = " ، ".join(usages)
            
            active_txt = wx.StaticText(box, label=f"الحالة: {status_str} | النشاط: {status_desc} | آخر ظهور: {active_time}")
            usage_txt = wx.StaticText(box, label=f"الميزات المستخدمة: {usage_str}")
            
            btn_sizer = wx.BoxSizer(wx.HORIZONTAL)
            row_data = {
                "box_sizer": box_sizer, "name_txt": name_txt, "id_txt": id_txt,
                "active_txt": active_txt, "usage_txt": usage_txt, "dev_info": dev
            }
            
            if dev["whitelisted"]:
                edit_btn = wx.Button(box, label="تعديل صلاحيات الجهاز", name=f"تعديل صلاحيات {dev['name']}")
                edit_btn.Bind(wx.EVT_BUTTON, lambda e, rd=row_data: self.on_edit_permissions(rd))
                btn_sizer.Add(edit_btn, 0, wx.ALL, 5)
                row_data["edit_btn"] = edit_btn
            else:
                activate_btn = wx.Button(box, label="تفعيل وترخيص الجهاز 🔓", name=f"تفعيل {dev['name']}")
                activate_btn.Bind(wx.EVT_BUTTON, lambda e, rd=row_data: self.on_activate_device(rd))
                btn_sizer.Add(activate_btn, 0, wx.ALL, 5)
                row_data["activate_btn"] = activate_btn
                
            box_sizer.Add(name_txt, 0, wx.ALL, 3)
            box_sizer.Add(id_txt, 0, wx.ALL, 3)
            box_sizer.Add(active_txt, 0, wx.ALL, 3)
            box_sizer.Add(usage_txt, 0, wx.ALL, 3)
            box_sizer.Add(btn_sizer, 0, wx.EXPAND | wx.TOP, 5)
            
            self.scroll_sizer.Add(box_sizer, 0, wx.EXPAND | wx.ALL, 6)
            self.rows.append(row_data)
            
        self.scroll_win.Layout()
        self.Layout()

    def on_search(self, event):
        self.render_analytics()

    def on_edit_permissions(self, row_data):
        dev = row_data["dev_info"]
        with FeatureSelectionDialog(self, dev["name"], dev["features"]) as dlg:
            if dlg.ShowModal() == wx.ID_OK:
                dev["features"] = dlg.selected_features
                self.save_whitelists()

    def on_activate_device(self, row_data):
        dev = row_data["dev_info"]
        config_tab = self.active_repo_provider.tab1
        config_tab.add_row(dev["name"], dev["id"], [f[0] for f in ALL_FEATURES])
        wx.MessageBox(f"تم نقل وتفعيل الجهاز '{dev['name']}' بنجاح! الرجاء حفظ وتطبيق التعديلات من التبويب الأول (الإعدادات السحابية) لتأكيد التفعيل.", "تم التفعيل بنجاح", wx.OK | wx.ICON_INFORMATION)
        self.GetParent().SetSelection(0)

    def save_whitelists(self):
        repo_path = self.get_repo_path()
        config_path = os.path.join(repo_path, "cloud_config.json")
        if not os.path.exists(config_path):
            for r in getattr(self.active_repo_provider, "registered_repos", []):
                alt_path = os.path.join(r["path"], "cloud_config.json")
                if os.path.exists(alt_path):
                    config_path = alt_path
                    break
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                
            whitelist = {}
            for key, label in ALL_FEATURES:
                whitelist[key] = []
                
            device_names = data.get("deviceNames", {})
            
            for dev in self.all_devices_data:
                if dev["whitelisted"]:
                    uid = dev["id"]
                    device_names[uid] = dev["name"]
                    for feat in dev["features"]:
                        if feat in whitelist:
                            whitelist[feat].append(uid)
                            
            data["whitelistedDevices"] = whitelist
            data["deviceNames"] = device_names
            
            with open(config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            wx.MessageBox("تم تحديث صلاحيات الجهاز بنجاح! الرجاء حفظ ورفع التعديلات على السحابة لتطبيقها.", "نجاح", wx.OK | wx.ICON_INFORMATION)
            self.load_analytics()
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            self.load_analytics()

    def on_refresh_cloud(self, event):
        self.refresh_btn.Disable()
        self.refresh_btn.SetLabel("جاري التحديث... ⏳")
        wx.CallAfter(self.do_git_pull)

    def do_git_pull(self):
        import threading
        import subprocess
        
        def run_pull():
            repos_to_pull = getattr(self.active_repo_provider, "registered_repos", [])
            errors = []
            for r in repos_to_pull:
                r_path = r.get("path")
                if r_path and os.path.exists(r_path):
                    try:
                        res = subprocess.run(["git", "pull"], cwd=r_path, capture_output=True, text=True)
                        if res.returncode != 0:
                            err_msg = res.stderr or res.stdout
                            errors.append(f"{r['name']}: {err_msg.strip()}")
                    except Exception as e:
                        errors.append(f"{r['name']}: {str(e)}")
            
            if errors:
                combined_err = "\n".join(errors)
                wx.CallAfter(self.on_pull_failed, combined_err)
            else:
                wx.CallAfter(self.on_pull_success)
                
        threading.Thread(target=run_pull, daemon=True).start()

    def on_pull_success(self):
        self.refresh_btn.Enable()
        self.refresh_btn.SetLabel("تحديث البيانات من السحابة 🔄")
        self.load_analytics()
        wx.MessageBox("تم تحديث البيانات من السحابة بنجاح!", "تحديث ناجح", wx.OK | wx.ICON_INFORMATION)

    def on_pull_failed(self, error):
        self.refresh_btn.Enable()
        self.refresh_btn.SetLabel("تحديث البيانات من السحابة 🔄")
        self.load_analytics()
        wx.MessageBox(f"فشل تحديث البيانات من السحابة:\n{error}", "فشل التحديث", wx.OK | wx.ICON_ERROR)

    def on_export_csv(self, event):
        if not self.all_devices_data:
            wx.MessageBox("لا توجد بيانات لتصديرها!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return

        with wx.FileDialog(self, "حفظ ملف الإحصائيات", wildcard="CSV files (*.csv)|*.csv",
                          style=wx.FD_SAVE | wx.FD_OVERWRITE_PROMPT) as fileDialog:
            if fileDialog.ShowModal() == wx.ID_CANCEL:
                return

            path = fileDialog.GetPath()
            try:
                import csv
                with open(path, "w", encoding="utf-8-sig", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(["معرّف الجهاز (ID)", "الاسم المسجل", "الاسم الفعلي (الموديل)", "حالة الترخيص", "آخر نشاط", "مؤشر النشاط", "الاستخدام بالتفصيل"])
                    for dev in self.all_devices_data:
                        assigned_name = dev.get("name") or ""
                        real_name = ""
                        active_time = "غير معروف"
                        status_indicator = "غير متصل"
                        
                        if dev["stats"]:
                            real_name = dev["stats"].get("deviceName") or ""
                            last_act = dev["stats"].get("lastActive", 0)
                            if last_act:
                                active_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(last_act / 1000.0))
                                diff_hours = (time.time() - (last_act / 1000.0)) / 3600.0
                                if diff_hours <= 24:
                                    status_indicator = "نشط (خلال 24 ساعة)"
                                elif diff_hours <= 168:
                                    status_indicator = "خامل (آخر أسبوع)"
                                else:
                                    status_indicator = "غير نشط (أكثر من أسبوع)"

                        license_status = "مفعّل" if dev["whitelisted"] else "غير مفعّل"
                        
                        usages = []
                        if dev["stats"]:
                            usage_map = dev["stats"].get("featuresUsage", {})
                            for f_key, count in usage_map.items():
                                label = next((f[1] for f in ALL_FEATURES if f[0] == f_key), f_key)
                                usages.append(f"{label}: {count}")
                        usage_str = " | ".join(usages)
                        
                        writer.writerow([dev.get("id", ""), assigned_name, real_name, license_status, active_time, status_indicator, usage_str])
                        
                wx.MessageBox("تم تصدير الإحصائيات بنجاح!", "نجاح التصدير", wx.OK | wx.ICON_INFORMATION)
            except Exception as e:
                wx.MessageBox(f"فشل تصدير الإحصائيات: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_toggle_auto_sync(self, event):
        if self.auto_sync_cb.GetValue():
            self.sync_timer.Start(300000) # 5 minutes
        else:
            self.sync_timer.Stop()

    def on_timer_sync(self, event):
        if not self.search_input.GetValue().strip() and self.refresh_btn.IsEnabled():
            self.do_silent_git_pull()

    def do_silent_git_pull(self):
        import threading
        import subprocess
        
        def run_pull():
            repos_to_pull = getattr(self.active_repo_provider, "registered_repos", [])
            has_changes = False
            for r in repos_to_pull:
                r_path = r.get("path")
                if r_path and os.path.exists(r_path):
                    try:
                        res = subprocess.run(["git", "pull"], cwd=r_path, capture_output=True, text=True)
                        if res.returncode == 0 and "Already up to date" not in res.stdout:
                            has_changes = True
                    except Exception:
                        pass
            if has_changes:
                wx.CallAfter(self.load_analytics)
                
        threading.Thread(target=run_pull, daemon=True).start()

    def update_charts(self, stats_map):
        # Clear previous grid items
        self.chart_grid.Clear(True)
        
        # Aggregate counts
        totals = {}
        for s_data in stats_map.values():
            usage_map = s_data.get("featuresUsage", {})
            for f_key, count in usage_map.items():
                totals[f_key] = totals.get(f_key, 0) + count
                
        # Sort and take top 5
        top_5 = sorted(totals.items(), key=lambda x: x[1], reverse=True)[:5]
        
        if not top_5:
            # Show empty state message
            msg = wx.StaticText(self.chart_box, label="لا توجد بيانات إحصائية كافية لعرض المخطط حالياً.")
            self.chart_grid.Add(msg, 0, wx.ALL, 10)
            self.chart_sizer.Layout()
            self.Layout()
            return
            
        max_count = max(count for f_key, count in top_5)
        
        for f_key, count in top_5:
            label = next((f[1] for f in ALL_FEATURES if f[0] == f_key), f_key)
            
            # Col 0: Label
            lbl = wx.StaticText(self.chart_box, label=f"{label} 🛠️")
            
            # Col 1: Visual bar
            pct = float(count) / max_count if max_count > 0 else 0.0
            bar = BarChartWidget(self.chart_box, percentage=pct, bar_color=wx.Colour(52, 152, 219))
            
            # Col 2: Count label
            cnt_lbl = wx.StaticText(self.chart_box, label=f"{count} استخدام")
            
            self.chart_grid.Add(lbl, 0, wx.ALIGN_CENTER_VERTICAL | wx.LEFT, 5)
            self.chart_grid.Add(bar, 1, wx.EXPAND | wx.ALIGN_CENTER_VERTICAL | wx.LEFT | wx.RIGHT, 5)
            self.chart_grid.Add(cnt_lbl, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
            
        self.chart_sizer.Layout()
        self.Layout()


class BarChartWidget(wx.Panel):
    def __init__(self, parent, percentage=0.0, bar_color=wx.Colour(0, 150, 255)):
        super().__init__(parent, size=(-1, 16))
        self.percentage = percentage
        self.bar_color = bar_color
        self.Bind(wx.EVT_PAINT, self.on_paint)
        
    def on_paint(self, event):
        dc = wx.PaintDC(self)
        size = self.GetSize()
        w = int(size.width * self.percentage)
        h = size.height
        
        # Draw background
        dc.SetBrush(wx.Brush(wx.Colour(235, 240, 245)))
        dc.SetPen(wx.Pen(wx.Colour(220, 225, 230)))
        dc.DrawRoundedRectangle(0, 0, size.width, h, 4)
        
        # Draw filled bar
        if w > 0:
            dc.SetBrush(wx.Brush(self.bar_color))
            dc.SetPen(wx.Pen(self.bar_color))
            dc.DrawRoundedRectangle(0, 0, w, h, 4)


class CrashExplorerTab(wx.Panel):
    def __init__(self, parent, sidebar_ref, active_repo_provider):
        super().__init__(parent)
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        self.sidebar = sidebar_ref
        self.active_repo_provider = active_repo_provider
        self.crashes = []
        
        outer_sizer = wx.BoxSizer(wx.VERTICAL)
        
        # Top Action Bar with Refresh Button
        top_bar = wx.BoxSizer(wx.HORIZONTAL)
        self.refresh_btn = wx.Button(self, label="تحديث وجلب السجلات من GitHub 🔄", name="تحديث وجلب سجلات الأعطال من GitHub")
        self.refresh_btn.Bind(wx.EVT_BUTTON, self.on_refresh)
        top_bar.Add(self.refresh_btn, 0, wx.ALL, 5)
        outer_sizer.Add(top_bar, 0, wx.EXPAND | wx.LEFT | wx.RIGHT | wx.TOP, 5)
        
        main_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        self.detail_panel = wx.Panel(self)
        detail_sizer = wx.BoxSizer(wx.VERTICAL)
        
        self.info_text = wx.StaticText(self.detail_panel, label="اختر تقرير عطل من القائمة اليمنى لعرض تفاصيله:")
        detail_sizer.Add(self.info_text, 0, wx.ALL, 10)
        
        self.trace_box = wx.TextCtrl(self.detail_panel, style=wx.TE_MULTILINE | wx.TE_READONLY | wx.TE_DONTWRAP | wx.HSCROLL, name="تفاصيل مسار الانهيار")
        detail_sizer.Add(self.trace_box, 1, wx.EXPAND | wx.ALL, 5)
        
        self.resolve_btn = wx.Button(self.detail_panel, label="تم حل المشكلة وأرشفة التقرير (Resolve & Archive) ✅")
        self.resolve_btn.Bind(wx.EVT_BUTTON, self.on_resolve)
        self.resolve_btn.Disable()
        detail_sizer.Add(self.resolve_btn, 0, wx.EXPAND | wx.ALL, 10)
        
        self.detail_panel.SetSizer(detail_sizer)
        
        self.list_box = wx.ListBox(self, size=(250, -1), name="قائمة الانهيارات غير المحلولة")
        self.list_box.Bind(wx.EVT_LISTBOX, self.on_select_crash)
        
        main_sizer.Add(self.detail_panel, 1, wx.EXPAND)
        main_sizer.Add(self.list_box, 0, wx.EXPAND | wx.ALL, 5)
        
        outer_sizer.Add(main_sizer, 1, wx.EXPAND)
        self.SetSizer(outer_sizer)
        self.load_crashes()

    def get_repo_path(self):
        return self.active_repo_provider.get_active_repo_path()

    def on_refresh(self, event=None):
        self.refresh_btn.Disable()
        self.refresh_btn.SetLabel("جارٍ التحديث وجلب السجلات من GitHub... ⏳")
        wx.Yield()

        import threading
        def do_pull():
            repos = getattr(self.active_repo_provider, "registered_repos", [])
            if not repos:
                repo_path = self.get_repo_path()
                if repo_path:
                    repos = [{"name": "المستودع", "path": repo_path}]

            errors = []
            for r in repos:
                r_path = r.get("path")
                if r_path and os.path.exists(r_path):
                    try:
                        res = subprocess.run(["git", "pull"], cwd=r_path, capture_output=True, text=True)
                        if res.returncode != 0:
                            err_msg = res.stderr or res.stdout
                            errors.append(f"{r.get('name')}: {err_msg.strip()}")
                    except Exception as e:
                        errors.append(f"{r.get('name')}: {str(e)}")
            
            wx.CallAfter(self.finish_refresh, errors)

        threading.Thread(target=do_pull, daemon=True).start()

    def finish_refresh(self, errors):
        self.refresh_btn.Enable()
        self.refresh_btn.SetLabel("تحديث وجلب السجلات من GitHub 🔄")
        self.load_crashes()
        if errors:
            combined = "\n".join(errors)
            wx.MessageBox(f"تم الجلب مع وجود تنبيهات في بعض المستودعات:\n{combined}", "تنبيه التحديث", wx.OK | wx.ICON_WARNING)
        else:
            wx.MessageBox("تم تحديث وجلب كافة سجلات الأعطال بنجاح من جميع المستودعات!", "نجاح التحديث", wx.OK | wx.ICON_INFORMATION)

    def load_crashes(self):
        self.list_box.Clear()
        self.trace_box.Clear()
        self.resolve_btn.Disable()
        self.info_text.SetLabel("اختر تقرير عطل من القائمة اليمنى لعرض تفاصيله:")
        self.crashes = []
        
        repos = getattr(self.active_repo_provider, "registered_repos", [])
        if not repos:
            repo_path = self.get_repo_path()
            if repo_path:
                repos = [{"name": "المستودع", "path": repo_path}]
            else:
                return

        for r in repos:
            r_path = r.get("path")
            if not r_path or not os.path.exists(r_path):
                continue

            crashes_dir = os.path.join(r_path, "crash_reports")
            if not os.path.exists(crashes_dir):
                continue
                
            try:
                for f_name in sorted(os.listdir(crashes_dir), reverse=True):
                    if f_name.endswith(".json"):
                        f_path = os.path.join(crashes_dir, f_name)
                        try:
                            with open(f_path, "r", encoding="utf-8") as f:
                                c_data = json.load(f)
                            c_data["filename"] = f_name
                            c_data["file_path"] = f_path
                            c_data["repo_path"] = r_path
                            c_data["repo_name"] = r.get("name", "")
                            self.crashes.append(c_data)
                            
                            dt = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(c_data.get("timestamp", 0) / 1000.0))
                            repo_prefix = f"[{r.get('name')}] " if len(repos) > 1 else ""
                            self.list_box.Append(f"{repo_prefix}{c_data.get('deviceName', 'جهاز')} ({dt})")
                        except Exception:
                            pass
            except Exception as e:
                wx.MessageBox(f"خطأ أثناء تحميل الانهيارات من {r.get('name')}: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def on_select_crash(self, event):
        sel = self.list_box.GetSelection()
        if sel == wx.NOT_FOUND or sel >= len(self.crashes):
            return
            
        crash = self.crashes[sel]
        dt = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(crash.get("timestamp", 0) / 1000.0))
        
        info = f"🚨 جهاز: {crash.get('deviceName')} | أندرويد: {crash.get('androidVersion')}\nمعرف الجهاز: {crash.get('deviceId')}\nتاريخ الانهيار: {dt}\n\nرسالة الخطأ: {crash.get('errorMessage')}"
        self.info_text.SetLabel(info)
        
        self.trace_box.SetValue(crash.get("stackTrace", ""))
        self.resolve_btn.Enable()

    def on_resolve(self, event):
        sel = self.list_box.GetSelection()
        if sel == wx.NOT_FOUND or sel >= len(self.crashes):
            return
            
        crash = self.crashes[sel]
        filename = crash["filename"]
        
        confirm = wx.MessageBox(f"هل أنت متأكد من حل هذه المشكلة وأرشفة السجل؟\nسيتم حذف الملف '{filename}' محلياً ورفعه لإزالته من مستودع GitHub.", "تأكيد الحل والأرشفة", wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING)
        if confirm != wx.YES:
            return
            
        repo_path = crash["repo_path"]
        file_path = crash["file_path"]
        
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
            
            try:
                subprocess.run(["git", "rm", os.path.join("crash_reports", filename)], cwd=repo_path, capture_output=True)
                subprocess.run(["git", "commit", "-m", f"Resolve crash report {filename} via wxPython GUI"], cwd=repo_path, capture_output=True)
                
                res_branch = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
                branch = res_branch.stdout.strip() if res_branch.returncode == 0 else "main"
                subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True)
            except Exception:
                pass
                
            wx.MessageBox("تم أرشفة وحذف سجل الانهيار بنجاح!", "نجاح العملية", wx.OK | wx.ICON_INFORMATION)
            self.load_crashes()
            self.sidebar.refresh_data()
        except Exception as e:
            wx.MessageBox(f"فشل حذف الملف: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


class MainManagerFrame(wx.Frame):
    def __init__(self):
        super().__init__(
            parent=None, 
            title="مدير مستودعات وإصدارات GitHub المتعددة", 
            size=(1100, 680)
        )
        self.SetLayoutDirection(wx.Layout_RightToLeft)
        
        # Load registered repositories
        self.load_registered_repos()
        
        main_panel = wx.Panel(self)
        layout_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        # 1. Right Side: Combo Box + Add/Edit/Delete Repo Buttons + Dynamic Sidebar
        sidebar_outer_sizer = wx.BoxSizer(wx.VERTICAL)
        
        # Combo box and button frame
        repo_select_sizer = wx.BoxSizer(wx.VERTICAL)
        
        repo_select_sizer.Add(wx.StaticText(main_panel, label="اختر المستودع النشط:"), 0, wx.ALL | wx.EXPAND, 3)
        
        # Populate Combobox
        repo_names = [repo["name"] for repo in self.registered_repos]
        self.repo_combo = wx.ComboBox(
            main_panel, 
            choices=repo_names, 
            style=wx.CB_READONLY, 
            name="المستودع النشط"
        )
        self.repo_combo.SetSelection(0 if repo_names else wx.NOT_FOUND)
        self.repo_combo.Bind(wx.EVT_COMBOBOX, self.on_repo_selection_changed)
        repo_select_sizer.Add(self.repo_combo, 0, wx.EXPAND | wx.BOTTOM, 5)
        
        # Horizontal sizer for repo actions
        repo_actions_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        self.add_repo_btn = wx.Button(main_panel, label="إضافة +", name="إضافة مستودع جديد للأداة")
        self.add_repo_btn.Bind(wx.EVT_BUTTON, self.on_add_repo_click)
        
        self.edit_repo_btn = wx.Button(main_panel, label="تعديل", name="تعديل بيانات المستودع النشط")
        self.edit_repo_btn.Bind(wx.EVT_BUTTON, self.on_edit_repo_click)
        
        self.delete_repo_btn = wx.Button(main_panel, label="حذف", name="حذف المستودع النشط")
        self.delete_repo_btn.Bind(wx.EVT_BUTTON, self.on_delete_repo_click)
        
        repo_actions_sizer.Add(self.add_repo_btn, 1, wx.EXPAND | wx.LEFT, 2)
        repo_actions_sizer.Add(self.edit_repo_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 2)
        repo_actions_sizer.Add(self.delete_repo_btn, 1, wx.EXPAND | wx.RIGHT, 2)
        
        repo_select_sizer.Add(repo_actions_sizer, 0, wx.EXPAND | wx.BOTTOM, 10)
        
        sidebar_outer_sizer.Add(repo_select_sizer, 0, wx.EXPAND | wx.ALL, 5)
        
        # Dynamic Sidebar
        self.sidebar = DynamicSidebarPanel(main_panel, active_repo_provider=self)
        sidebar_outer_sizer.Add(self.sidebar, 1, wx.EXPAND | wx.ALL, 5)
        
        # 2. Left Side: Notebook Tabs
        self.notebook = wx.Notebook(main_panel)
        self.notebook.SetLayoutDirection(wx.Layout_RightToLeft)
        self.notebook.Bind(wx.EVT_NOTEBOOK_PAGE_CHANGED, self.on_tab_changed)
        
        self.tab1 = CloudConfigTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab2 = AppUpdateTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab3 = GitTagsTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab4 = SourceCodeTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab5 = AnnouncementsTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab6 = AnalyticsTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab7 = CrashExplorerTab(self.notebook, self.sidebar, active_repo_provider=self)
        self.tab8 = DeviceGroupsTab(self.notebook, self.sidebar, active_repo_provider=self)
        
        self.notebook.AddPage(self.tab1, "1. الإعدادات السحابية")
        self.notebook.AddPage(self.tab2, "2. تحديث التطبيق")
        self.notebook.AddPage(self.tab3, "3. إدارة الـ Tags")
        self.notebook.AddPage(self.tab4, "4. الكود المصدري")
        self.notebook.AddPage(self.tab5, "5. الإعلانات العامة")
        self.notebook.AddPage(self.tab6, "6. الإحصائيات والبحث")
        self.notebook.AddPage(self.tab7, "7. سجل الأعطال")
        self.notebook.AddPage(self.tab8, "8. مجموعات الأجهزة")
        
        # Assemble Layout
        layout_sizer.Add(self.notebook, 3, wx.EXPAND | wx.ALL, 5)
        layout_sizer.Add(sidebar_outer_sizer, 1, wx.EXPAND | wx.ALL, 10)
        
        main_panel.SetSizer(layout_sizer)
        self.Centre()
        
        wx.CallAfter(self.sidebar.set_mode, "releases_files")

    def load_registered_repos(self):
        # Default starting configurations
        self.registered_repos = [
            {
                "name": "مستودع ملفات التحديث والإصدارات",
                "path": RELEASES_REPO
            },
            {
                "name": "مستودع الكود المصدري للتطبيق",
                "path": SOURCE_REPO
            }
        ]
        
        try:
            if os.path.exists(REPOS_CONFIG_FILE):
                with open(REPOS_CONFIG_FILE, "r", encoding="utf-8") as f:
                    self.registered_repos = json.load(f)
            else:
                # Save defaults if file doesn't exist
                os.makedirs(os.path.dirname(REPOS_CONFIG_FILE), exist_ok=True)
                with open(REPOS_CONFIG_FILE, "w", encoding="utf-8") as f:
                    json.dump(self.registered_repos, f, indent=2, ensure_ascii=False)
        except Exception as e:
            wx.MessageBox(f"فشل تحميل قائمة المستودعات المسجلة:\n{str(e)}", "تنبيه", wx.OK | wx.ICON_WARNING)

    def save_registered_repos(self):
        try:
            with open(REPOS_CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(self.registered_repos, f, indent=2, ensure_ascii=False)
        except Exception as e:
            wx.MessageBox(f"فشل حفظ قائمة المستودعات:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def get_active_repo_path(self):
        sel = self.repo_combo.GetSelection()
        if sel != wx.NOT_FOUND and sel < len(self.registered_repos):
            return self.registered_repos[sel]["path"]
        return None

    def on_repo_selection_changed(self, event):
        self.active_repo_changed()

    def active_repo_changed(self):
        # Refresh current sidebar
        self.sidebar.refresh_data()
        
        # Tell active tabs to re-load file details
        self.tab1.load_config()
        self.tab2.load_update_info()
        self.tab5.load_announcements()
        self.tab6.load_analytics()
        self.tab7.load_crashes()
        self.tab8.load_groups()

    def on_tab_changed(self, event):
        sel = event.GetSelection()
        if sel in (0, 1, 4, 5, 6, 7):
            self.sidebar.set_mode("releases_files")
            if sel == 5:
                self.tab6.load_analytics()
            elif sel == 6:
                self.tab7.load_crashes()
            elif sel == 7:
                self.tab8.load_groups()
        elif sel == 2:
            self.sidebar.set_mode("tags")
        elif sel == 3:
            self.sidebar.set_mode("source_files")
        event.Skip()

    def on_add_repo_click(self, event):
        with AddRepoDialog(self) as dlg:
            if dlg.ShowModal() == wx.ID_OK:
                # Add to internal configurations list
                new_repo = {
                    "name": dlg.repo_name,
                    "path": dlg.cloned_path,
                    "url": dlg.repo_url
                }
                self.registered_repos.append(new_repo)
                self.save_registered_repos()
                
                # Update combo choices
                self.repo_combo.Append(dlg.repo_name)
                # Select the newly cloned repo
                self.repo_combo.SetSelection(len(self.registered_repos) - 1)
                
                # Trigger changes refresh
                self.active_repo_changed()
                play_success_sound()

    def on_edit_repo_click(self, event):
        sel = self.repo_combo.GetSelection()
        if sel == wx.NOT_FOUND or sel >= len(self.registered_repos):
            play_error_sound()
            wx.MessageBox("الرجاء اختيار مستودع لتعديله أولاً!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        repo = self.registered_repos[sel]
        current_url = repo.get("url", "")
        if not current_url and os.path.exists(repo["path"]):
            try:
                res = subprocess.run(["git", "config", "--get", "remote.origin.url"], cwd=repo["path"], capture_output=True, text=True)
                if res.returncode == 0:
                    current_url = res.stdout.strip()
            except Exception:
                pass
                
        with EditRepoDialog(self, repo["name"], repo["path"], current_url) as dlg:
            if dlg.ShowModal() == wx.ID_OK:
                repo["name"] = dlg.updated_name
                repo["path"] = dlg.updated_path
                repo["url"] = dlg.updated_url
                self.save_registered_repos()
                
                # Update remote URL in Git via git remote set-url origin <new_url>
                if os.path.exists(dlg.updated_path):
                    try:
                        subprocess.run(["git", "remote", "set-url", "origin", dlg.updated_url], cwd=dlg.updated_path, capture_output=True)
                    except Exception:
                        pass
                
                self.repo_combo.SetString(sel, dlg.updated_name)
                self.active_repo_changed()
                play_success_sound()

    def on_delete_repo_click(self, event):
        sel = self.repo_combo.GetSelection()
        if sel == wx.NOT_FOUND or sel >= len(self.registered_repos):
            play_error_sound()
            wx.MessageBox("الرجاء اختيار مستودع لحذفه أولاً!", "تنبيه", wx.OK | wx.ICON_WARNING)
            return
            
        repo = self.registered_repos[sel]
        
        confirm_remove = wx.MessageBox(
            f"هل أنت متأكد تماماً من رغبتك في إزالة المستودع '{repo['name']}' من قائمة الأداة؟",
            "تأكيد إزالة مستودع",
            wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
        )
        if confirm_remove != wx.YES:
            return
            
        confirm_disk = wx.MessageBox(
            f"هل تريد أيضاً حذف مجلد المستودع بالكامل من القرص الصلب (على جهازك)؟\nتنبيه: هذا الإجراء سيقوم بمسح المجلد '{repo['path']}' نهائياً ولا يمكن التراجع عنه!",
            "تأكيد حذف الملفات من القرص الصلب",
            wx.YES_NO | wx.NO_DEFAULT | wx.ICON_WARNING
        )
        
        self.registered_repos.pop(sel)
        self.save_registered_repos()
        
        if confirm_disk == wx.YES:
            try:
                if os.path.exists(repo['path']):
                    shutil.rmtree(repo['path'])
                    wx.MessageBox("تم حذف مجلد المستودع من القرص الصلب بنجاح.", "تم الحذف", wx.OK | wx.ICON_INFORMATION)
            except Exception as e:
                play_error_sound()
                wx.MessageBox(f"حدث خطأ أثناء محاولة حذف المجلد من القرص الصلب:\n{str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
                
        self.repo_combo.Delete(sel)
        
        if len(self.registered_repos) > 0:
            self.repo_combo.SetSelection(0)
        else:
            self.repo_combo.Clear()
            
        self.active_repo_changed()
        play_success_sound()


if __name__ == "__main__":
    app = wx.App()
    frame = MainManagerFrame()
    frame.Show()
    app.MainLoop()
