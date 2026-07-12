import sqlite3
import json
import os
import re

db_path = r'C:\Users\AHMED\.gemini\antigravity\conversations\89629709-b27b-4a75-af6e-9d34936b309e.db'
conn = sqlite3.connect(db_path)
cur = conn.cursor()
cur.execute("SELECT step_payload FROM steps")
rows = cur.fetchall()

latest_content = {}
for row in rows:
    payload_str = row[0]
    if not payload_str: continue
    
    try:
        data = json.loads(payload_str)
        
        if 'tool_calls' in data:
            for call in data['tool_calls']:
                if call.get('name') in ['write_to_file', 'replace_file_content']:
                    args = call.get('args', {})
                    target = args.get('TargetFile', '')
                    if 'ui/screens' in target.replace('\\\\', '/'):
                        basename = os.path.basename(target)
                        content = args.get('CodeContent', args.get('ReplacementContent', ''))
                        if content:
                            latest_content[basename] = content
                            
        # Check user inputs or other types of messages for raw code blocks
        if data.get('type') in ['USER_INPUT', 'PLANNER_RESPONSE', 'MODEL_RESPONSE']:
            content = data.get('content', '')
            if 'ui/screens/' in content and '`kotlin' in content:
                # We can try to extract code blocks if they are marked with a path
                pass
                
    except Exception as e:
        pass

for name, content in latest_content.items():
    print(f"Recovered {name} from tool calls, {len(content)} bytes")
