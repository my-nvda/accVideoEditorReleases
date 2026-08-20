import os
import json
import subprocess
import time
import wx

# Paths
REPO_PATH = r"D:\.gemini\antigravity\scratch\accVideoEditorReleases"
CONFIG_FILE = os.path.join(REPO_PATH, "cloud_config.json")

class DeviceRow:
    def __init__(self, parent, sizer, index, name_val="", id_val="", on_delete_callback=None):
        self.parent = parent
        self.sizer = sizer
        self.on_delete = on_delete_callback
        
        # Row Container Sizer
        self.row_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        # Name Field
        name_label = f"اسم صاحب الجهاز رقم {index}"
        self.name_text = wx.StaticText(parent, label=name_label + ":")
        self.name_input = wx.TextCtrl(parent, value=name_val, name=name_label, size=(150, -1))
        
        # ID Field
        id_label = f"معرّف الجهاز رقم {index}"
        self.id_text = wx.StaticText(parent, label=id_label + ":")
        self.id_input = wx.TextCtrl(parent, value=id_val, name=id_label, size=(250, -1))
        
        # Delete Button
        btn_label = f"إزالة الجهاز رقم {index}"
        self.delete_btn = wx.Button(parent, label="إزالة", name=btn_label)
        self.delete_btn.Bind(wx.EVT_BUTTON, self.on_delete_click)
        
        # Add to row sizer
        self.row_sizer.Add(self.name_text, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        self.row_sizer.Add(self.name_input, 1, wx.EXPAND | wx.RIGHT, 15)
        self.row_sizer.Add(self.id_text, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        self.row_sizer.Add(self.id_input, 1, wx.EXPAND | wx.RIGHT, 15)
        self.row_sizer.Add(self.delete_btn, 0, wx.ALIGN_CENTER_VERTICAL)
        
        # Add row sizer to parent vertical sizer
        self.sizer.Add(self.row_sizer, 0, wx.EXPAND | wx.ALL, 8)
        self.parent.Layout()

    def on_delete_click(self, event):
        if self.on_delete:
            self.on_delete(self)

    def destroy_widgets(self):
        self.name_text.Destroy()
        self.name_input.Destroy()
        self.id_text.Destroy()
        self.id_input.Destroy()
        self.delete_btn.Destroy()


class AnnouncementRow:
    def __init__(self, parent, sizer, index, title_val="", msg_val="", id_val="", on_delete_callback=None):
        self.parent = parent
        self.sizer = sizer
        self.on_delete = on_delete_callback
        
        self.row_sizer = wx.BoxSizer(wx.VERTICAL)
        
        # Header (Title & ID) row
        hdr_sizer = wx.BoxSizer(wx.HORIZONTAL)
        
        title_label = f"عنوان الإعلان رقم {index}"
        self.title_text = wx.StaticText(parent, label=title_label + ":")
        self.title_input = wx.TextCtrl(parent, value=title_val, name=title_label, size=(200, -1))
        
        id_label = f"معرّف الإعلان رقم {index}"
        self.id_text = wx.StaticText(parent, label=" ID:")
        self.id_input = wx.TextCtrl(parent, value=id_val if id_val else f"ann_{int(time.time()*1000)}", name=id_label, size=(120, -1))
        
        hdr_sizer.Add(self.title_text, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        hdr_sizer.Add(self.title_input, 1, wx.EXPAND | wx.RIGHT, 15)
        hdr_sizer.Add(self.id_text, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 5)
        hdr_sizer.Add(self.id_input, 0, wx.RIGHT, 15)
        
        # Message input
        msg_label = f"محتوى الإعلان رقم {index}"
        self.msg_text = wx.StaticText(parent, label="محتوى الرسالة:")
        self.msg_input = wx.TextCtrl(parent, value=msg_val, name=msg_label, style=wx.TE_MULTILINE, size=(-1, 50))
        
        # Delete Button
        btn_label = f"إزالة الإعلان رقم {index}"
        self.delete_btn = wx.Button(parent, label="إزالة الإعلان", name=btn_label)
        self.delete_btn.Bind(wx.EVT_BUTTON, self.on_delete_click)
        
        self.row_sizer.Add(hdr_sizer, 0, wx.EXPAND | wx.BOTTOM, 5)
        self.row_sizer.Add(self.msg_text, 0, wx.BOTTOM, 2)
        self.row_sizer.Add(self.msg_input, 0, wx.EXPAND | wx.BOTTOM, 5)
        self.row_sizer.Add(self.delete_btn, 0, wx.ALIGN_LEFT)
        
        # Wrap in StaticBox for nice grouping
        self.box = wx.StaticBox(parent, label=f"الإعلان #{index}")
        self.box_sizer = wx.StaticBoxSizer(self.box, wx.VERTICAL)
        self.box_sizer.Add(self.row_sizer, 0, wx.EXPAND | wx.ALL, 5)
        
        self.sizer.Add(self.box_sizer, 0, wx.EXPAND | wx.ALL, 8)
        self.parent.Layout()

    def on_delete_click(self, event):
        if self.on_delete:
            self.on_delete(self)

    def destroy_widgets(self):
        self.title_text.Destroy()
        self.title_input.Destroy()
        self.id_text.Destroy()
        self.id_input.Destroy()
        self.msg_text.Destroy()
        self.msg_input.Destroy()
        self.delete_btn.Destroy()
        self.box.Destroy()


class CloudConfigFrame(wx.Frame):
    def __init__(self):
        super().__init__(
            parent=None, 
            title="محرر الإعدادات السحابية والإعلانات - Accessible Video Editor", 
            size=(800, 600)
        )
        
        # Enable Right-to-Left Layout for Arabic compatibility
        self.SetLayoutDirection(wx.Layout_RightToLeft)

        # Tab Control Notebook
        self.notebook = wx.Notebook(self)
        self.notebook.SetLayoutDirection(wx.Layout_RightToLeft)

        # ------------------ TAB 1: Devices ------------------
        self.tab_devices = wx.ScrolledWindow(self.notebook)
        self.tab_devices.SetScrollRate(0, 20)
        self.devices_main_sizer = wx.BoxSizer(wx.VERTICAL)
        
        title_text = wx.StaticText(self.tab_devices, label="إدارة معرّفات الأجهزة المسموح لها بالتطبيق", style=wx.ALIGN_CENTER)
        title_font = wx.Font(12, wx.FONTFAMILY_DEFAULT, wx.FONTSTYLE_NORMAL, wx.FONTWEIGHT_BOLD)
        title_text.SetFont(title_font)
        self.devices_main_sizer.Add(title_text, 0, wx.ALIGN_CENTER | wx.ALL, 15)
        
        self.devices_sizer = wx.BoxSizer(wx.VERTICAL)
        self.devices_main_sizer.Add(self.devices_sizer, 1, wx.EXPAND | wx.ALL, 10)
        
        self.add_device_btn = wx.Button(self.tab_devices, label="إضافة جهاز جديد +", name="إضافة جهاز جديد")
        self.add_device_btn.Bind(wx.EVT_BUTTON, self.on_add_device)
        self.devices_main_sizer.Add(self.add_device_btn, 0, wx.EXPAND | wx.LEFT | wx.RIGHT | wx.BOTTOM, 20)
        
        self.tab_devices.SetSizer(self.devices_main_sizer)
        self.notebook.AddPage(self.tab_devices, "الأجهزة المصرح لها")

        # ------------------ TAB 2: Announcements ------------------
        self.tab_announcements = wx.ScrolledWindow(self.notebook)
        self.tab_announcements.SetScrollRate(0, 20)
        self.ann_main_sizer = wx.BoxSizer(wx.VERTICAL)
        
        ann_title = wx.StaticText(self.tab_announcements, label="إدارة الإعلانات العامة والإشعارات الترويجية", style=wx.ALIGN_CENTER)
        ann_title.SetFont(title_font)
        self.ann_main_sizer.Add(ann_title, 0, wx.ALIGN_CENTER | wx.ALL, 15)
        
        self.ann_sizer = wx.BoxSizer(wx.VERTICAL)
        self.ann_main_sizer.Add(self.ann_sizer, 1, wx.EXPAND | wx.ALL, 10)
        
        self.add_ann_btn = wx.Button(self.tab_announcements, label="إضافة إعلان جديد +", name="إضافة إعلان جديد")
        self.add_ann_btn.Bind(wx.EVT_BUTTON, self.on_add_announcement)
        self.ann_main_sizer.Add(self.add_ann_btn, 0, wx.EXPAND | wx.LEFT | wx.RIGHT | wx.BOTTOM, 20)
        
        self.tab_announcements.SetSizer(self.ann_main_sizer)
        self.notebook.AddPage(self.tab_announcements, "إرسال الإعلانات العامة")

        # ------------------ Bottom Sizer (Save Buttons) ------------------
        main_layout_sizer = wx.BoxSizer(wx.VERTICAL)
        main_layout_sizer.Add(self.notebook, 1, wx.EXPAND)

        action_sizer = wx.BoxSizer(wx.HORIZONTAL)
        self.save_btn = wx.Button(self, label="حفظ التغييرات محلياً", name="حفظ التغييرات محلياً")
        self.save_btn.Bind(wx.EVT_BUTTON, self.on_save_local)
        
        self.upload_btn = wx.Button(self, label="حفظ ورفع التغييرات إلى السحابة", name="حفظ ورفع التغييرات إلى السحابة")
        self.upload_btn.Bind(wx.EVT_BUTTON, self.on_upload_cloud)
        
        action_sizer.Add(self.save_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 10)
        action_sizer.Add(self.upload_btn, 1, wx.EXPAND | wx.LEFT | wx.RIGHT, 10)
        
        main_layout_sizer.Add(action_sizer, 0, wx.EXPAND | wx.ALL, 15)
        self.SetSizer(main_layout_sizer)
        
        # Dynamic lists
        self.device_rows = []
        self.ann_rows = []
        
        self.config_data = {}
        self.load_config()
        self.Centre()

    def load_config(self):
        try:
            if not os.path.exists(CONFIG_FILE):
                self.add_device_row()
                self.add_ann_row()
                return

            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                self.config_data = json.load(f)

            # Whitelist Devices
            whitelist = self.config_data.get("whitelistedDevices", {})
            id_list = whitelist.get("btnVideoEditor", [])
            device_names = self.config_data.get("deviceNames", {})

            for uid in id_list:
                name = device_names.get(uid, "")
                self.add_device_row(name, uid)

            if not id_list:
                self.add_device_row()

            # Announcements
            ann_list = self.config_data.get("announcements", [])
            for ann in ann_list:
                self.add_ann_row(ann.get("title", ""), ann.get("message", ""), ann.get("id", ""))
            
            if not ann_list:
                self.add_ann_row()

        except Exception as e:
            wx.MessageBox(f"فشل تحميل الإعدادات: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)

    def add_device_row(self, name_val="", id_val=""):
        idx = len(self.device_rows) + 1
        row = DeviceRow(
            parent=self.tab_devices, 
            sizer=self.devices_sizer, 
            index=idx, 
            name_val=name_val, 
            id_val=id_val, 
            on_delete_callback=self.on_delete_device_row
        )
        self.device_rows.append(row)
        if not name_val:
            row.name_input.SetFocus()

    def on_add_device(self, event):
        self.add_device_row()

    def on_delete_device_row(self, row_obj):
        row_obj.destroy_widgets()
        if row_obj in self.device_rows:
            self.device_rows.remove(row_obj)
        self.tab_devices.Layout()
        self.refresh_devices_accessibility_names()

    def refresh_devices_accessibility_names(self):
        for i, row in enumerate(self.device_rows):
            idx = i + 1
            row.name_text.SetLabel(f"اسم صاحب الجهاز رقم {idx}:")
            row.name_input.SetName(f"اسم صاحب الجهاز رقم {idx}")
            row.id_text.SetLabel(f"معرّف الجهاز رقم {idx}:")
            row.id_input.SetName(f"معرّف الجهاز رقم {idx}")
            row.delete_btn.SetName(f"إزالة الجهاز رقم {idx}")
        self.tab_devices.Layout()

    def add_ann_row(self, title_val="", msg_val="", id_val=""):
        idx = len(self.ann_rows) + 1
        row = AnnouncementRow(
            parent=self.tab_announcements, 
            sizer=self.ann_sizer, 
            index=idx, 
            title_val=title_val, 
            msg_val=msg_val, 
            id_val=id_val, 
            on_delete_callback=self.on_delete_ann_row
        )
        self.ann_rows.append(row)
        if not title_val:
            row.title_input.SetFocus()

    def on_add_announcement(self, event):
        self.add_ann_row()

    def on_delete_ann_row(self, row_obj):
        row_obj.destroy_widgets()
        if row_obj in self.ann_rows:
            self.ann_rows.remove(row_obj)
        self.tab_announcements.Layout()
        self.refresh_ann_accessibility_names()

    def refresh_ann_accessibility_names(self):
        for i, row in enumerate(self.ann_rows):
            idx = i + 1
            row.box.SetLabel(f"الإعلان #{idx}")
            row.title_text.SetLabel(f"عنوان الإعلان رقم {idx}:")
            row.title_input.SetName(f"عنوان الإعلان رقم {idx}")
            row.id_input.SetName(f"معرّف الإعلان رقم {idx}")
            row.msg_input.SetName(f"محتوى الإعلان رقم {idx}")
            row.delete_btn.SetName(f"إزالة الإعلان رقم {idx}")
        self.tab_announcements.Layout()

    def get_paired_data(self):
        device_ids = []
        id_to_name_map = {}
        for row in self.device_rows:
            name = row.name_input.GetValue().strip()
            uid = row.id_input.GetValue().strip()
            if name and uid:
                device_ids.append(uid)
                id_to_name_map[uid] = name
        return device_ids, id_to_name_map

    def get_announcements_data(self):
        anns = []
        for row in self.ann_rows:
            t = row.title_input.GetValue().strip()
            m = row.msg_input.GetValue().strip()
            i = row.id_input.GetValue().strip()
            if t and m:
                anns.append({
                    "id": i if i else f"ann_{int(time.time()*1000)}",
                    "title": t,
                    "message": m
                })
        return anns

    def save_local_action(self):
        try:
            device_ids, id_to_name_map = self.get_paired_data()
            anns = self.get_announcements_data()

            if not os.path.exists(CONFIG_FILE):
                self.config_data = {"whitelistedDevices": {}}

            whitelist = self.config_data.get("whitelistedDevices", {})
            
            feature_keys = [
                "btnVideoEditor", "btnImageEditor", "btnWatermark", "btnCreateBlankImage",
                "btnVideoTrimmer", "btnSmartCut", "btnAudioEditor", "btnAudioStudio",
                "btnAiAnalysis", "btnStt", "btnOcr", "btnFastConverter", "btnBoostVolume",
                "btnExtractAudio", "btnCompressVideo", "btnMergeVideos", "btnReverseMedia",
                "btnSlideshowMaker", "btnTickerText", "btnBatchProcess", "btnSpeedControl",
                "btnNoiseReduction", "btnBackgroundMusic", "btnAudioNormalization",
                "btnAiSceneInspector", "btnAiVoiceDubbing", "btnAudioStemSeparator",
                "btnAutoShortsCreator", "btnCinematicLutShaders", "btnAiSceneAudioDescription",
                "btnSubtitlesOcrSrt"
            ]

            for key in feature_keys:
                whitelist[key] = device_ids

            self.config_data["whitelistedDevices"] = whitelist
            self.config_data["deviceNames"] = id_to_name_map
            self.config_data["announcements"] = anns

            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(self.config_data, f, indent=2, ensure_ascii=False)

            return True
        except Exception as e:
            wx.MessageBox(f"فشل الحفظ المحلي: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)
            return False

    def on_save_local(self, event):
        if self.save_local_action():
            wx.MessageBox("تم حفظ التغييرات محلياً بنجاح!", "نجاح", wx.OK | wx.ICON_INFORMATION)

    def on_upload_cloud(self, event):
        if not self.save_local_action():
            return

        try:
            subprocess.run(["git", "add", "cloud_config.json"], cwd=REPO_PATH, check=True, capture_output=True)
            subprocess.run(["git", "commit", "-m", "Update whitelisted devices and announcements via wxPython GUI"], cwd=REPO_PATH, capture_output=True)
            subprocess.run(["git", "push"], cwd=REPO_PATH, check=True, capture_output=True, text=True)
            wx.MessageBox("تم حفظ وتطبيق التغييرات على السحاب بنجاح!", "تم الرفع بنجاح", wx.OK | wx.ICON_INFORMATION)
        except subprocess.CalledProcessError as e:
            err_msg = e.stderr if e.stderr else str(e)
            if "nothing to commit" in err_msg or "clean" in err_msg:
                wx.MessageBox("الملفات مطابقة بالفعل للسحاب، لا توجد تغييرات جديدة لرفعها.", "تنبيه", wx.OK | wx.ICON_INFORMATION)
            else:
                wx.MessageBox(f"فشل الاتصال والرفع إلى GitHub:\n{err_msg}", "خطأ في الرفع", wx.OK | wx.ICON_ERROR)
        except Exception as e:
            wx.MessageBox(f"حدث خطأ غير متوقع: {str(e)}", "خطأ", wx.OK | wx.ICON_ERROR)


if __name__ == "__main__":
    app = wx.App()
    frame = CloudConfigFrame()
    frame.Show()
    app.MainLoop()
