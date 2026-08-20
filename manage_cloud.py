import os
import json
import subprocess
import tkinter as tk
from tkinter import messagebox
from tkinter import ttk

# Paths
REPO_PATH = r"D:\.gemini\antigravity\scratch\accVideoEditorReleases"
CONFIG_FILE = os.path.join(REPO_PATH, "cloud_config.json")

class CloudConfigManagerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("محرر الإعدادات السحابية - Accessible Video Editor")
        self.root.geometry("600x400")
        self.root.minsize(500, 300)
        
        # Make the layout responsive
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)

        # Set up a clean, high-contrast style for accessibility
        style = ttk.Style()
        style.theme_use("winnative")
        
        # Main container frame
        self.main_frame = ttk.Frame(root, padding="20")
        self.main_frame.grid(row=0, column=0, sticky="nsew")
        self.main_frame.columnconfigure(1, weight=1)

        # Instructions / Title Label
        self.title_label = ttk.Label(
            self.main_frame, 
            text="إدارة معرّفات الأجهزة المسموح لها باستخدام ميزات التطبيق",
            font=("Arial", 12, "bold"),
            anchor="center"
        )
        self.title_label.grid(row=0, column=0, columnspan=2, pady=(0, 20), sticky="ew")

        # Label and Entry for User 1 ID
        self.user1_label = ttk.Label(self.main_frame, text="معرّف الجهاز الأول (المستخدم الأساسي):", font=("Arial", 10))
        self.user1_label.grid(row=1, column=0, padx=10, pady=10, sticky="w")
        self.user1_entry = ttk.Entry(self.main_frame, font=("Arial", 10), width=40)
        self.user1_entry.grid(row=1, column=1, padx=10, pady=10, sticky="ew")

        # Label and Entry for User 2 ID
        self.user2_label = ttk.Label(self.main_frame, text="معرّف الجهاز الثاني (رامز ماجد):", font=("Arial", 10))
        self.user2_label.grid(row=2, column=0, padx=10, pady=10, sticky="w")
        self.user2_entry = ttk.Entry(self.main_frame, font=("Arial", 10), width=40)
        self.user2_entry.grid(row=2, column=1, padx=10, pady=10, sticky="ew")

        # Button Frame
        self.btn_frame = ttk.Frame(self.main_frame, padding="10")
        self.btn_frame.grid(row=3, column=0, columnspan=2, pady=20, sticky="ew")
        self.btn_frame.columnconfigure(0, weight=1)
        self.btn_frame.columnconfigure(1, weight=1)

        # Apply and Save Button
        self.save_btn = ttk.Button(
            self.btn_frame, 
            text="حفظ التغييرات محلياً", 
            command=self.save_config
        )
        self.save_btn.grid(row=0, column=0, padx=10, pady=10, sticky="ew")

        # Upload/Push Button
        self.upload_btn = ttk.Button(
            self.btn_frame, 
            text="رفع التغييرات إلى السحابة (GitHub)", 
            command=self.upload_config
        )
        self.upload_btn.grid(row=0, column=1, padx=10, pady=10, sticky="ew")

        # Status Bar
        self.status_var = tk.StringVar(value="جاهز.")
        self.status_label = ttk.Label(
            self.main_frame, 
            textvariable=self.status_var, 
            font=("Arial", 9, "italic"),
            foreground="green",
            anchor="center"
        )
        self.status_label.grid(row=4, column=0, columnspan=2, pady=(10, 0), sticky="ew")

        # Load configuration on startup
        self.load_config()

    def load_config(self):
        try:
            if not os.path.exists(CONFIG_FILE):
                raise FileNotFoundError(f"الملف غير موجود في المسار: {CONFIG_FILE}")

            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                self.config_data = json.load(f)

            # Extract IDs from the first feature whitelisted list
            whitelist = self.config_data.get("whitelistedDevices", {})
            first_feature_list = whitelist.get("btnVideoEditor", [])
            
            id1 = first_feature_list[0] if len(first_feature_list) > 0 else ""
            id2 = first_feature_list[1] if len(first_feature_list) > 1 else ""

            # Populate inputs
            self.user1_entry.delete(0, tk.END)
            self.user1_entry.insert(0, id1)

            self.user2_entry.delete(0, tk.END)
            self.user2_entry.insert(0, id2)

            self.status_var.set("تم تحميل الإعدادات الحالية بنجاح.")
        except Exception as e:
            messagebox.showerror("خطأ", f"فشل تحميل ملف الإعدادات: {str(e)}")
            self.status_var.set("فشل تحميل البيانات.")

    def save_config(self):
        try:
            id1 = self.user1_entry.get().strip()
            id2 = self.user2_entry.get().strip()

            if not id1 or not id2:
                if not messagebox.askyesno("تحذير", "أحد حقول المعرّفات فارغ. هل تريد الاستمرار في الحفظ؟"):
                    return

            new_list = [id1, id2]

            # Update all keys inside whitelistedDevices
            whitelist = self.config_data.get("whitelistedDevices", {})
            for key in whitelist.keys():
                whitelist[key] = new_list

            # Write back to file
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(self.config_data, f, indent=2, ensure_ascii=False)

            self.status_var.set("تم حفظ التغييرات محلياً بنجاح.")
            messagebox.showinfo("نجاح", "تم حفظ التغييرات محلياً بنجاح! تذكر الضغط على زر الرفع لتصبح سارية في السحابة.")
        except Exception as e:
            messagebox.showerror("خطأ", f"فشل حفظ التغييرات: {str(e)}")

    def upload_config(self):
        # Save first just in case
        self.save_config()
        self.status_var.set("جاري الرفع إلى GitHub...")
        self.root.update_idletasks()

        try:
            # Run Git commands
            # Add changes
            subprocess.run(["git", "add", "cloud_config.json"], cwd=REPO_PATH, check=True, capture_output=True)
            # Commit changes
            subprocess.run(["git", "commit", "-m", "Update whitelisted devices via Config Manager GUI"], cwd=REPO_PATH, capture_output=True)
            # Push changes
            result = subprocess.run(["git", "push"], cwd=REPO_PATH, check=True, capture_output=True, text=True)

            self.status_var.set("تم رفع التحديثات إلى السحابة بنجاح.")
            messagebox.showinfo("تم الرفع بنجاح", "تم رفع وتطبيق التغييرات على السحابة بنجاح!")
        except subprocess.CalledProcessError as e:
            err_msg = e.stderr if e.stderr else str(e)
            if "nothing to commit" in err_msg or "clean" in err_msg:
                self.status_var.set("لا توجد تغييرات جديدة لرفعها.")
                messagebox.showinfo("تنبيه", "لا توجد أي تغييرات جديدة لرفعها، الملف متطابق بالفعل مع السحابة.")
            else:
                self.status_var.set("فشل الرفع إلى GitHub.")
                messagebox.showerror("خطأ في الرفع", f"حدث خطأ أثناء الاتصال والرفع إلى GitHub:\n{err_msg}")
        except Exception as e:
            self.status_var.set("فشل الرفع.")
            messagebox.showerror("خطأ", f"حدث خطأ غير متوقع: {str(e)}")

if __name__ == "__main__":
    root = tk.Tk()
    app = CloudConfigManagerApp(root)
    root.mainloop()
