import os
import requests
import subprocess
import sys

def get_token():
    result = subprocess.run(['gh', 'auth', 'token'], capture_output=True, text=True)
    if result.returncode != 0:
        print("Failed to get token")
        sys.exit(1)
    return result.stdout.strip()

def upload_apk():
    token = get_token()
    repo = "my-nvda/accVideoEditorReleases"
    tag = "v2.2.8"
    apk_path = r"D:\.gemini\antigravity\scratch\AccessibleVideoEditor\app\build\outputs\apk\debug\app-debug.apk"
    
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    print(f"Fetching release {tag}...")
    resp = requests.get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers)
    if resp.status_code != 200:
        print(f"Error fetching release: {resp.text}")
        sys.exit(1)
        
    release_id = resp.json()['id']
    print(f"Release ID: {release_id}")
    
    # Check if asset already exists and delete it
    for asset in resp.json().get('assets', []):
        if asset['name'] == 'app-debug.apk':
            print("Asset exists, deleting it...")
            requests.delete(asset['url'], headers=headers)
            
    # Upload
    upload_url = resp.json()['upload_url'].split('{')[0]
    upload_url += "?name=app-debug.apk"
    
    print(f"Uploading APK to {upload_url}...")
    headers['Content-Type'] = 'application/vnd.android.package-archive'
    
    try:
        with open(apk_path, 'rb') as f:
            resp = requests.post(upload_url, headers=headers, data=f)
        
        if resp.status_code == 201:
            print("Upload successful!")
        else:
            print(f"Upload failed: {resp.status_code} {resp.text}")
    except Exception as e:
        print(f"Upload exception: {e}")

if __name__ == "__main__":
    upload_apk()
