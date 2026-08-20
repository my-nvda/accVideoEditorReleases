import os
import json
import shutil
import subprocess
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# Path configurations
RELEASES_REPO = r"D:\.gemini\antigravity\scratch\accVideoEditorReleases"
SOURCE_REPO = r"D:\.gemini\antigravity\scratch\AccessibleVideoEditor"
REPOS_CONFIG_FILE = os.path.join(RELEASES_REPO, "repos_config.json")
SCRATCH_DIR = r"D:\.gemini\antigravity\scratch"

# Feature list
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

# State
active_repo_index = 0

def load_repos():
    default_repos = [
        {"name": "مستودع ملفات التحديث والإصدارات", "path": RELEASES_REPO, "url": ""},
        {"name": "مستودع الكود المصدري للتطبيق", "path": SOURCE_REPO, "url": ""}
    ]
    try:
        if os.path.exists(REPOS_CONFIG_FILE):
            with open(REPOS_CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        else:
            os.makedirs(os.path.dirname(REPOS_CONFIG_FILE), exist_ok=True)
            with open(REPOS_CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(default_repos, f, indent=2, ensure_ascii=False)
            return default_repos
    except Exception:
        return default_repos

def save_repos(repos):
    try:
        with open(REPOS_CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(repos, f, indent=2, ensure_ascii=False)
    except Exception:
        pass

def get_active_repo_path():
    repos = load_repos()
    global active_repo_index
    if 0 <= active_repo_index < len(repos):
        return repos[active_repo_index]["path"]
    return None

def get_active_branch(repo_path):
    try:
        res = subprocess.run(["git", "branch", "--show-current"], cwd=repo_path, capture_output=True, text=True)
        if res.returncode == 0 and res.stdout.strip():
            return res.stdout.strip()
    except Exception:
        pass
    return "main"

class ReleaseManagerAPIHandler(BaseHTTPRequestHandler):
    def send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed_url = urlparse(self.path)
        path = parsed_url.path

        # Static assets serving
        if path == "/" or path == "/index.html":
            self.serve_static("web/index.html", "text/html")
            return
        elif path == "/app.js":
            self.serve_static("web/app.js", "application/javascript")
            return
        elif path == "/styles.css":
            self.serve_static("web/styles.css", "text/css")
            return

        # APIs
        if path == "/api/repos":
            repos = load_repos()
            for r in repos:
                if "url" not in r or not r["url"]:
                    if os.path.exists(r["path"]):
                        try:
                            res = subprocess.run(["git", "config", "--get", "remote.origin.url"], cwd=r["path"], capture_output=True, text=True)
                            if res.returncode == 0:
                                r["url"] = res.stdout.strip()
                        except Exception:
                            r["url"] = ""
            self.send_json({"repos": repos, "active_index": active_repo_index})
            
        elif path == "/api/features":
            self.send_json({"features": ALL_FEATURES})
            
        elif path == "/api/cloud_config":
            repo_path = get_active_repo_path()
            config_path = os.path.join(repo_path, "cloud_config.json")
            if not os.path.exists(config_path):
                for r in load_repos():
                    alt_path = os.path.join(r["path"], "cloud_config.json")
                    if os.path.exists(alt_path):
                        config_path = alt_path
                        break
            if not os.path.exists(config_path):
                self.send_json({"devices": []})
                return
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                whitelist = data.get("whitelistedDevices", {})
                device_names = data.get("deviceNames", {})
                all_uids = set(device_names.keys())
                for key, uids in whitelist.items():
                    for u in uids:
                        all_uids.add(u)
                
                devices = []
                for uid in sorted(list(all_uids)):
                    checked = [key for key, uids in whitelist.items() if uid in uids]
                    devices.append({
                        "id": uid,
                        "name": device_names.get(uid, ""),
                        "features": checked
                    })
                import base64
                raw_token = data.get("github_token", "")
                decoded_token = raw_token
                if raw_token and not raw_token.startswith("ghp_"):
                    try:
                        reversed_str = base64.b64decode(raw_token.encode("utf-8")).decode("utf-8")
                        decoded_token = reversed_str[::-1]
                    except Exception:
                        pass
                self.send_json({
                    "devices": devices,
                    "announcements": data.get("announcements", []),
                    "github_token": decoded_token,
                    "github_repo": data.get("github_repo", "")
                })
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/analytics":
            repo_path = get_active_repo_path()
            stats_dirs = [os.path.join(repo_path, "device_stats")]
            for r in load_repos():
                alt_dir = os.path.join(r["path"], "device_stats")
                if alt_dir not in stats_dirs:
                    stats_dirs.append(alt_dir)
            
            stats_list = []
            seen_files = set()
            for stats_dir in stats_dirs:
                if os.path.exists(stats_dir):
                    for f_name in os.listdir(stats_dir):
                        if f_name.endswith(".json") and f_name not in seen_files:
                            seen_files.add(f_name)
                            f_path = os.path.join(stats_dir, f_name)
                            try:
                                with open(f_path, "r", encoding="utf-8") as f:
                                    stats_list.append(json.load(f))
                            except Exception:
                                pass
            self.send_json({"stats": stats_list})

        elif path == "/api/crashes":
            repo_path = get_active_repo_path()
            crashes_dirs = [os.path.join(repo_path, "crash_reports")]
            for r in load_repos():
                alt_dir = os.path.join(r["path"], "crash_reports")
                if alt_dir not in crashes_dirs:
                    crashes_dirs.append(alt_dir)
            
            crashes_list = []
            seen_files = set()
            for crashes_dir in crashes_dirs:
                if os.path.exists(crashes_dir):
                    for f_name in os.listdir(crashes_dir):
                        if f_name.endswith(".json") and f_name not in seen_files:
                            seen_files.add(f_name)
                            f_path = os.path.join(crashes_dir, f_name)
                            try:
                                with open(f_path, "r", encoding="utf-8") as f:
                                    crash_data = json.load(f)
                                    crash_data["filename"] = f_name
                                    crashes_list.append(crash_data)
                            except Exception:
                                pass
            crashes_list.sort(key=lambda x: x.get("filename", ""), reverse=True)
            self.send_json({"crashes": crashes_list})

        elif path == "/api/app_update":
            repo_path = get_active_repo_path()
            update_path = os.path.join(repo_path, "update.json")
            if not os.path.exists(update_path):
                for r in load_repos():
                    alt_path = os.path.join(r["path"], "update.json")
                    if os.path.exists(alt_path):
                        update_path = alt_path
                        break
            if not os.path.exists(update_path):
                self.send_json({"versionCode": "", "versionName": "", "downloadUrl": "", "releaseNotes": ""})
                return
            try:
                with open(update_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                # Unescape newlines
                notes = data.get("releaseNotes", "").replace("\\n", "\n")
                self.send_json({
                    "versionCode": data.get("versionCode", ""),
                    "versionName": data.get("versionName", ""),
                    "downloadUrl": data.get("downloadUrl", ""),
                    "releaseNotes": notes
                })
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/tags":
            repo_path = get_active_repo_path()
            local_tags = []
            remote_tags = []
            try:
                # Local tags
                res = subprocess.run(["git", "tag"], cwd=repo_path, capture_output=True, text=True)
                if res.returncode == 0:
                    local_tags = [t.strip() for t in res.stdout.split("\n") if t.strip()]
                # Remote tags
                res_rem = subprocess.run(["git", "ls-remote", "--tags", "origin"], cwd=repo_path, capture_output=True, text=True)
                if res_rem.returncode == 0:
                    r_tags = set()
                    for line in res_rem.stdout.split("\n"):
                        if "refs/tags/" in line:
                            t = line.split("refs/tags/")[1].strip()
                            if t.endswith("^{}"):
                                t = t[:-3]
                            r_tags.add(t)
                    remote_tags = sorted(list(r_tags))
                self.send_json({"local": local_tags, "remote": remote_tags})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/files":
            repo_path = get_active_repo_path()
            local_files = []
            remote_files = []
            exclude = {".git", ".gradle", ".idea", "build", "app/build", ".kotlin", "local.properties"}
            try:
                # Local files
                for f in sorted(os.listdir(repo_path)):
                    if f in exclude:
                        continue
                    is_dir = os.path.isdir(os.path.join(repo_path, f))
                    local_files.append({"name": f, "type": "folder" if is_dir else "file"})
                # Remote files
                branch = get_active_branch(repo_path)
                res = subprocess.run(["git", "ls-tree", f"origin/{branch}"], cwd=repo_path, capture_output=True, text=True)
                if res.returncode == 0:
                    for line in res.stdout.split("\n"):
                        if not line.strip():
                            continue
                        parts = line.split("\t", 1)
                        if len(parts) == 2:
                            meta, name = parts
                            meta_parts = meta.split()
                            if len(meta_parts) >= 2 and name not in exclude:
                                remote_files.append({
                                    "name": name,
                                    "type": "folder" if meta_parts[1] == "tree" else "file"
                                })
                self.send_json({"local": local_files, "remote": remote_files, "branch": branch})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/file_content":
            repo_path = get_active_repo_path()
            query = parse_qs(parsed_url.query)
            filename = query.get("file", [""])[0]
            if not filename or "/" in filename or "\\" in filename:
                self.send_json({"error": "Invalid file path"}, 400)
                return
            full_path = os.path.join(repo_path, filename)
            try:
                with open(full_path, "r", encoding="utf-8") as f:
                    content = f.read()
                self.send_json({"content": content})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

    def do_POST(self):
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        
        # Read body
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8') if content_length > 0 else ""
        data = {}
        if body:
            try:
                data = json.loads(body)
            except Exception:
                pass

        if path == "/api/active_repo":
            global active_repo_index
            active_repo_index = int(data.get("index", 0))
            self.send_json({"success": True})

        elif path == "/api/repos":
            name = data.get("name", "").strip()
            url = data.get("url", "").strip()
            if not name or not url:
                self.send_json({"error": "Name and URL are required"}, 400)
                return
            
            safe_folder = "".join([c if c.isalnum() else "_" for c in name])
            target_path = os.path.join(SCRATCH_DIR, safe_folder)
            
            if os.path.exists(target_path):
                self.send_json({"error": "A repository with this folder name already exists locally!"}, 400)
                return
                
            try:
                res = subprocess.run(["git", "clone", url, target_path], capture_output=True, text=True)
                if res.returncode == 0:
                    repos = load_repos()
                    repos.append({"name": name, "path": target_path, "url": url})
                    save_repos(repos)
                    active_repo_index = len(repos) - 1
                    self.send_json({"success": True, "index": active_repo_index})
                else:
                    self.send_json({"error": res.stderr}, 400)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/repos/edit":
            index = int(data.get("index", -1))
            name = data.get("name", "").strip()
            new_path = data.get("path", "").strip()
            new_url = data.get("url", "").strip()
            repos = load_repos()
            if index < 0 or index >= len(repos) or not name or not new_path or not new_url:
                self.send_json({"error": "Invalid inputs"}, 400)
                return
            repos[index]["name"] = name
            repos[index]["path"] = new_path
            repos[index]["url"] = new_url
            save_repos(repos)
            
            # Update remote URL in Git via git remote set-url origin <new_url>
            if os.path.exists(new_path):
                try:
                    subprocess.run(["git", "remote", "set-url", "origin", new_url], cwd=new_path, capture_output=True)
                except Exception:
                    pass
            self.send_json({"success": True})

        elif path == "/api/repos/delete":
            index = int(data.get("index", -1))
            delete_disk = bool(data.get("delete_disk", False))
            repos = load_repos()
            if index < 0 or index >= len(repos):
                self.send_json({"error": "Invalid index"}, 400)
                return
            
            removed = repos.pop(index)
            save_repos(repos)
            
            if delete_disk:
                try:
                    if os.path.exists(removed["path"]):
                        shutil.rmtree(removed["path"])
                except Exception as e:
                    self.send_json({"success": True, "warning": f"Unregistered repo, but failed deleting disk folder: {str(e)}"})
                    return
            
            active_repo_index = 0 if repos else -1
            self.send_json({"success": True, "active_index": active_repo_index})

        elif path == "/api/cloud_config":
            devices = data.get("devices", [])
            announcements = data.get("announcements", [])
            github_token = data.get("github_token", "").strip()
            github_token_encoded = github_token
            if github_token.startswith("ghp_"):
                import base64
                try:
                    reversed_token = github_token[::-1]
                    github_token_encoded = base64.b64encode(reversed_token.encode("utf-8")).decode("utf-8")
                except Exception:
                    pass
            github_repo = data.get("github_repo", "").strip()
            upload = bool(data.get("upload", False))
            repo_path = get_active_repo_path()
            config_path = os.path.join(repo_path, "cloud_config.json")
            if not os.path.exists(config_path):
                for r in load_repos():
                    alt_path = os.path.join(r["path"], "cloud_config.json")
                    if os.path.exists(alt_path):
                        config_path = alt_path
                        repo_path = r["path"]
                        break
            try:
                config_data = {}
                if os.path.exists(config_path):
                    with open(config_path, "r", encoding="utf-8") as f:
                        config_data = json.load(f)
                
                whitelist = {}
                for key, label in ALL_FEATURES:
                    whitelist[key] = []
                    
                device_names = {}
                for dev in devices:
                    uid = dev["id"]
                    device_names[uid] = dev["name"]
                    for feat in dev.get("features", []):
                        if feat in whitelist:
                            whitelist[feat].append(uid)
                            
                config_data["whitelistedDevices"] = whitelist
                config_data["deviceNames"] = device_names
                config_data["announcements"] = announcements
                config_data["github_token"] = github_token_encoded
                config_data["github_repo"] = github_repo
                config_data["telemetryConfig"] = {
                    "token": github_token_encoded,
                    "repo": github_repo
                }
                
                with open(config_path, "w", encoding="utf-8") as f:
                    json.dump(config_data, f, indent=2, ensure_ascii=False)
                
                if upload:
                    subprocess.run(["git", "add", "cloud_config.json"], cwd=repo_path, check=True)
                    subprocess.run(["git", "commit", "-m", "Update cloud config via Web interface"], cwd=repo_path)
                    branch = get_active_branch(repo_path)
                    subprocess.run(["git", "push", "origin", branch], cwd=repo_path, check=True)
                
                self.send_json({"success": True})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/app_update":
            vc = int(data.get("versionCode", 0))
            vn = data.get("versionName", "").strip()
            url = data.get("downloadUrl", "").strip()
            notes = data.get("releaseNotes", "")
            upload = bool(data.get("upload", False))
            
            repo_path = get_active_repo_path()
            update_path = os.path.join(repo_path, "update.json")
            if not os.path.exists(update_path):
                for r in load_repos():
                    alt_path = os.path.join(r["path"], "update.json")
                    if os.path.exists(alt_path):
                        update_path = alt_path
                        repo_path = r["path"]
                        break
            try:
                escaped_notes = notes.replace("\n", "\\n")
                update_data = {
                    "versionCode": vc,
                    "versionName": vn,
                    "downloadUrl": url,
                    "releaseNotes": escaped_notes
                }
                with open(update_path, "w", encoding="utf-8") as f:
                    json.dump(update_data, f, indent=2, ensure_ascii=False)
                    
                if upload:
                    subprocess.run(["git", "add", "update.json"], cwd=repo_path, check=True)
                    subprocess.run(["git", "commit", "-m", f"Update release info for version {vn} via Web interface"], cwd=repo_path)
                    branch = get_active_branch(repo_path)
                    subprocess.run(["git", "push", "origin", branch], cwd=repo_path, check=True)
                
                self.send_json({"success": True})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/tags":
            tag = data.get("tag", "").strip()
            msg = data.get("message", "").strip() or f"Release {tag}"
            repo_path = get_active_repo_path()
            try:
                res = subprocess.run(["git", "tag", "-a", tag, "-m", msg], cwd=repo_path, capture_output=True, text=True)
                if res.returncode != 0:
                    self.send_json({"error": res.stderr}, 400)
                    return
                # Push tag
                res_push = subprocess.run(["git", "push", "origin", tag], cwd=repo_path, capture_output=True, text=True)
                if res_push.returncode != 0:
                    self.send_json({"success": True, "warning": f"Local tag created, but push to origin failed: {res_push.stderr}"})
                else:
                    self.send_json({"success": True})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/tags/delete":
            tag = data.get("tag", "").strip()
            location = data.get("location", "local")
            repo_path = get_active_repo_path()
            try:
                if location == "local":
                    res = subprocess.run(["git", "tag", "-d", tag], cwd=repo_path, capture_output=True, text=True)
                else:
                    res = subprocess.run(["git", "push", "origin", "--delete", tag], cwd=repo_path, capture_output=True, text=True)
                
                if res.returncode == 0:
                    self.send_json({"success": True})
                else:
                    self.send_json({"error": res.stderr}, 400)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/git_pull":
            mode = data.get("mode", "files")
            repos = load_repos()
            errors = []
            output_logs = []
            for r in repos:
                r_path = r.get("path")
                if r_path and os.path.exists(r_path):
                    try:
                        if mode == "files":
                            res = subprocess.run(["git", "pull"], cwd=r_path, capture_output=True, text=True)
                        else:
                            res = subprocess.run(["git", "fetch", "--tags"], cwd=r_path, capture_output=True, text=True)
                        
                        if res.returncode != 0:
                            errors.append(f"{r['name']}: {res.stderr or res.stdout}")
                        else:
                            output_logs.append(f"{r['name']}: {res.stdout.strip()}")
                    except Exception as e:
                        errors.append(f"{r['name']}: {str(e)}")
            
            if errors:
                self.send_json({"error": "\n".join(errors)}, 400)
            else:
                self.send_json({"success": True, "stdout": "\n".join(output_logs)})

        elif path == "/api/crashes/resolve":
            filename = data.get("filename", "")
            upload = bool(data.get("upload", False))
            repo_path = get_active_repo_path()
            file_path = os.path.join(repo_path, "crash_reports", filename)
            
            if not filename or "/" in filename or "\\" in filename:
                self.send_json({"error": "Invalid filename"}, 400)
                return
                
            if not os.path.exists(file_path):
                for r in load_repos():
                    alt_path = os.path.join(r["path"], "crash_reports", filename)
                    if os.path.exists(alt_path):
                        file_path = alt_path
                        repo_path = r["path"]
                        break
            
            if not os.path.exists(file_path):
                self.send_json({"error": "File not found"}, 404)
                return
                
            try:
                os.remove(file_path)
                
                try:
                    subprocess.run(["git", "rm", os.path.join("crash_reports", filename)], cwd=repo_path, capture_output=True)
                    subprocess.run(["git", "commit", "-m", f"Resolve crash report {filename} via Web interface"], cwd=repo_path, capture_output=True)
                    if upload:
                        branch = get_active_branch(repo_path)
                        subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True)
                except Exception:
                    pass
                    
                self.send_json({"success": True})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/git_pull":
            repos = load_repos()
            errors = []
            output_logs = []
            for r in repos:
                r_path = r.get("path")
                if r_path and os.path.exists(r_path):
                    try:
                        res = subprocess.run(["git", "pull"], cwd=r_path, capture_output=True, text=True)
                        if res.returncode != 0:
                            errors.append(f"{r['name']}: {res.stderr or res.stdout}")
                        else:
                            output_logs.append(f"{r['name']}: {res.stdout.strip()}")
                    except Exception as e:
                        errors.append(f"{r['name']}: {str(e)}")
            if errors:
                self.send_json({"error": "\n".join(errors)}, 400)
            else:
                self.send_json({"success": True, "stdout": "\n".join(output_logs)})

        elif path == "/api/save_file":
            repo_path = get_active_repo_path()
            filename = data.get("file", "").strip()
            content = data.get("content", "")
            if not filename or "/" in filename or "\\" in filename:
                self.send_json({"error": "Invalid filename"}, 400)
                return
            try:
                full_path = os.path.join(repo_path, filename)
                with open(full_path, "w", encoding="utf-8") as f:
                    f.write(content)
                self.send_json({"success": True})
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/delete_file":
            repo_path = get_active_repo_path()
            filename = data.get("file", "").strip()
            location = data.get("location", "local")
            if not filename or "/" in filename or "\\" in filename:
                self.send_json({"error": "Invalid filename"}, 400)
                return
            try:
                if location == "local":
                    full_path = os.path.join(repo_path, filename)
                    if os.path.isdir(full_path):
                        shutil.rmtree(full_path)
                    else:
                        os.remove(full_path)
                    self.send_json({"success": True})
                else:
                    res_rm = subprocess.run(["git", "rm", "-r", filename], cwd=repo_path, capture_output=True, text=True)
                    if res_rm.returncode != 0:
                        self.send_json({"error": res_rm.stderr}, 400)
                        return
                    subprocess.run(["git", "commit", "-m", f"Delete {filename} via Web interface"], cwd=repo_path)
                    branch = get_active_branch(repo_path)
                    res_push = subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True, text=True)
                    if res_push.returncode == 0:
                        self.send_json({"success": True})
                    else:
                        self.send_json({"error": res_push.stderr}, 400)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

        elif path == "/api/push_source":
            repo_path = get_active_repo_path()
            msg = data.get("message", "").strip()
            if not msg:
                self.send_json({"error": "Commit message is required"}, 400)
                return
            try:
                subprocess.run(["git", "add", "."], cwd=repo_path, check=True)
                res_commit = subprocess.run(["git", "commit", "-m", msg], cwd=repo_path, capture_output=True, text=True)
                branch = get_active_branch(repo_path)
                res_push = subprocess.run(["git", "push", "origin", branch], cwd=repo_path, capture_output=True, text=True)
                
                logs = f"{res_commit.stdout}\n{res_commit.stderr}\n{res_push.stdout}\n{res_push.stderr}"
                if res_push.returncode == 0:
                    self.send_json({"success": True, "logs": logs})
                else:
                    if "nothing to commit" in res_commit.stdout or "clean" in res_commit.stdout:
                        self.send_json({"success": True, "logs": "No changes to commit.", "warning": "No changes to commit"})
                    else:
                        self.send_json({"error": logs}, 400)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)

    def serve_static(self, filepath, mime_type):
        dir_path = os.path.dirname(os.path.abspath(__file__))
        full_path = os.path.join(dir_path, filepath)
        if not os.path.exists(full_path):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")
            return
        
        self.send_response(200)
        self.send_header("Content-Type", f"{mime_type}; charset=utf-8")
        self.end_headers()
        with open(full_path, "rb") as f:
            self.wfile.write(f.read())

def run_server(port=8000):
    while port < 9000:
        try:
            server = HTTPServer(("127.0.0.1", port), ReleaseManagerAPIHandler)
            url = f"http://127.0.0.1:{port}"
            print(f"Web Release Manager Server running at {url}")
            
            # Start background thread to open the browser automatically
            import threading
            import webbrowser
            import time
            def open_browser():
                time.sleep(0.8)
                webbrowser.open(url)
            threading.Thread(target=open_browser, daemon=True).start()
            
            server.serve_forever()
            break
        except OSError as e:
            print(f"Port {port} in use, trying next... Error: {e}")
            port += 1

if __name__ == "__main__":
    run_server()
