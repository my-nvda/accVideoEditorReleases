$cp1256 = [System.Text.Encoding]::GetEncoding(1256)
$utf8 = [System.Text.Encoding]::UTF8

$files = Get-ChildItem -Path "D:\.gemini\antigravity\scratch\AccessibleVideoEditor\app\src\main\java\com\example\accessiblevideoeditor\ui" -Filter "*.kt" -Recurse

$fixedCount = 0

foreach ($file in $files) {
    $bytesOnDisk = [IO.File]::ReadAllBytes($file.FullName)
    $content = $utf8.GetString($bytesOnDisk)
    
    if ($content -match "ط") {
        try {
            $bytes = $cp1256.GetBytes($content)
            $decoded = $utf8.GetString($bytes)
            
            # Check for Replacement Character U+FFFD ()
            if ($decoded.Contains([char]65533)) {
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
