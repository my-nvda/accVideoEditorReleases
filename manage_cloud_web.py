import os
import json
import subprocess
import webbrowser
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler

# Paths
REPO_PATH = r"D:\.gemini\antigravity\scratch\accVideoEditorReleases"
CONFIG_FILE = os.path.join(REPO_PATH, "cloud_config.json")
PORT = 8555

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>محرر الإعدادات السحابية - Accessible Video Editor</title>
    <style>
        body {
            font-family: system-ui, -apple-system, sans-serif;
            background-color: #f3f4f6;
            color: #1f2937;
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 90vh;
        }
        main {
            background: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
            max-width: 700px;
            width: 100%;
        }
        h1 {
            font-size: 1.5rem;
            margin-bottom: 20px;
            text-align: center;
            color: #111827;
        }
        .form-group {
            margin-bottom: 20px;
        }
        .group-label {
            display: block;
            font-weight: 600;
            margin-bottom: 12px;
            font-size: 1.05rem;
        }
        .id-item {
            display: flex;
            gap: 12px;
            margin-bottom: 12px;
            align-items: center;
            background-color: #f9fafb;
            padding: 10px;
            border: 1px solid #e5e7eb;
            border-radius: 6px;
        }
        .input-wrapper {
            display: flex;
            flex-direction: column;
            flex: 1;
            gap: 4px;
        }
        .input-wrapper label {
            font-size: 0.85rem;
            font-weight: 600;
            color: #4b5563;
        }
        input[type="text"] {
            width: 100%;
            padding: 10px;
            border: 1px solid #d1d5db;
            border-radius: 6px;
            font-size: 0.95rem;
            box-sizing: border-box;
        }
        input[type="text"]:focus {
            outline: 2px solid #2563eb;
            background-color: white;
        }
        button {
            padding: 10px 15px;
            font-size: 1rem;
            font-weight: 600;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            transition: background 0.2s;
        }
        .btn-add {
            background-color: #10b981;
            color: white;
            margin-top: 5px;
            width: 100%;
        }
        .btn-add:hover, .btn-add:focus {
            background-color: #059669;
            outline: 3px solid #a7f3d0;
        }
        .btn-remove {
            background-color: #ef4444;
            color: white;
            align-self: flex-end;
            margin-bottom: 2px;
        }
        .btn-remove:hover, .btn-remove:focus {
            background-color: #dc2626;
            outline: 3px solid #fecaca;
        }
        .btn-group {
            display: flex;
            gap: 12px;
            margin-top: 30px;
            border-top: 1px solid #e5e7eb;
            padding-top: 20px;
        }
        .btn-save {
            background-color: #4b5563;
            color: white;
            flex: 1;
        }
        .btn-save:hover, .btn-save:focus {
            background-color: #374151;
            outline: 3px solid #9ca3af;
        }
        .btn-upload {
            background-color: #2563eb;
            color: white;
            flex: 1.2;
        }
        .btn-upload:hover, .btn-upload:focus {
            background-color: #1d4ed8;
            outline: 3px solid #93c5fd;
        }
        .status {
            margin-top: 20px;
            padding: 12px;
            border-radius: 6px;
            font-weight: 600;
            text-align: center;
            display: none;
        }
        .status.success {
            background-color: #def7ec;
            color: #03543f;
            display: block;
        }
        .status.error {
            background-color: #fde8e8;
            color: #9b1c1c;
            display: block;
        }
    </style>
</head>
<body>
    <main role="main">
        <h1>إدارة معرّفات الأجهزة المسموح لها</h1>
        
        <form id="configForm" action="/save" method="POST">
            <div class="form-group">
                <span class="group-label">قائمة الأجهزة المصرّح لها:</span>
                <div id="idsContainer">
                    <!-- Dynamic inputs loaded by JS -->
                </div>
                <button type="button" id="btnAddId" class="btn-add">إضافة جهاز جديد +</button>
            </div>

            <div class="btn-group">
                <button type="submit" name="action" value="save" class="btn-save">حفظ التغييرات محلياً</button>
                <button type="submit" name="action" value="upload" class="btn-upload">حفظ ورفع التغييرات إلى السحابة</button>
            </div>
        </form>

        {status_html}
    </main>

    <script>
        const initialDevices = {initial_devices_json};
        const container = document.getElementById('idsContainer');
        const btnAdd = document.getElementById('btnAddId');

        let inputCounter = 0;

        function addInput(name = "", idValue = "") {{
            inputCounter++;
            const div = document.createElement('div');
            div.className = 'id-item';

            // Name Field Wrap
            const nameWrap = document.createElement('div');
            nameWrap.className = 'input-wrapper';
            const nameLabel = document.createElement('label');
            nameLabel.setAttribute('for', 'deviceName_' + inputCounter);
            nameLabel.textContent = 'اسم صاحب الجهاز:';
            const nameInput = document.createElement('input');
            nameInput.type = 'text';
            nameInput.name = 'device_names';
            nameInput.value = name;
            nameInput.placeholder = 'مثال: عبير';
            nameInput.required = true;
            nameInput.id = 'deviceName_' + inputCounter;
            nameWrap.appendChild(nameLabel);
            nameWrap.appendChild(nameInput);

            // ID Field Wrap
            const idWrap = document.createElement('div');
            idWrap.className = 'input-wrapper';
            const idLabel = document.createElement('label');
            idLabel.setAttribute('for', 'deviceId_' + inputCounter);
            idLabel.textContent = 'معرّف الجهاز (ID):';
            const idInput = document.createElement('input');
            idInput.type = 'text';
            idInput.name = 'device_ids';
            idInput.value = idValue;
            idInput.placeholder = 'مثال: 062c80e6a4faab64';
            idInput.required = true;
            idInput.id = 'deviceId_' + inputCounter;
            idWrap.appendChild(idLabel);
            idWrap.appendChild(idInput);

            const btnRemove = document.createElement('button');
            btnRemove.type = 'button';
            btnRemove.className = 'btn-remove';
            btnRemove.textContent = 'إزالة';
            btnRemove.setAttribute('aria-label', 'إزالة جهاز ' + (name || ('رقم ' + inputCounter)));
            btnRemove.onclick = () => {{
                div.remove();
            }};

            div.appendChild(nameWrap);
            div.appendChild(idWrap);
            div.appendChild(btnRemove);
            container.appendChild(div);
            
            // Focus new name input for immediate editing
            if (name === "") {{
                nameInput.focus();
            }}
        }}

        // Initialize with loaded values
        if (initialDevices.length === 0) {{
            addInput();
        }} else {{
            initialDevices.forEach(d => addInput(d.name, d.id));
        }}

        btnAdd.onclick = () => addInput();
    </script>
