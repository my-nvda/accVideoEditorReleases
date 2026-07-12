import sqlite3
import os

db_path = r'C:\Users\AHMED\.gemini\antigravity\conversations\89629709-b27b-4a75-af6e-9d34936b309e.db'

if os.path.exists(db_path):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = cur.fetchall()
    
    for table in tables:
        tname = table[0]
        cur.execute(f"PRAGMA table_info({tname})")
        cols = [r[1] for r in cur.fetchall()]
        for col in cols:
            try:
                cur.execute(f"SELECT COUNT(*) FROM {tname} WHERE {col} LIKE '%fun HomeScreen%'")
                cnt = cur.fetchone()[0]
                if cnt > 0:
                    print(f'Found {cnt} hits in table {tname} column {col}')
            except:
                pass
