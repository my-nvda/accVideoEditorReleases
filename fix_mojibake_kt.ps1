$cp1256 = [System.Text.Encoding]::GetEncoding(1256)
$utf8 = [System.Text.Encoding]::UTF8

$files = Get-ChildItem -Path "D:\.gemini\antigravity\scratch\AccessibleVideoEditor\app\src\main\java\com\example\accessiblevideoeditor\ui" -Filter "*.kt" -Recurse

$fixedCount = 0

foreach ($file in $files) {
    $bytesOnDisk = [IO.File]::ReadAllBytes($file.FullName)
    $content = $utf8.GetString($bytesOnDisk)
    
    # Check if there are signs of double-encoding (ط is very common in double encoded Arabic)
    if ($content -match "ط") {
        try {
            $bytes = $cp1256.GetBytes($content)
            $decoded = $utf8.GetString($bytes)
            
            # If decoding produced replacement characters, it means the file wasn't purely double-encoded UTF-8
            # (e.g. it contained actual valid Arabic that got mangled into invalid UTF-8 bytes during this test)
            if ($decoded.Contains("")) {
                continue
            }
            
            # If decoding changed the text and looks like it produced Arabic
            if ($decoded -cne $content -and $decoded -match "[\u0600-\u06FF]") {
                [IO.File]::WriteAllText($file.FullName, $decoded, $utf8)
                Write-Host "Fixed $($file.Name)"
                $fixedCount++
            }
        } catch {
            # Ignore encoding errors
        }
    }
}

Write-Host "Fixed $fixedCount files."