</body>
</html>
"""

class ConfigHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def do_GET(self):
        if self.path == "/":
            self.send_response(200)
            self.send_header("Content-type", "text/html; charset=utf-8")
            self.end_headers()
            
            # Load current values from cloud_config.json
            devices_list = []
            try:
                if os.path.exists(CONFIG_FILE):
                    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    
                    whitelist = data.get("whitelistedDevices", {})
                    id_list = whitelist.get("btnVideoEditor", [])
                    device_names = data.get("deviceNames", {})

                    for uid in id_list:
                        name = device_names.get(uid, "")
                        devices_list.append({"id": uid, "name": name})
            except Exception:
                pass

            response_html = HTML_TEMPLATE.replace("{initial_devices_json}", json.dumps(devices_list)).replace("{status_html}", "")
            self.wfile.write(response_html.encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/save":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length).decode('utf-8')
            fields = urllib.parse.parse_qs(post_data)

            # Get lists of device names and ids
            device_names = fields.get('device_names', [])
            device_ids = fields.get('device_ids', [])

            # Clean and pair them up
            paired_devices = []
            id_to_name_map = {}
            for name, uid in zip(device_names, device_ids):
                name_clean = name.strip()
                uid_clean = uid.strip()
                if name_clean and uid_clean:
                    paired_devices.append({"id": uid_clean, "name": name_clean})
                    id_to_name_map[uid_clean] = name_clean

            clean_ids = [d["id"] for d in paired_devices]
            action = fields.get('action', ['save'])[0]

            status_class = ""
            status_message = ""

            try:
                # 1. Update file locally
                if os.path.exists(CONFIG_FILE):
                    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                else:
                    data = {"whitelistedDevices": {}}

                whitelist = data.get("whitelistedDevices", {})
                
                # We need all these feature keys updated with the new ID list
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
                    whitelist[key] = clean_ids

                data["whitelistedDevices"] = whitelist
                data["deviceNames"] = id_to_name_map

                with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                    json.dump(data, f, indent=2)

                status_class = "success"
                status_message = "تم حفظ التغييرات محلياً بنجاح!"

                # 2. If action is upload, commit and push to Git
                if action == "upload":
                    # Git commands
                    subprocess.run(["git", "add", "cloud_config.json"], cwd=REPO_PATH, check=True, capture_output=True)
                    subprocess.run(["git", "commit", "-m", "Update whitelisted devices and names via Web GUI"], cwd=REPO_PATH, capture_output=True)
                    push_res = subprocess.run(["git", "push"], cwd=REPO_PATH, check=True, capture_output=True, text=True)
                    status_message = "تم حفظ التغييرات ورفعها وتطبيقها على السحابة بنجاح!"

            except subprocess.CalledProcessError as e:
                err_msg = e.stderr if e.stderr else str(e)
                if "nothing to commit" in err_msg or "clean" in err_msg:
                    status_class = "success"
                    status_message = "تم الحفظ محلياً. لا توجد تغييرات جديدة لرفعها على السحابة (الملفات متطابقة بالفعل)."
                else:
                    status_class = "error"
                    status_message = f"فشل الرفع إلى GitHub: {err_msg}"
            except Exception as e:
                status_class = "error"
                status_message = f"حدث خطأ: {str(e)}"

            # Render page back with status message
            self.send_response(200)
            self.send_header("Content-type", "text/html; charset=utf-8")
            self.end_headers()

            status_html = f'<div class="status {status_class}" role="alert">{status_message}</div>'
            response_html = HTML_TEMPLATE.replace("{initial_devices_json}", json.dumps(paired_devices)).replace("{status_html}", status_html)
            self.wfile.write(response_html.encode("utf-8"))

def run():
    print(f"Starting server on http://localhost:{PORT}")
    webbrowser.open(f"http://localhost:{PORT}")
    server = HTTPServer(('localhost', PORT), ConfigHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        server.server_close()

if __name__ == "__main__":
    run()
