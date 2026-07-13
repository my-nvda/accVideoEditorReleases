$token = gh auth token
$repo = "my-nvda/accVideoEditorReleases"
$tag = "v2.2.8"
$apkPath = "D:\.gemini\antigravity\scratch\AccessibleVideoEditor\app\build\outputs\apk\debug\app-debug.apk"

$headers = @{
    "Authorization" = "token $token"
    "Accept" = "application/vnd.github.v3+json"
}

Write-Host "Fetching release $tag..."
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" -Headers $headers

if (-not $release.id) {
    Write-Host "Failed to find release"
    exit 1
}

Write-Host "Release ID: $($release.id)"

foreach ($asset in $release.assets) {
    if ($asset.name -eq "app-debug.apk") {
        Write-Host "Deleting existing asset..."
        Invoke-RestMethod -Uri $asset.url -Method Delete -Headers $headers
    }
}

$uploadUrl = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=app-debug.apk"
$uploadUrl = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=app-debug.apk"

Write-Host "Uploading APK to $uploadUrl..."
$uploadHeaders = @{
    "Authorization" = "token $token"
    "Accept" = "application/vnd.github.v3+json"
    "Content-Type" = "application/vnd.android.package-archive"
}

Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -InFile $apkPath -TimeoutSec 300

Write-Host "Upload complete!"
